package pow.crimson2.setup;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import pow.crimson2.VampireSMPPlugin;
import pow.crimson2.managers.SkinShuffleManager;

import java.util.List;
import java.util.UUID;

/**
 * Shows the player's registered stage skins as player-head icons in a chest GUI.
 *
 * Layout (9-slot row, rows as needed):
 *
 *   [ Human  ] [ Stage 1 ] [ Stage 2 ] [ Stage 3 ] [  ----  ] ... [  Done  ]
 *     slot 0     slot 1     slot 2     slot 3                      slot 8
 *
 * Clicking a head slot opens the single-stage capture dialog so the player can
 * update that skin.  Clicking Done or closing the inventory calls the continuation.
 *
 * This class is a temporary Listener that deregisters itself when the inventory
 * is closed, so it does not accumulate.
 */
public class SkinPreviewGui implements Listener {

    private static final String[] STAGES = {"human", "stage1", "stage2", "stage3"};
    private static final int[] HEAD_SLOTS  = {0, 1, 2, 3};
    private static final int   DONE_SLOT   = 8;

    private final VampireSMPPlugin  plugin;
    private final Player            player;
    private final SkinShuffleManager mgr;
    private final Runnable          continuation; // called when GUI closes
    private final Inventory         inv;
    private boolean                 done = false;

    private SkinPreviewGui(VampireSMPPlugin plugin, Player player,
                           SkinShuffleManager mgr, Runnable continuation) {
        this.plugin       = plugin;
        this.player       = player;
        this.mgr          = mgr;
        this.continuation = continuation;
        this.inv          = buildInventory();
    }

    /**
     * Open the preview GUI for {@code player}.
     * {@code continuation} is called when the player closes the GUI or clicks Done.
     */
    public static void open(VampireSMPPlugin plugin, Player player,
                            SkinShuffleManager mgr, Runnable continuation) {
        SkinPreviewGui gui = new SkinPreviewGui(plugin, player, mgr, continuation);
        Bukkit.getPluginManager().registerEvents(gui, plugin);
        player.openInventory(gui.inv);
    }

    // =========================================================================
    // Build
    // =========================================================================

    private Inventory buildInventory() {
        Inventory chest = Bukkit.createInventory(null, 9, "§6Stage Skin Preview");

        // Head slots
        for (int i = 0; i < STAGES.length; i++) {
            chest.setItem(HEAD_SLOTS[i], buildHead(STAGES[i]));
        }

        // Filler panes for empty slots 4-7
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName("§8");
        filler.setItemMeta(fillerMeta);
        for (int slot = 4; slot < 8; slot++) {
            chest.setItem(slot, filler);
        }

        // Done button
        ItemStack done = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta doneMeta = done.getItemMeta();
        doneMeta.setDisplayName("§a§lDone");
        doneMeta.setLore(List.of("§7Close and continue setup."));
        done.setItemMeta(doneMeta);
        chest.setItem(DONE_SLOT, done);

        return chest;
    }

    /**
     * Build a player-head item for the given stage with the registered texture.
     * If no skin is registered, shows a stone head with a "Not Set" label.
     */
    private ItemStack buildHead(String stage) {
        String label     = SkinShuffleManager.displayName(stage);
        boolean hasEntry = mgr.hasStageRegistered(player, stage);

        if (hasEntry) {
            ProfileProperty textures = mgr.getRegisteredProperty(player, stage);
            if (textures != null) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();

                // Apply the stored texture to the skull
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(textures);
                meta.setPlayerProfile(profile);

                meta.setDisplayName("§e" + label);
                meta.setLore(List.of(
                    "§a✔ Skin registered",
                    "§7Click to update this stage's skin."
                ));
                head.setItemMeta(meta);
                return head;
            }
        }

        // No skin registered — show default head with "Not set" label
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        meta.setDisplayName("§7" + label);
        meta.setLore(List.of(
            "§8✗ No skin registered",
            "§7Click to register a skin for this stage."
        ));
        head.setItemMeta(meta);
        return head;
    }

    // =========================================================================
    // Events
    // =========================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;
        if (!event.getInventory().equals(inv)) return;

        event.setCancelled(true); // never let items be taken

        int slot = event.getRawSlot();

        // Done button or filler — close
        if (slot == DONE_SLOT) {
            done = true;
            player.closeInventory();
            return;
        }

        // Head slots — start single-stage capture
        for (int i = 0; i < HEAD_SLOTS.length; i++) {
            if (slot == HEAD_SLOTS[i]) {
                String stage = STAGES[i];
                done = true;
                player.closeInventory();
                // Open the capture dialog for this specific stage
                SkinSetupDialog.openSingleCaptureStep(plugin, player, mgr, stage, () ->
                    open(plugin, player, mgr, continuation)); // re-open GUI after capture
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getPlayer().equals(player)) return;
        if (!event.getInventory().equals(inv)) return;

        HandlerList.unregisterAll(this); // clean up this one-shot listener

        if (!done) {
            // Player closed without clicking Done — treat as done
            Bukkit.getScheduler().runTask(plugin, continuation);
        }
    }
}
