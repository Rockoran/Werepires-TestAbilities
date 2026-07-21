package pow.crimson2.ghost;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import pow.crimson2.VampireSMPPlugin;

/**
 * Isolates every direct reference to the Simple Voice Chat API in one class.
 *
 * <p>The voicechat API is a {@code provided} dependency — it is NOT bundled in the plugin jar.
 * On a server without Simple Voice Chat installed, those classes don't exist. If the main plugin
 * class referenced them directly, the JVM's bytecode verifier would try to load
 * {@code VoicechatPlugin} while verifying {@code onEnable()} and fail with
 * {@code NoClassDefFoundError} — taking the whole plugin down before any try/catch could run.</p>
 *
 * <p>Keeping the references here means this class is only loaded/verified when
 * {@link #register(VampireSMPPlugin)} is actually called, which the caller only does after
 * confirming the voicechat plugin is present.</p>
 */
public final class VoicechatHook {

    private VoicechatHook() {}

    /** Register the ghost voice-haunt add-on. Only call when the "voicechat" plugin is loaded. */
    public static boolean register(VampireSMPPlugin plugin) {
        BukkitVoicechatService service =
                plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) return false;
        service.registerPlugin(new GhostVoicechatPlugin(plugin));
        return true;
    }
}
