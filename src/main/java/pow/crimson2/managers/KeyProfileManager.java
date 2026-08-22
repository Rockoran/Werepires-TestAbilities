package pow.crimson2.managers;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pow.crimson2.VampireSMPPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sends authoritative species changes to the bundled CommandKeys client. */
public final class KeyProfileManager implements Listener {
    public static final String CHANNEL = "vsmp:keyprofile";
    private final VampireSMPPlugin plugin;
    private final Map<UUID, Byte> lastSent = new HashMap<>();

    public KeyProfileManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 40L, 20L);
    }

    private void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) sendIfChanged(player);
    }

    public void sendIfChanged(Player player) {
        byte species = speciesOf(player);
        Byte previous = lastSent.put(player.getUniqueId(), species);
        if (previous != null && previous == species) return;
        try { player.sendPluginMessage(plugin, CHANNEL, new byte[]{species}); }
        catch (RuntimeException ex) {
            plugin.getLogger().fine("Could not send key profile to " + player.getName() + ": " + ex.getMessage());
        }
    }

    private byte speciesOf(Player player) {
        if (plugin.getGhostModeManager() != null && plugin.getGhostModeManager().isGhost(player)) return 2;
        if (plugin.getThrallManager() != null && plugin.getThrallManager().isThrall(player)) return 3;
        if (plugin.getVampireManager() != null && plugin.getVampireManager().isVampire(player)) return 1;
        return 0;
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        lastSent.remove(event.getPlayer().getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) sendIfChanged(event.getPlayer());
        }, 60L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        lastSent.remove(event.getPlayer().getUniqueId());
    }
}
