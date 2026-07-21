package dev.imb11.skinshuffle.mixin;

import dev.imb11.skinshuffle.ghost.GhostState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class GhostCameraMixin {

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void skinShuffle$ghostCameraNoClip(double desiredCameraDistance,
                                                CallbackInfoReturnable<Double> cir) {
        if (!GhostState.shouldClip()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        cir.setReturnValue(desiredCameraDistance);
    }
}
