package io.siuolplex.automaton.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.siuolplex.automaton.Automaton;
import io.siuolplex.automaton.CameraPoint;
import io.siuolplex.automaton.EntityLockOn;
import io.siuolplex.automaton.mixin.GameRendererInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.sql.Time;
import java.time.Instant;

public class AutomatonClient implements ClientModInitializer {
    public static KeyMapping CREATE_CAMERA_POINT_KEY;
    public static KeyMapping TELEPORT_TO_POINT_KEY;
    public static KeyMapping LOCK_ON_KEY;
    public static CameraPoint POINT;
    public static EntityLockOn LOCK_ON;
    private static int tickTimer = 0;
    public static long current = 0;
    public static int fps = 0;

    @Override
    public void onInitializeClient() {
        CREATE_CAMERA_POINT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.automaton.create_camera_point",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.automaton.keybinds"
        ));

        TELEPORT_TO_POINT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.automaton.teleport_to_point",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.automaton.keybinds"
        ));

        LOCK_ON_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.automaton.lock_on",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.automaton.keybinds"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CREATE_CAMERA_POINT_KEY.consumeClick()) {
                POINT = CameraPoint.createFromPlayer(client.player);
                client.player.displayClientMessage(Component.translatable("automaton.camera.create_cam_point"), false);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TELEPORT_TO_POINT_KEY.consumeClick()) {
                if (POINT == null) {
                    client.player.displayClientMessage(Component.translatable("automaton.camera.no_valid_point"), false);
                } else if (!client.player.isSpectator()) {
                    client.player.displayClientMessage(Component.literal("Will not teleport you unless you are in spectator"), false);
                }
                else {
                    client.getConnection().sendCommand(POINT.transformIntoCommand());
                    client.player.displayClientMessage(Component.literal("Teleported to point!"), false);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (LOCK_ON_KEY.consumeClick()) {
                if (LOCK_ON == null) {
                    GameRenderer gameRenderer = client.gameRenderer;
                    HitResult hit = ((GameRendererInvoker) gameRenderer).invokePick(client.player, client.player.blockInteractionRange(), 100, client.getTimer().getGameTimeDeltaPartialTick(true));

                    if (hit.getType() == HitResult.Type.ENTITY) {
                        LOCK_ON = new EntityLockOn(((EntityHitResult)hit).getEntity());
                        client.player.displayClientMessage(Component.translatable("Locked on to entity ", LOCK_ON.lockedOnEntity().getName()), false);
                    } else {
                        client.player.displayClientMessage(Component.literal("Could not lock in."), false);
                    }
                } else {
                    client.player.displayClientMessage(Component.literal("Unlocked camera"), false);
                    LOCK_ON = null;
                }
            }
        });

        WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
            if (LOCK_ON != null && LOCK_ON.verifyLockExistance() && ctx.camera().getEntity().distanceTo(LOCK_ON.lockedOnEntity()) <= 100) {
                if (ctx.camera().getEntity() instanceof LocalPlayer player) {
                    float xRot0 = player.initialXRotation(); // Initial X and Y rots for the tick.
                    float yRot0 = player.initialYRotation();
                    ctx.camera().getEntity().lookAt(EntityAnchorArgument.Anchor.EYES, LOCK_ON.lockedOnEntity().getEyePosition(ctx.tickCounter().getGameTimeDeltaPartialTick(true))); // Lock eye pos on it, kinda have to do every frame, though I'd prefer not to.
                    float xRot = player.getXRot(); // Get rot
                    float yRot = player.getYRot();
                    // Set the rotation to this, again, kinda have to do every frame.
                    player.setXRot(Mth.rotLerp(ctx.tickCounter().getGameTimeDeltaPartialTick(true), xRot0, xRot));
                    player.setYRot(Mth.rotLerp(ctx.tickCounter().getGameTimeDeltaPartialTick(true), yRot0, yRot));
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (LOCK_ON != null && LOCK_ON.verifyLockExistance() && client.player != null && client.player.distanceTo(LOCK_ON.lockedOnEntity()) <= 100) {
            } else if (LOCK_ON != null) {
                LOCK_ON = null;
                if (client.player != null) client.player.displayClientMessage(Component.literal("Unlocked camera"), false);
            }
        });
    }
}
