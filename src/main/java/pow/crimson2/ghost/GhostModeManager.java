package pow.crimson2.ghost;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import pow.crimson2.VampireSMPPlugin;

/**
 * "Ghost" / haunt mode. When a player suffers final (perma) death they may choose to become a
 * normal spectator, or to <i>haunt the lands</i> — a restricted creative state:
 *
 * <ul>
 *   <li>flies, invisible to everyone (no invisibility effect — hidden via {@code hidePlayer})</li>
 *   <li>cannot break or place blocks, use items, pick up or drop items</li>
 *   <li>can open doors / levers / buttons / chests, but cannot move items in/out of containers</li>
 *   <li>can reveal itself to specific players ({@code /ghost show <player>})</li>
 * </ul>
 *
 * <p>Noclip-through-walls and the voice-chat haunt are layered on later (mod + SVC phases).
 * Ghost state is marked with the {@code ghost_haunting} scoreboard tag so it survives relogs;
 * the event guards key off that tag.</p>
 */
public class GhostModeManager implements Listener {

    public static final String GHOST_TAG = "ghost_haunting";
    /** S2C channel the VampireSMP Compatibility mod listens on to toggle client noclip. */
    public static final String GHOST_CHANNEL = "vsmp:ghost";

    private final VampireSMPPlugin plugin;

    /** Ghost UUID -> set of player UUIDs they have revealed themselves to. */
    private final Map<UUID, Set<UUID>> revealedTo = new ConcurrentHashMap<>();

    /**
     * Ghost UUID -> the players they are voice-haunting. Their speech is relayed to every one of
     * them and to nobody else, so a ghost can hold a conversation with a group rather than being
     * limited to whispering at one person at a time.
     */
    private final Map<UUID, Set<UUID>> hauntTargets = new ConcurrentHashMap<>();

    /** Ghost UUID -> saved [viewDistance, simulationDistance] to restore when un-ghosted. */
    private final Map<UUID, int[]> savedDistances = new ConcurrentHashMap<>();

    public GhostModeManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Voice haunt target (used by GhostVoicechatPlugin) ───────────────────────

    /** Everyone this ghost is voice-haunting. Empty set when they are haunting nobody. */
    public Set<UUID> getHauntTargets(UUID ghostId) {
        Set<UUID> targets = hauntTargets.get(ghostId);
        return targets == null ? java.util.Collections.emptySet() : new java.util.HashSet<>(targets);
    }

    public boolean isHaunting(UUID ghostId) {
        Set<UUID> targets = hauntTargets.get(ghostId);
        return targets != null && !targets.isEmpty();
    }

    /** @return true if the target was newly added, false if this ghost already haunted them. */
    public boolean addHauntTarget(UUID ghostId, UUID targetId) {
        if (ghostId == null || targetId == null || ghostId.equals(targetId)) return false;
        return hauntTargets
                .computeIfAbsent(ghostId, id -> java.util.concurrent.ConcurrentHashMap.newKeySet())
                .add(targetId);
    }

    /** @return true if the target was actually being haunted. */
    public boolean removeHauntTarget(UUID ghostId, UUID targetId) {
        Set<UUID> targets = hauntTargets.get(ghostId);
        if (targets == null) return false;
        boolean removed = targets.remove(targetId);
        if (targets.isEmpty()) hauntTargets.remove(ghostId);
        return removed;
    }

