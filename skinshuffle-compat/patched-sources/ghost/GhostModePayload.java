package dev.imb11.skinshuffle.ghost;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C payload telling the client to enter ({@code active=true}) or leave ghost/haunt mode.
 * Sent by the VampireSMP Paper plugin on the {@code vsmp:ghost} channel; body is a single boolean.
 */
public record GhostModePayload(boolean active) implements CustomPayload {
    public static final CustomPayload.Id<GhostModePayload> PACKET_ID =
            new CustomPayload.Id<>(Identifier.of("vsmp", "ghost"));

    public static final PacketCodec<RegistryByteBuf, GhostModePayload> PACKET_CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.active()),
            (buf) -> new GhostModePayload(buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
