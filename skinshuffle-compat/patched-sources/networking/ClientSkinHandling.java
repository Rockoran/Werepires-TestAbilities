package dev.imb11.skinshuffle.networking;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.imb11.skinshuffle.api.data.SkinQueryResult;
import dev.imb11.skinshuffle.client.config.SkinPresetManager;
import dev.imb11.skinshuffle.util.SkinShuffleClientPlayer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.SkinTextures;
import org.jetbrains.annotations.Nullable;

public class ClientSkinHandling {
    private static boolean handshakeTakenPlace = false;

    private static boolean reconnectRequired = false;

    /**
     * Skin textures forced onto the local player by the server via
     * {@link dev.imb11.skinshuffle.networking.ForceSkinPayload}. When non-null this takes
     * priority over the locally chosen SkinShuffle preset for the client's own model, so a
     * server can switch the player's skin without requiring a reconnect.
     */
    private static @Nullable SkinTextures forcedSkin = null;

    public static @Nullable SkinTextures getForcedSkin() {
        return forcedSkin;
    }

    public static void clearForcedSkin() {
        forcedSkin = null;
    }

    public static boolean isReconnectRequired() {
        return reconnectRequired;
    }

    public static void setReconnectRequired(boolean reconnectRequired) {
        ClientSkinHandling.reconnectRequired = reconnectRequired;
    }

    public static boolean isInstalledOnServer() {
        return handshakeTakenPlace;
    }

    public static void sendRefresh(SkinQueryResult result) {
        ClientPlayNetworking.send(new SkinRefreshPayload(result.toProperty()));
    }

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SkinPresetManager.setApiPreset(null);
        });

        ClientPlayConnectionEvents.INIT.register((handler, client) -> {
            if (client.world == null) return;
            handshakeTakenPlace = false;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            handshakeTakenPlace = false;
            setReconnectRequired(false);
            clearForcedSkin();
            SkinPresetManager.setApiPreset(null);
        });

        ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.PACKET_ID, (payload, context) -> {
            handshakeTakenPlace = true;
        });

        ClientPlayNetworking.registerGlobalReceiver(RefreshPlayerListEntryPayload.PACKET_ID, (payload, context) -> {
            int id = payload.entityID();
            MinecraftClient client = context.client();
            client.execute(() -> {
                ClientWorld world = client.world;
                if (world != null) {
                    Entity entity = world.getEntityById(id);
                    if (entity instanceof AbstractClientPlayerEntity player) {
                        ((SkinShuffleClientPlayer) player).skinShuffle$refreshPlayerListEntry();
                    }
                }
            });
        });

        // A server (e.g. a Paper plugin) can force a skin onto the local client. Receiving
        // this payload also implies the server speaks the SkinShuffle protocol, so we mark
        // the handshake as having taken place to suppress the reconnect prompt.
        ClientPlayNetworking.registerGlobalReceiver(ForceSkinPayload.PACKET_ID, (payload, context) -> {
            MinecraftClient client = context.client();
            handshakeTakenPlace = true;

            if (payload.clear() || payload.property() == null) {
                client.execute(() -> {
                    clearForcedSkin();
                    refreshLocalPlayer(client);
                });
                return;
            }

            // Resolve the texture Property into SkinTextures off-thread, then store + refresh
            // on the client thread. A freshly built GameProfile backs its properties() with an
            // immutable multimap, so we assemble a mutable PropertyMap and pass it in directly.
            Multimap<String, Property> backing = LinkedHashMultimap.create();
            backing.put("textures", payload.property());
            PropertyMap properties = new PropertyMap(backing);
            GameProfile profile = new GameProfile(
                    client.getGameProfile().id(),
                    client.getGameProfile().name(),
                    properties
            );

            client.getSkinProvider().fetchSkinTextures(profile).thenAccept(optional ->
                    optional.ifPresent(textures -> client.execute(() -> {
                        forcedSkin = textures;
                        refreshLocalPlayer(client);
                    })));
        });
    }

    /**
     * Forces the local player's own model and player-list entry to re-evaluate their skin,
     * picking up any freshly applied {@link #forcedSkin} without needing a respawn/reconnect.
     */
    private static void refreshLocalPlayer(MinecraftClient client) {
        if (client.player instanceof SkinShuffleClientPlayer skinShufflePlayer) {
            skinShufflePlayer.skinShuffle$refreshPlayerListEntry();
        }
    }
}
