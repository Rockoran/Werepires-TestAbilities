package dev.imb11.skinshuffle.ghost;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

/**
 * Client handling for ghost/haunt mode: receives the {@code vsmp:ghost} toggle and, while
 * active (and clip enabled), makes the local player clip through blocks (and keeps them flying
 * so noclip doesn't drop them out of the world).
 *
 * <p>The client command {@code /clip [on|off]} lets the player toggle clip-through locally
 * (default on), e.g. to stand on a surface while haunting.</p>
 */
public final class GhostClient {

    private GhostClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(GhostModePayload.PACKET_ID, (payload, context) -> {
            boolean active = payload.active();
            context.client().execute(() -> {
                GhostState.active = active;
                if (active) GhostState.clipEnabled = true; // reset to default each time haunt begins
                if (context.client().player != null) {
                    context.client().player.sendMessage(Text.literal(
                            active ? "§5Haunt: noclip enabled §8(/clip to toggle)" : "§7Haunt: noclip disabled"), true);
                }
            });
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            GhostState.active = false;
            GhostState.clipEnabled = true;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            if (GhostState.shouldClip()) {
                player.noClip = true; // also stops suffocation while inside blocks
                // Keep flight on so a noclipping player doesn't fall through the world.
                player.getAbilities().flying = true;
            } else if (player.noClip && !player.isSpectator()) {
                player.noClip = false;
            }
        });

        registerClipCommand();
    }

    private static void registerClipCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root =
                ClientCommandManager.literal("clip")
                    .executes(ctx -> { setClip(ctx.getSource(), !GhostState.clipEnabled); return 1; })
                    .then(ClientCommandManager.literal("on")
                        .executes(ctx -> { setClip(ctx.getSource(), true); return 1; }))
                    .then(ClientCommandManager.literal("off")
                        .executes(ctx -> { setClip(ctx.getSource(), false); return 1; }));
            dispatcher.register(root);
        });
    }

    private static void setClip(FabricClientCommandSource source, boolean enabled) {
        GhostState.clipEnabled = enabled;
        if (!GhostState.active) {
            source.sendFeedback(Text.literal("§7Clip-through " + (enabled ? "on" : "off")
                    + " §8(only applies while haunting)."));
        } else {
            source.sendFeedback(Text.literal(enabled
                    ? "§5Clip-through §aenabled§5 — you pass through blocks."
                    : "§7Clip-through §cdisabled§7 — you collide normally."));
        }
    }
}
