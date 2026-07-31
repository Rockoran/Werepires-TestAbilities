package pow.crimson2.ghost;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import pow.crimson2.VampireSMPPlugin;

/**
 * Requires players to run the VampireSMP Compatibility Mod. The mod sends a handshake key on
 * join over {@code vsmp:modkey}; any player who hasn't sent a valid key within a few seconds is
 * kicked with instructions to obtain the mod.
 *
 * <p>Gated by config {@code require-compatibility-mod} (default true). Players with the
 * {@code vampiresmp.modgate.bypass} permission (default op) are never checked.</p>
 */
public class ModGateManager implements PluginMessageListener {

    public static final String CHANNEL = "vsmp:modkey";
    private static final String EXPECTED_KEY = "VSMP-COMPAT-1";
    private static final long CHECK_DELAY_TICKS = 100L; // 5 seconds

    private final VampireSMPPlugin plugin;
    private final Set<UUID> verified = ConcurrentHashMap.newKeySet();

    public ModGateManager(VampireSMPPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("require-compatibility-mod", true);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        try {
            String key = readVarStr(message);
            if (EXPECTED_KEY.equals(key)) {
                verified.add(player.getUniqueId());
            }
        } catch (Exception ignored) {
        }
    }

    /** Schedule the post-join verification kick. */
    public void scheduleCheck(Player player) {
        if (!isEnabled()) return;
        if (player.hasPermission("vampiresmp.modgate.bypass")) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (player.hasPermission("vampiresmp.modgate.bypass")) return;
            if (hasMod(player)) return;
            player.kick(LegacyComponentSerializer.legacySection().deserialize(
                    "§c§lMissing Required Mod\n\n"
                  + "§7This server requires the §fVampireSMP Compatibility Mod§7.\n\n"
                  + "§7DM §fRockoran §7for the correct mod,\n"
                  + "§7or ask in §foneshot general§7."));
            plugin.logInfo("[ModGate] Kicked " + player.getName() + " — missing compatibility mod.");
        }, CHECK_DELAY_TICKS);
    }

    public void clear(Player player) {
        verified.remove(player.getUniqueId());
    }

    /**
     * True if the player is confirmed to be running the compatibility mod — either they sent
     * the handshake key, OR their client registered the {@code vsmp:ghost} plugin channel
     * (which only this mod does). The channel check is the reliable signal; the key is a bonus.
     */
    private boolean hasMod(Player player) {
        if (verified.contains(player.getUniqueId())) return true;
        try {
            for (String ch : player.getListeningPluginChannels()) {
                if (ch.equalsIgnoreCase("vsmp:ghost")) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** Read a Minecraft-style VarInt-prefixed UTF-8 string. */
    private static String readVarStr(byte[] data) {
        int[] pos = {0};
        int len = 0, shift = 0;
        byte b;
        do {
            b = data[pos[0]++];
            len |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return new String(data, pos[0], len, StandardCharsets.UTF_8);
    }
}
