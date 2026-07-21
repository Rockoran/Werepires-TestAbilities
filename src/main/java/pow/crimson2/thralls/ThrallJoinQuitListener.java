package pow.crimson2.thralls;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import pow.crimson2.VampireSMPPlugin;

/**
 * Handles thrall system logic on player join / quit.
 */
public class ThrallJoinQuitListener implements Listener {

    private final VampireSMPPlugin plugin;
    private final ThrallManager thrallManager;

    public ThrallJoinQuitListener(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.thrallManager = plugin.getThrallManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cache username
        ThrallBondData d = thrallManager.getBond(uuid);
        if (d != null) {
            d.setUsername(player.getName());

            // Hide boss bar initially for non-thralls; it will be shown by tick if needed
            if (!thrallManager.isThrall(player)) {
                thrallManager.hideBossBar(player);
            }

            // Reopen tag offer GUI if it was pending
            if (thrallManager.getThrallConfig().isThrallTagOfferEnabled()
                    && d.getEffectiveStage() >= 3
                    && d.isTagOfferPending()) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) thrallManager.openTagOfferGui(player);
                }, 25L); // ~1.25 seconds
            }
        }

        // Update profile username cache
        ThrallProfile profile = thrallManager.getProfile(uuid);
        // Also register this UUID for preferences list
        // (stored implicitly in profiles map)
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cache username
        ThrallBondData d = thrallManager.getBond(uuid);
        if (d != null) d.setUsername(player.getName());

        // Hide and clean up boss bar
        thrallManager.hideBossBar(player);

        // Clear blood taste state so timers don't run while offline
        if (d != null) {
            d.setBloodTasteTarget(null);
            d.setBloodTastePrepTimer(0);
            d.setBloodTasteCd(0);
        }

        // Save this player's bond immediately; profiles saved as before.
        thrallManager.getDataManager().saveOneBondSync(uuid);
        thrallManager.getDataManager().saveAllProfiles();
    }
}
