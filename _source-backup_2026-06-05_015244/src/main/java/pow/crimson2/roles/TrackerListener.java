package pow.crimson2.roles;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import pow.crimson2.VampireSMPPlugin;

/**
 * Handles the Tracker role compass mechanic:
 *
 *   Right-click a player with compass  → saves them as the tracking target.
 *   Right-click air/block  with compass → updates compass bearing toward target
 *                                         (starts or continues the track-duration window).
 */
public class TrackerListener implements Listener {

    private final VampireSMPPlugin plugin;
    private final RoleManager      rm;

    public TrackerListener(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.rm     = plugin.getRoleManager();
    }

    // -------------------------------------------------------------------------
    // Right-click entity (tag a player as target)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player tracker = event.getPlayer();
        if (tracker.getInventory().getItemInMainHand().getType() != Material.COMPASS) return;
        if (!tracker.getScoreboardTags().contains("tracker")) return;
        if (!(event.getRightClicked() instanceof Player)) return;

        Player target = (Player) event.getRightClicked();
        event.setCancelled(true);

        if (target.equals(tracker)) {
            tracker.sendActionBar("§cCan't track yourself.");
            return;
        }

        rm.setTrackerTarget(tracker.getUniqueId(), target.getUniqueId());
        tracker.sendActionBar("§bTarget: §f" + target.getName());
    }

    // -------------------------------------------------------------------------
    // Right-click air/block (update compass bearing)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player tracker = event.getPlayer();
        if (tracker.getInventory().getItemInMainHand().getType() != Material.COMPASS) return;
        if (!tracker.getScoreboardTags().contains("tracker")) return;

        String actionName = event.getAction().name();
        if (!actionName.startsWith("RIGHT_CLICK")) return;

        event.setCancelled(true);

        UUID targetUUID = rm.getTrackerTarget(tracker.getUniqueId());
        if (targetUUID == null) {
            tracker.sendActionBar("§7No target — right-click a player first.");
            return;
        }

        if (rm.isTrackerOnCooldown(tracker.getUniqueId())) {
            int rem = rm.getTrackerCooldownRemaining(tracker.getUniqueId());
            int m = rem / 60, s = rem % 60;
            String sf = s < 10 ? "0" + s : String.valueOf(s);
            tracker.sendActionBar("§cCooldown: §f" + m + "m " + sf + "s");
            return;
        }

        Player target = org.bukkit.Bukkit.getPlayer(targetUUID);
        if (target == null || !target.isOnline()) {
            tracker.sendActionBar("§cTarget offline — right-click a new player.");
            return;
        }

        rm.activateTrackerCompass(tracker, target);
    }
}
