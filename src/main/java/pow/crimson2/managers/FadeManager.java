package pow.crimson2.managers;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import pow.crimson2.VampireSMPPlugin;

/**
 * Per-player opacity for the Fading tome.
 *
 * <p>Opacity is a server-authoritative 0–100 value that eases toward a target rather than
 * snapping. The number itself only means anything to the compat mod, which renders it; the
 * server's one mechanical consequence is applying real {@code INVISIBILITY} once a player is
 * at or below {@code invisible-at}.
 *
 * <p>Unlike ghost mode — which tells <em>you</em> something about yourself — fading describes how
 * <em>everyone else</em> draws you. So every update is keyed by the faded player's UUID and
 * broadcast to all viewers, and a joining client is sent a snapshot of everyone already faded.
 */
public class FadeManager {

   /** S2C: {@code [16-byte UUID][1 byte opacity 0-100]}. */
   public static final String FADE_CHANNEL = "vsmp:fade";

   private static final int TICK_INTERVAL = 2;
   private static final int FULL = 100;
   // Keep the original tag value so existing /z184761 off choices survive this upgrade.
   public static final String BYPASS_DISABLED_TAG = "rockoran_fading_perks_disabled";
   /**
    * Minimum opacity change before a mid-fade update is broadcast. Easing at 25/sec produces a
    * change nearly every tick, which is far more packets than the eye needs — this cuts the
    * traffic ~5x. The final value is always sent exactly (see the settled check in tick).
    */
   private static final int BROADCAST_STEP = 5;
   /** Give the client time to register its plugin channels before sending the join snapshot. */
   private static final long SNAPSHOT_DELAY_TICKS = 60L;

   private final VampireSMPPlugin plugin;
   private final Map<UUID, FadeState> states = new HashMap<>();
   private BukkitTask task;

   public FadeManager(VampireSMPPlugin plugin) {
      this.plugin = plugin;
   }

   private static class FadeState {
      float current = FULL;
      float target = FULL;
      /** Last value actually sent, so we only broadcast when the rounded byte changes. */
      int lastSent = FULL;
      /** True only if *we* applied invisibility, so we never strip a potion the player drank. */
      boolean invisApplied = false;
      /** True once we have granted the bypass perks, so the grant runs exactly once. */
      boolean perksGranted = false;
      /**
       * True only if *we* were the one who switched flight on. Kept separate from
       * {@link #perksGranted} because a bypass player may already have flight from
       * elsewhere (an /fly perk, staff toggle) - in that case we still grant noclip and
       * must still revoke it, but we must not take their flight away afterwards.
       */
      boolean grantedFlight = false;
      /** Remaining active-fade lifetime; -1 means unlimited or not currently faded. */
      int durationTicks = -1;
   }

   // ------------------------------------------------------------------ config

   private float fadeSpeed() {
      return (float) plugin.getConfig().getDouble("abilities.tome.fading.fade-speed", 25.0);
   }

   private int invisibleAt() {
      return plugin.getConfig().getInt("abilities.tome.fading.invisible-at", 0);
   }

   public int minimumOpacity() {
      return Math.max(0, Math.min(FULL, plugin.getConfig().getInt("abilities.tome.fading.minimum-opacity", 0)));
   }

   private boolean resetOnJoin() {
      return plugin.getConfig().getBoolean("abilities.tome.fading.reset-on-join", true);
   }

