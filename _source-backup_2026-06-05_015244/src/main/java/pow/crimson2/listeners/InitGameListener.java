package pow.crimson2.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import pow.crimson2.VampireSMPPlugin;

public class InitGameListener implements Listener {

    private final VampireSMPPlugin plugin;

    public InitGameListener(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!plugin.getInitGameManager().isPlayerSelectionGUI(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player admin)) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        String displayName = meta.getDisplayName();

        if (clicked.getType() == Material.LIME_CONCRETE) {
            plugin.getInitGameManager().handleGUIConfirmation(admin);
            return;
        }
        if (clicked.getType() == Material.ARROW) {
            if (displayName.contains("Previous Page")) {
                plugin.getInitGameManager().handlePageChange(admin, -1);
            } else if (displayName.contains("Next Page")) {
                plugin.getInitGameManager().handlePageChange(admin, 1);
            }
            return;
        }
        if (displayName.contains(" - Vampire") || displayName.contains(" - Human")) {
            String playerName = extractPlayerName(displayName);
            if (playerName != null) plugin.getInitGameManager().handlePlayerToggle(admin, playerName);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!plugin.getInitGameManager().isPlayerSelectionGUI(event.getView().getTitle())) return;
        if (!(event.getPlayer() instanceof Player admin)) return;
        if (!plugin.getInitGameManager().isGUIRefreshInProgress(admin.getUniqueId())) {
            plugin.getInitGameManager().handleGUIClose(admin);
        }
    }

    private String extractPlayerName(String displayName) {
        String stripped = displayName.replaceAll("§[0-9a-fk-or]", "");
        String[] parts  = stripped.split(" - ");
        return parts.length >= 1 ? parts[0].trim() : null;
    }
}