    /** Stop haunting everyone. @return how many targets were released. */
    public int clearHauntTarget(UUID ghostId) {
        Set<UUID> targets = hauntTargets.remove(ghostId);
        return targets == null ? 0 : targets.size();
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public boolean isGhost(Player p) {
        return p != null && p.getScoreboardTags().contains(GHOST_TAG);
    }

    // ── Perma-death entry point ────────────────────────────────────────────────

    /**
     * Called at final death. Places the player in spectator as the safe default, then offers
     * the choice to haunt instead.
     */
    public void promptPermaDeath(Player player) {
        if (!player.isOnline()) return;

        // Default to spectator unless they choose to haunt (also covers dialog dismissal).
        becomeSpectator(player, false);

        Dialog dialog = Dialog.create(b -> b.empty()
            .base(DialogBase.builder(Component.text("Your Journey Has Ended", NamedTextColor.DARK_RED))
                .body(java.util.List.of(DialogBody.plainMessage(Component.text(
                    "Death has claimed you for the last time. What becomes of your soul?"))))
                .build())
            .type(DialogType.multiAction(java.util.List.of(
                ActionButton.create(
                    Component.text("Become a Spectator", NamedTextColor.GRAY),
                    Component.text("Drift freely and watch over the living."),
                    220,
                    DialogAction.customClick((view, aud) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) becomeSpectator(player, true);
                    }), ClickCallback.Options.builder().build())),
                ActionButton.create(
                    Component.text("Haunt These Lands", NamedTextColor.DARK_PURPLE),
                    Component.text("Linger as a restless spirit among the living."),
                    220,
                    DialogAction.customClick((view, aud) -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) enterGhostMode(player);
                    }), ClickCallback.Options.builder().build()))
            )).build())
        );
        player.showDialog(dialog);
    }

    // ── Transitions ─────────────────────────────────────────────────────────────

    /** Plain spectator (the classic final-death outcome). */
    public void becomeSpectator(Player player, boolean announce) {
        clearGhostState(player);
        player.setGameMode(GameMode.SPECTATOR);
        if (announce) {
            player.sendMessage("§7Your soul drifts free. You are now a §fspectator§7.");
        }
    }

    /** Enter the restricted haunt/ghost mode. */
    public void enterGhostMode(Player player) {
        player.addScoreboardTag(GHOST_TAG);
        revealedTo.put(player.getUniqueId(), ConcurrentHashMap.newKeySet());

        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        applyGhostRenderDistance(player); // keep each ghost's chunk footprint small

        // Invisible to everyone (no potion effect — actually hidden); reveal system works on top.
        // They stay in the tab list, though, so others still see they're "online" (haunting).
        reapplyGhostVisibility(player);

        // Swap to the registered "Ghost" skin (no-op if they never registered one).
        if (plugin.getSkinShuffleManager() != null) {
            plugin.getSkinShuffleManager().applyGhostSkin(player);
        }

        player.sendTitle("§5§lA RESTLESS SPIRIT", "§7You linger to haunt the living", 10, 80, 30);
        player.sendMessage("");
        player.sendMessage("§5§lYOU HAUNT THESE LANDS");
        player.sendMessage("§7You are unseen. You cannot touch the world,");
        player.sendMessage("§7but doors and chests still answer your presence.");
        player.sendMessage("§8Use §7/ghost show <player>§8 to reveal yourself to someone.");
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, SoundCategory.MASTER, 1.0F, 0.4F);
        player.playSound(player.getLocation(), Sound.ENTITY_VEX_AMBIENT, SoundCategory.MASTER, 0.6F, 0.6F);

        // Notify the compat mod so it re-enables block interactions for this spectator ghost.
        sendGhostState(player, true);
        plugin.logInfo("[Ghost] " + player.getName() + " is now haunting.");
    }

    /** Remove ghost state and reveal the player to everyone again (used on revive / reset). */
    public void clearGhostState(Player player) {
        UUID id = player.getUniqueId();
        player.removeScoreboardTag(GHOST_TAG);
        revealedTo.remove(id);
        hauntTargets.remove(id);
        restoreGhostRenderDistance(player);
        // Make sure everyone can see them again.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) viewer.showPlayer(plugin, player);
        }
        // Restore the skin that matches their (now non-ghost) state.
        if (plugin.getSkinShuffleManager() != null) {
            plugin.getSkinShuffleManager().applyExistingVampireSkin(player);
        }
        // Tell the compat mod to disable block-interaction override.
        sendGhostState(player, false);
    }

    /**
     * Allow a ghost's "invalid" movement instead of rubber-banding it. Paper fires
     * {@link io.papermc.paper.event.player.PlayerFailMoveEvent} when it's about to correct a
     * move that clipped into blocks / moved too quickly; calling {@code setAllowed(true)} makes
     * the server accept the position — this is what lets client-side noclip actually stick,
     * with no NMS or mixin needed. (Same technique as the SimpleClip plugin.)
     *
     * <p>We accept <em>every</em> failed ghost move, including moves into not-yet-loaded chunks.
     * Refusing those would make Paper send a position-correction (teleport) packet, the client
     * noclips forward again, and we get a ~20/s rubber-band packet storm <em>per ghost</em> at the
     * edge of their view. Paper 1.21 loads/generates chunks asynchronously, so just allowing the
     * move (and relying on the small ghost view distance to bound it) is cheaper and smoother.</p>
     */
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onGhostFailMove(io.papermc.paper.event.player.PlayerFailMoveEvent event) {
        if (!isGhost(event.getPlayer())) return;
        event.setAllowed(true);
    }

    /**
     * Shrink a ghost's view + simulation distance so its free-roaming noclip doesn't load and
     * tick a huge number of chunks (the main cause of per-ghost server lag). Saved values are
     * restored when the ghost is freed/revived.
     */
    private void applyGhostRenderDistance(Player p) {
        int view = Math.max(2, plugin.getConfig().getInt("ghost.view-distance", 4));
        int sim  = Math.max(2, plugin.getConfig().getInt("ghost.simulation-distance", 2));
        try {
            savedDistances.putIfAbsent(p.getUniqueId(),
                    new int[]{ p.getViewDistance(), p.getSimulationDistance() });
            p.setViewDistance(view);
            p.setSimulationDistance(sim);
        } catch (Throwable t) {
            plugin.getLogger().fine("[Ghost] could not shrink render distance for "
                    + p.getName() + ": " + t.getMessage());
        }
    }

    /** Restore the player's original view + simulation distance. */
    private void restoreGhostRenderDistance(Player p) {
        int[] d = savedDistances.remove(p.getUniqueId());
        if (d == null) return;
        try {
            p.setViewDistance(d[0]);
            p.setSimulationDistance(d[1]);
        } catch (Throwable t) {
            plugin.getLogger().fine("[Ghost] could not restore render distance for "
                    + p.getName() + ": " + t.getMessage());
        }
    }

    /** Send the ghost toggle to the compat mod over the {@code vsmp:ghost} channel. */
    /**
     * Toggle client-side noclip WITHOUT entering full ghost mode — used by the Fading tome's
     * bypass perk. Real ghosts are skipped: their ghost state owns this channel, and stomping it
     * here would drop them out of noclip mid-haunt.
     */
    public void setExternalNoclip(Player player, boolean active) {
        if (player == null || isGhost(player)) return;
        sendGhostState(player, active);
    }

    private void sendGhostState(Player player, boolean active) {
        if (player == null || !player.isOnline()) return;
        try {
            player.sendPluginMessage(plugin, GHOST_CHANNEL, new byte[]{ (byte) (active ? 1 : 0) });
        } catch (Exception e) {
            plugin.getLogger().fine("[Ghost] vsmp:ghost send failed for "
                    + player.getName() + ": " + e.getMessage());
        }
    }

    // ── Per-player visibility ────────────────────────────────────────────────────

    /**
     * Hide a ghost's entity from a viewer, but keep them visible in the viewer's tab list.
     * {@code hidePlayer} normally also removes the player from the tab list; Paper's
     * {@code listPlayer} re-adds them there while the entity stays hidden in the world.
     * Guards against double-hide (idempotent).
     */
    private void hideButKeepListed(Player viewer, Player ghost) {
        if (!viewer.canSee(ghost)) return; // already hidden — don't flicker
        viewer.hidePlayer(plugin, ghost);
        try {
            viewer.listPlayer(ghost);
        } catch (Throwable ignored) {
            // Older Paper without listPlayer — ghost just won't appear in tab; not fatal.
        }
    }

    /**
     * Show a ghost's entity to a viewer. Guards against double-show (idempotent).
     * Re-lists after show in case a prior hide+listPlayer left the tab state ambiguous.
     */
    private void showToViewer(Player viewer, Player ghost) {
        if (viewer.canSee(ghost)) return; // already visible — skip
        viewer.showPlayer(plugin, ghost);
        try {
            viewer.listPlayer(ghost);
        } catch (Throwable ignored) {}
    }

    /**
     * Re-apply the full hide/show policy for every online player against this ghost.
     * Players in {@code revealedTo} are shown; everyone else is hidden (but kept listed).
     * Call this on ghost entry, on ghost relog, and after bulk reveal changes.
     */
    public void reapplyGhostVisibility(Player ghost) {
        Set<UUID> revealed = revealedTo.getOrDefault(ghost.getUniqueId(), Set.of());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(ghost)) continue;
            if (revealed.contains(viewer.getUniqueId())) {
                showToViewer(viewer, ghost);
            } else {
                hideButKeepListed(viewer, ghost);
            }
        }
    }

    public boolean revealTo(Player ghost, Player viewer) {
        if (!isGhost(ghost)) return false;
        revealedTo.computeIfAbsent(ghost.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(viewer.getUniqueId());
        showToViewer(viewer, ghost);
        return true;
    }

    public boolean concealFrom(Player ghost, Player viewer) {
        if (!isGhost(ghost)) return false;
        Set<UUID> set = revealedTo.get(ghost.getUniqueId());
        if (set != null) set.remove(viewer.getUniqueId());
        hideButKeepListed(viewer, ghost);
        return true;
    }

    public Set<UUID> getRevealedTo(Player ghost) {
        return revealedTo.getOrDefault(ghost.getUniqueId(), Set.of());
    }

    // ── Keep invisibility consistent across joins ────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();
        // Hide any existing ghosts from the newly-joined player (unless revealed to them).
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(joiner) || !isGhost(p)) continue;
            if (!getRevealedTo(p).contains(joiner.getUniqueId())) {
                hideButKeepListed(joiner, p);
            }
        }
        // A returning ghost: re-assert creative flight + visibility (reveals survive relog).
        if (isGhost(joiner)) {
            joiner.setGameMode(GameMode.CREATIVE);
            joiner.setAllowFlight(true);
            joiner.setFlying(true);
            applyGhostRenderDistance(joiner);
            revealedTo.computeIfAbsent(joiner.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            // Restore full visibility state: hide from non-revealed, show to previously-revealed.
            reapplyGhostVisibility(joiner);
            if (plugin.getSkinShuffleManager() != null) {
                plugin.getSkinShuffleManager().applyGhostSkin(joiner);
            }
            // Re-enable block-interaction override in the mod once the channel is ready.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (joiner.isOnline() && isGhost(joiner)) sendGhostState(joiner, true);
            }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID quitId = event.getPlayer().getUniqueId();
        // Do NOT clear revealedTo here — reveals persist across ghost relogs so that
        // players the ghost showed themselves to before logging off are still revealed on return.
        // revealedTo is only cleared in clearGhostState() when ghost mode fully ends.
        hauntTargets.remove(quitId);
        savedDistances.remove(quitId); // distances reset on disconnect; drop the stale entry
        // Also stop anyone haunting the player who just left, and prune ghosts left with nobody.
        hauntTargets.values().forEach(targets -> targets.remove(quitId));
        hauntTargets.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    // ── Restrictions (only while ghost) ──────────────────────────────────────────

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isGhost(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isGhost(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && isGhost(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isGhost(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGhostAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && isGhost(p)) e.setCancelled(true);
    }

    /** Allow interacting with blocks (doors/levers/buttons/chests) but never use the held item. */
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent e) {
        if (!isGhost(e.getPlayer())) return;
        // Deny item use (eating, throwing, placing, bows, etc.) but keep block interaction.
        e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        switch (e.getAction()) {
            case LEFT_CLICK_BLOCK:
            case LEFT_CLICK_AIR:
                e.setCancelled(true); // no attacking/breaking
                break;
            default:
                // RIGHT_CLICK_BLOCK keeps useInteractedBlock at default (ALLOW) → doors/chests open.
                break;
        }
    }

    /** Block taking/placing items in any inventory (containers, own inv, creative grab). */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && isGhost(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && isGhost(p)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreativeInventory(InventoryCreativeEvent e) {
        if (e.getWhoClicked() instanceof Player p && isGhost(p)) e.setCancelled(true);
    }
}