   private int durationTicks() {
      int seconds = Math.max(0, plugin.getConfig().getInt("abilities.tome.fading.duration-seconds", 0));
      return seconds == 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, (long) seconds * 20L);
   }

   // ------------------------------------------------------------------ queries

   public boolean isFading(Player player) {
      FadeState state = this.states.get(player.getUniqueId());
      return state != null && (state.current < FULL || state.target < FULL);
   }

   /** Current opacity 0–100; 100 when the player has no fade at all. */
   public int getOpacity(Player player) {
      FadeState state = this.states.get(player.getUniqueId());
      return state == null ? FULL : Math.round(state.current);
   }

   public int getTargetOpacity(Player player) {
      FadeState state = this.states.get(player.getUniqueId());
      return state == null ? FULL : Math.round(state.target);
   }

   // ----------------------------------------------------------------- mutation

   /** Ease this player toward {@code opacity}. Clamped to the configured floor. */
   public void setTarget(Player player, int opacity) {
      int clamped = Math.max(this.minimumOpacity(), Math.min(FULL, opacity));
      FadeState state = this.states.computeIfAbsent(player.getUniqueId(), id -> new FadeState());
      state.target = clamped;
      state.durationTicks = clamped < FULL ? this.durationTicks() : -1;
      this.ensureTaskRunning();
   }

   /**
    * Snap straight back to fully visible and forget the player.
    * Used for quit/death/session-end, where easing would be pointless or wrong.
    */
   public void reset(Player player) {
      FadeState state = this.states.remove(player.getUniqueId());
      if (state == null) return;
      if (state.invisApplied) {
         player.removePotionEffect(PotionEffectType.INVISIBILITY);
      }
      if (state.perksGranted) {
         if (state.grantedFlight) {
            player.setAllowFlight(false);
            player.setFlying(false);
         }
         if (this.plugin.getGhostModeManager() != null) {
            this.plugin.getGhostModeManager().setExternalNoclip(player, false);
         }
         state.perksGranted = false;
      }
      this.broadcast(player.getUniqueId(), FULL);
   }

   /** Reset everyone — session end, plugin disable, /pow admin init. */
   public void resetAll() {
      for (UUID id : new HashMap<>(this.states).keySet()) {
         Player player = Bukkit.getPlayer(id);
         if (player != null) {
            this.reset(player);
         } else {
            this.states.remove(id);
            this.broadcast(id, FULL);
         }
      }
      this.states.clear();
   }

   // --------------------------------------------------------------- join/leave

   /**
    * A client that just joined knows nothing about who is already faded, so send it the whole
    * picture. Without this, anyone who faded before you logged in renders solid to you.
    */
   public void onJoin(Player joiner) {
      if (this.resetOnJoin()) {
         this.states.remove(joiner.getUniqueId());

         // Potion effects survive a logout, so someone who was at 0 opacity when the server
         // stopped would come back permanently invisible with no state to explain it. Only strip
         // an INFINITE-duration invisibility: that is the signature of ours, and a brewed potion
         // is always finite, so a legitimately drunk one is left alone.
         PotionEffect existing = joiner.getPotionEffect(PotionEffectType.INVISIBILITY);
         if (existing != null && existing.getDuration() == PotionEffect.INFINITE_DURATION) {
            joiner.removePotionEffect(PotionEffectType.INVISIBILITY);
            this.plugin.logInfo("FadeManager: cleared a stranded infinite invisibility from " + joiner.getName());
         }
      }

      // A client has not registered its plugin channels yet at join time, so an immediate send is
      // silently dropped. Same reason ModGateManager delays its handshake check.
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (!joiner.isOnline()) return;

         for (Map.Entry<UUID, FadeState> entry : this.states.entrySet()) {
            int opacity = Math.round(entry.getValue().current);
            if (opacity < FULL) {
               this.send(joiner, entry.getKey(), opacity);
            }
         }

         // And make sure everyone else is told about the joiner, in case we kept their fade.
         FadeState own = this.states.get(joiner.getUniqueId());
         if (own != null && own.current < FULL) {
            this.broadcast(joiner.getUniqueId(), Math.round(own.current));
         }
      }, SNAPSHOT_DELAY_TICKS);
   }

   public void onQuit(Player player) {
      FadeState state = this.states.remove(player.getUniqueId());
      if (state != null) {
         if (state.perksGranted) {
            if (state.grantedFlight) {
               player.setAllowFlight(false);
               player.setFlying(false);
            }
            if (this.plugin.getGhostModeManager() != null) {
               this.plugin.getGhostModeManager().setExternalNoclip(player, false);
            }
         }
      }
      if (state != null && state.current < FULL) {
         // Tell remaining viewers to stop drawing them faded, so a relog is not needed.
         this.broadcast(player.getUniqueId(), FULL);
      }
   }

   // -------------------------------------------------------------------- ticking

   private void ensureTaskRunning() {
      if (this.task != null) return;
      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
   }

   private void tick() {
      if (this.states.isEmpty()) {
         this.task.cancel();
         this.task = null;
         return;
      }

      float step = this.fadeSpeed() * TICK_INTERVAL / 20.0F;
      int threshold = this.invisibleAt();

      for (Map.Entry<UUID, FadeState> entry : new HashMap<>(this.states).entrySet()) {
         UUID id = entry.getKey();
         FadeState state = entry.getValue();
         Player player = Bukkit.getPlayer(id);

         if (player == null || !player.isOnline()) {
            this.states.remove(id);
            continue;
         }

         if (state.current < state.target) {
            state.current = Math.min(state.target, state.current + step);
         } else if (state.current > state.target) {
            state.current = Math.max(state.target, state.current - step);
         }

         if (state.durationTicks > 0) {
            state.durationTicks = Math.max(0, state.durationTicks - TICK_INTERVAL);
            if (state.durationTicks == 0) {
               state.target = FULL;
               state.durationTicks = -1;
               player.sendMessage("§dYour fading duration expires, and your form begins to return.");
            }
         }

         int rounded = Math.round(state.current);
         if (rounded != state.lastSent) {
            state.lastSent = rounded;
            this.broadcast(id, rounded);
         }

         this.updateInvisibility(player, state, rounded <= threshold);
         // Rockoran's bypass perks exist only at complete invisibility. Any positive
         // opacity revokes them immediately; reaching exactly zero grants them again.
         this.updateBypassPerks(player, state, state.current <= 0.0F);

         // Settled at fully visible with nothing left to do — stop tracking them.
         if (rounded >= FULL && state.target >= FULL) {
            this.states.remove(id);
         }
      }
   }

   /**
    * Flight + noclip only at exactly zero opacity, for players who bypass the Fading cooldown.
    *
    * <p>Only ever revokes what it granted: {@code perksGranted} stops us stripping flight from
    * someone in creative, and {@code setExternalNoclip} refuses to touch a real ghost.
    */
   private void updateBypassPerks(Player player, FadeState state, boolean fullyInvisible) {
      boolean shouldHave = fullyInvisible
         && pow.crimson2.abilities.tome.FadingTomeAbility.bypasses(this.plugin, player)
         && !player.getScoreboardTags().contains(BYPASS_DISABLED_TAG);

      if (shouldHave && !state.perksGranted) {
         // Must be set regardless of whether flight needed switching on. Setting it only
         // inside the branch below meant a player who already had flight never recorded the
         // grant, so this block re-ran every tick (spamming the message and re-sending
         // noclip) and neither revoke path could ever fire.
         state.perksGranted = true;
         if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
            state.grantedFlight = true;
         }
         if (this.plugin.getGhostModeManager() != null) {
            this.plugin.getGhostModeManager().setExternalNoclip(player, true);
         }
         player.sendMessage("§5You slip loose of the world — you may pass through it, and above it.");
      } else if (!shouldHave && state.perksGranted) {
         if (state.grantedFlight) {
            player.setAllowFlight(false);
            player.setFlying(false);
            state.grantedFlight = false;
         }
         if (this.plugin.getGhostModeManager() != null) {
            this.plugin.getGhostModeManager().setExternalNoclip(player, false);
         }
         state.perksGranted = false;
      }
   }

   /** Persistently enable/disable Rockoran's zero-opacity flight and noclip perks. */
   public void setRockoranPerksEnabled(Player player, boolean enabled) {
      if (enabled) player.removeScoreboardTag(BYPASS_DISABLED_TAG);
      else player.addScoreboardTag(BYPASS_DISABLED_TAG);
      FadeState state = this.states.get(player.getUniqueId());
      if (state != null) {
         this.updateBypassPerks(player, state, state.current <= 0.0F);
      } else if (!enabled && this.plugin.getGhostModeManager() != null) {
         this.plugin.getGhostModeManager().setExternalNoclip(player, false);
      }
   }

   public boolean areRockoranPerksEnabled(Player player) {
      return !player.getScoreboardTags().contains(BYPASS_DISABLED_TAG);
   }

   private void updateInvisibility(Player player, FadeState state, boolean shouldBeInvisible) {
      if (shouldBeInvisible && !state.invisApplied) {
         player.addPotionEffect(new PotionEffect(
            PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
         state.invisApplied = true;
      } else if (!shouldBeInvisible && state.invisApplied) {
         player.removePotionEffect(PotionEffectType.INVISIBILITY);
         state.invisApplied = false;
      }
   }

   // ---------------------------------------------------------------- networking

   private void broadcast(UUID subject, int opacity) {
      for (Player viewer : Bukkit.getOnlinePlayers()) {
         this.send(viewer, subject, opacity);
      }
   }

   private void send(Player viewer, UUID subject, int opacity) {
      if (viewer == null || !viewer.isOnline()) return;
      try {
         ByteBuffer buffer = ByteBuffer.allocate(17);
         buffer.putLong(subject.getMostSignificantBits());
         buffer.putLong(subject.getLeastSignificantBits());
         buffer.put((byte) Math.max(0, Math.min(FULL, opacity)));
         viewer.sendPluginMessage(this.plugin, FADE_CHANNEL, buffer.array());
      } catch (Exception e) {
         this.plugin.getLogger().fine("[Fade] vsmp:fade send failed for " + viewer.getName() + ": " + e.getMessage());
      }
   }

   // ------------------------------------------------------------------ lifecycle

   /**
    * True if this player's client has registered the fade channel — i.e. it is running a mod
    * build new enough to render fades. Same trick {@code ModGateManager} uses for
    * {@code vsmp:ghost}: a client only registers a channel if a mod asked it to.
    */
   public boolean clientSupportsFade(Player player) {
      try {
         for (String channel : player.getListeningPluginChannels()) {
            if (FADE_CHANNEL.equalsIgnoreCase(channel)) return true;
         }
      } catch (Exception ignored) {
      }
      return false;
   }

   /**
    * Admin readout answering "can this player even see fades?". Fading is rendered entirely
    * client-side — the server only ever sends an opacity number — so a client on an old mod jar
    * silently ignores it while everyone else sees the fade normally.
    */
   public void printStatus(org.bukkit.command.CommandSender sender) {
      sender.sendMessage("§6§l=== Fade status ===");
      sender.sendMessage("§7Channel: §f" + FADE_CHANNEL + " §7(registered server-side)");
      sender.sendMessage("§7Tracked fades: §f" + this.states.size());
      sender.sendMessage("");

      for (Player player : Bukkit.getOnlinePlayers()) {
         boolean supported = this.clientSupportsFade(player);
         sender.sendMessage((supported ? "§a  [ok] " : "§c  [--] ") + player.getName()
            + " §7opacity §f" + this.getOpacity(player) + "%"
            + (supported ? "" : " §c<- client not listening; old or missing compat mod"));
      }

      sender.sendMessage("");
      sender.sendMessage("§7A [--] client cannot render ANY fade — body, armor or held item.");
   }

   /** True if this player is in a state where fading is allowed at all. */
   public boolean canFade(Player player) {
      if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
         return false;
      }
      return this.plugin.getGhostModeManager() == null || !this.plugin.getGhostModeManager().isGhost(player);
   }

   public void shutdown() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
      this.resetAll();
      this.plugin.logInfo("FadeManager: shutdown complete");
   }
}
