package pow.crimson2.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import pow.crimson2.VampireSMPPlugin;

import java.util.HexFormat;

/** Sends the server's configured resource pack without requiring server.properties access. */
public final class ServerResourcePackManager implements Listener {
    private final VampireSMPPlugin plugin;
    private final String url;
    private final byte[] sha1;
    private final boolean enabled;
    private final boolean required;
    private final long delayTicks;

    public ServerResourcePackManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("server-resource-pack.enabled", true);
        this.url = plugin.getConfig().getString("server-resource-pack.url", "").strip();
        this.required = plugin.getConfig().getBoolean("server-resource-pack.required", false);
        this.delayTicks = Math.max(0L, plugin.getConfig().getLong("server-resource-pack.join-delay-ticks", 20L));
        this.sha1 = parseSha1(plugin.getConfig().getString("server-resource-pack.sha1", ""));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || url.isBlank() || sha1 == null) return;
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.setResourcePack(url, sha1, required);
        }, delayTicks);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        if (!enabled) return;
        switch (event.getStatus()) {
            case DECLINED -> event.getPlayer().sendMessage(Component.text("The WerePires resource pack was declined.", NamedTextColor.YELLOW));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> plugin.getLogger().warning(
                    "Resource pack " + event.getStatus() + " for " + event.getPlayer().getName());
            default -> { }
        }
    }

    private byte[] parseSha1(String value) {
        String hash = value == null ? "" : value.strip();
        if (!hash.matches("(?i)[0-9a-f]{40}")) {
            if (enabled) plugin.getLogger().warning("server-resource-pack.sha1 must be exactly 40 hexadecimal characters; automatic pack delivery is disabled.");
            return null;
        }
        return HexFormat.of().parseHex(hash);
    }
}
