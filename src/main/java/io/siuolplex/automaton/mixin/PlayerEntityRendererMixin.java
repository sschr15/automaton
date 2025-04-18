package io.siuolplex.automaton.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerEntityRendererMixin {
    @WrapOperation(method = "setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/geom/ModelPart;visible:Z", opcode = Opcodes.PUTFIELD),
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isSpectator()Z"),
                    to = @At(value = "FIELD", target = "Lnet/minecraft/client/model/PlayerModel;hat:Lnet/minecraft/client/model/geom/ModelPart;", ordinal = 1)
            ))
    private void noSpectatorHead$noModel(ModelPart instance, boolean value, Operation<Void> original, AbstractClientPlayer player) {
        if (!(player instanceof LocalPlayer)) instance.visible = false;
        else original.call(instance, value);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At(value = "HEAD"), cancellable = true)
    private void noSpectatorHead$noTag(AbstractClientPlayer player, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick, CallbackInfo ci) {
        if ((player.isSpectator())) ci.cancel();
    }
}