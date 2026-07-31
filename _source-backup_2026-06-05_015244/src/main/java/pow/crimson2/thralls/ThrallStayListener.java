package pow.crimson2.thralls;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import pow.crimson2.VampireSMPPlugin;

/**
 * Enforces the "stay" command by teleporting players back to their locked location
 * whenever they move while the stay timer is active.
 */
public class ThrallStayListener implements Listener {

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;

    public ThrallStayListener(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.thrallManager = plugin.getThrallManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!thrallManager.getThrallConfig().isThrallStayEnabled()) return;

        Player player = event.getPlayer();
        ThrallBondData d = thrallManager.getBond(player.getUniqueId());
        if (d == null || d.getStayTime() <= 0 || d.getStayLoc() == null) return;

        // Only cancel movement that changes block position (allow head rotation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
         && event.getFrom().getBlockY() == event.getTo().getBlockY()
         && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        event.setCancelled(true);
        player.teleport(d.getStayLoc());
    }
}
