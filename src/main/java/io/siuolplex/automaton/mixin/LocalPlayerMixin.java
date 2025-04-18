package io.siuolplex.automaton.mixin;

import com.mojang.authlib.GameProfile;
import io.siuolplex.automaton.InitialTickRotations;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer implements InitialTickRotations {
    public LocalPlayerMixin(ClientLevel clientLevel, GameProfile gameProfile) {
        super(clientLevel, gameProfile);
    }

    @Unique
    private float initialXRot = 0f;
    @Unique
    private float initialYRot = 0f;

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"))
    public void automaton$setInitialRotationsForTick(CallbackInfo ci) {
        initialXRot = getXRot();
        initialYRot = getYRot();
    }

    @Override
    public float initialXRotation() {
        return initialXRot;
    }

    @Override
    public float initialYRotation() {
        return initialYRot;
    }
}
