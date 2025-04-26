package io.siuolplex.automaton.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.siuolplex.automaton.Automaton;
import io.siuolplex.automaton.CameraPoint;
import io.siuolplex.automaton.EntityLockOn;
import io.siuolplex.automaton.mixin.GameRendererInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.RangeArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.sql.Time;
import java.time.Instant;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AutomatonClient implements ClientModInitializer {
    public static KeyMapping CREATE_CAMERA_POINT_KEY;
    public static KeyMapping TELEPORT_TO_POINT_KEY;
    public static KeyMapping LOCK_ON_KEY;

    public static final KeyMapping[] PLAYER_LOCK_KEYS = new KeyMapping[9];
    public static final AbstractClientPlayer[] LOCK_ON_TARGETS = new AbstractClientPlayer[9];

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

        for (int i = 0; i < PLAYER_LOCK_KEYS.length; i++) {
            PLAYER_LOCK_KEYS[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.automaton.lock_onto_" + i,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_KP_1 + i,
                    "category.automaton.keybinds"
            ));
        }

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            dispatcher.register(literal("automaton_follow")
                    .then(argument("index", IntegerArgumentType.integer(1, PLAYER_LOCK_KEYS.length))
                    .then(argument("target", EntityArgument.player())
                    .executes(context -> {
                        EntitySelector selector = context.getArgument("target", EntitySelector.class);
                        AbstractClientPlayer player = ((EntitySelectorClient) selector).automaton$findSinglePlayerClient(context.getSource());
                        int slot = IntegerArgumentType.getInteger(context, "index");
                        LOCK_ON_TARGETS[slot - 1] = player;
                        context.getSource().sendFeedback(Component.translatable("commands.lockon.set_slot", slot, player.getDisplayName()));
                        return Command.SINGLE_SUCCESS;
                    })))
            );
        });

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
                    client.player.displayClientMessage(Component.translatable("automaton.camera.no_spectator"), true);
                }
                else {
                    client.getConnection().sendCommand(POINT.transformIntoCommand());
                    client.player.displayClientMessage(Component.translatable("automaton.camera.teleported"), true);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (LOCK_ON_KEY.consumeClick()) {
                lockOnto(client, null);
            }

            for (int i = 0; i < PLAYER_LOCK_KEYS.length; i++) {
                if (PLAYER_LOCK_KEYS[i].consumeClick()) {
                    lockOnto(client, LOCK_ON_TARGETS[i]);
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
                if (client.player != null) client.player.displayClientMessage(Component.translatable("automaton.camera.unlocked"), true);
            }
        });
    }

    private static void lockOnto(Minecraft client, @Nullable AbstractClientPlayer target) {
        assert client.player != null;

        if (target != null) {
            if (LOCK_ON != null && LOCK_ON.lockedOnEntity() == target) {
                LOCK_ON = null;
                client.player.displayClientMessage(Component.translatable("automaton.camera.unlocked"), true);
                return;
            }

            if (!client.level.isLoaded(target.blockPosition())) {
                client.player.displayClientMessage(Component.translatable("automaton.camera.unable_to_lock"), true);
                return;
            }

            LOCK_ON = new EntityLockOn(target);
            client.player.displayClientMessage(Component.translatable("automaton.camera.locked_on", target.getDisplayName()), true);
            return;
        }

        if (LOCK_ON == null) {
            GameRenderer gameRenderer = client.gameRenderer;
            HitResult hit = ((GameRendererInvoker) gameRenderer).invokePick(client.player, client.player.blockInteractionRange(), 100, client.getTimer().getGameTimeDeltaPartialTick(true));

            if (hit.getType() == HitResult.Type.ENTITY) {
                LOCK_ON = new EntityLockOn(((EntityHitResult)hit).getEntity());
                client.player.displayClientMessage(Component.translatable("automaton.camera.locked_on", LOCK_ON.lockedOnEntity().getName()), true);
            } else {
                client.player.displayClientMessage(Component.translatable("automaton.camera.unable_to_lock"), true);
            }
        } else {
            client.player.displayClientMessage(Component.translatable("automaton.camera.unlocked"), true);
            LOCK_ON = null;
        }
    }
}
