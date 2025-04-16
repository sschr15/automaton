package io.siuolplex.automaton.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.siuolplex.automaton.CameraPoint;
import io.siuolplex.automaton.EntityLockOn;
import io.siuolplex.automaton.mixin.GameRendererInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class AutomatonClient implements ClientModInitializer {
    public static KeyMapping CREATE_CAMERA_POINT_KEY;
    public static KeyMapping TELEPORT_TO_POINT_KEY;
    public static KeyMapping LOCK_ON_KEY;
    public static CameraPoint POINT;
    public static EntityLockOn LOCK_ON;
    private static int tickTimer = 0;

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
                client.player.displayClientMessage(Component.literal("Created Camera Point"), false);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (TELEPORT_TO_POINT_KEY.consumeClick()) {
                if (POINT == null) {
                    client.player.displayClientMessage(Component.literal("No current point to teleport to!"), false);
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
                        client.player.displayClientMessage(Component.literal("Locked on to entity " + LOCK_ON.lockedOnEntity().getName()), false);
                    } else {
                        client.player.displayClientMessage(Component.literal("Could not lock in."), false);
                    }
                } else {
                    client.player.displayClientMessage(Component.literal("Unlocked camera"), false);
                    LOCK_ON = null;
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (LOCK_ON != null && LOCK_ON.verifyLockExistance() && client.player.distanceTo(LOCK_ON.lockedOnEntity()) <= 100) {
                float xRot0 = client.player.getXRot();
                float yRot0 = client.player.getYRot();
                client.player.setXRot(xRot0);
                client.player.setYRot(yRot0);

                client.player.lookAt(EntityAnchorArgument.Anchor.EYES, LOCK_ON.lockedOnEntity().getEyePosition(1f));
                float xRot = client.player.getXRot();
                float yRot = client.player.getYRot();
                client.player.setXRot(Mth.rotLerp(client.getTimer().getRealtimeDeltaTicks(), xRot0, xRot));
                client.player.setYRot(Mth.rotLerp(client.getTimer().getRealtimeDeltaTicks(), yRot0, yRot));


                client.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        yRot,
                        xRot,
                        client.player.onGround()));
            } else if (LOCK_ON != null) {
                LOCK_ON = null;
                client.player.displayClientMessage(Component.literal("Unlocked camera"), false);
            }
        });
    }
}
