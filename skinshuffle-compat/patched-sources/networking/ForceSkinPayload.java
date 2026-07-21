package dev.imb11.skinshuffle.networking;

import com.mojang.authlib.properties.Property;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * S2C payload used by a (non-Fabric) server, such as a Paper plugin, to force a
 * specific skin texture onto the local client without requiring a reconnect.
 *
 * <p>When {@code clear} is {@code true} the client releases the server-forced skin and
 * falls back to the locally chosen SkinShuffle preset. Otherwise {@code property} carries
 * the {@code textures} {@link Property} (base64 value + optional signature) to apply to the
 * local player's own rendered model.</p>
 *
 * <p>The byte layout mirrors {@link SkinRefreshPayload} so the same encoder/decoder logic
 * can be reused on the server side:</p>
 * <pre>
 *   boolean clear
 *   if (!clear) {
 *       boolean hasSignature
 *       String  name
 *       String  value
 *       String  signature   // only when hasSignature
 *   }
 * </pre>
 */
public record ForceSkinPayload(boolean clear, @Nullable Property property) implements CustomPayload {
    public static final CustomPayload.Id<ForceSkinPayload> PACKET_ID =
            new CustomPayload.Id<>(Identifier.of("skinshuffle", "force_skin"));

    public static final PacketCodec<RegistryByteBuf, ForceSkinPayload> PACKET_CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.clear());
                if (!value.clear()) {
                    Property property = value.property();
                    boolean hasSignature = property != null && property.hasSignature();
                    buf.writeBoolean(hasSignature);
                    buf.writeString(property == null ? "textures" : property.name());
                    buf.writeString(property == null ? "" : property.value());
                    if (hasSignature) {
                        buf.writeString(property.signature());
                    }
                }
            },
            (buf) -> {
                boolean clear = buf.readBoolean();
                if (clear) {
                    return new ForceSkinPayload(true, null);
                }
                boolean hasSignature = buf.readBoolean();
                String name = buf.readString();
                String value = buf.readString();
                String signature = hasSignature ? buf.readString() : null;
                return new ForceSkinPayload(false, new Property(name, value, signature));
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}
