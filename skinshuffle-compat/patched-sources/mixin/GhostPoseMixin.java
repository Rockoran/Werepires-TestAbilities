package dev.imb11.skinshuffle.mixin;

import dev.imb11.skinshuffle.ghost.GhostState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class GhostPoseMixin {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void skinShuffle$ghostStandingPose(CallbackInfo ci) {
        if (!GhostState.shouldClip()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (((Object) this) != client.player) return;

        client.player.setPose(EntityPose.STANDING);
        ci.cancel();
    }
}
