package pow.crimson2.ghost;

import java.util.UUID;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.StaticSoundPacket;

import pow.crimson2.VampireSMPPlugin;

/**
 * Simple Voice Chat add-on for the ghost/haunt voice mechanic.
 *
 * <p>When a haunting ghost speaks while {@link GhostModeManager#getHauntTarget(UUID)} is set,
 * we <b>cancel</b> the normal proximity broadcast (so nobody hears the ghost) and instead
 * <b>relay</b> the audio as a static (non-positional) sound to <i>only</i> the haunt target.
 * The target's own voice is untouched, so they stay fully open to everyone else.</p>
 */
public class GhostVoicechatPlugin implements VoicechatPlugin {

    private final VampireSMPPlugin plugin;
    private final GhostModeManager ghost;

    public GhostVoicechatPlugin(VampireSMPPlugin plugin) {
        this.plugin = plugin;
        this.ghost = plugin.getGhostModeManager();
    }

    @Override
    public String getPluginId() {
        return "vampiresmp_ghost";
    }

    @Override
    public void initialize(VoicechatApi api) {
        // No setup needed; the server API is obtained per-event.
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        VoicechatConnection sender = event.getSenderConnection();
        if (sender == null || sender.getPlayer() == null) return;

        UUID ghostId = sender.getPlayer().getUuid();
        UUID targetId = ghost.getHauntTarget(ghostId);
        if (targetId == null) return; // this speaker isn't haunting anyone — normal voice

        // The ghost is voice-haunting someone: never let the world hear them.
        if (event.isCancellable()) event.cancel();

        VoicechatConnection target = event.getVoicechat().getConnectionOf(targetId);
        if (target == null) return; // target offline / not connected to voice chat

        // Relay the exact audio to the target only, as a static (global) sound.
        StaticSoundPacket relay = event.getPacket().toStaticSoundPacket();
        event.getVoicechat().sendStaticSoundPacketTo(target, relay);
    }
}
