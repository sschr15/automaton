package io.siuolplex.automaton;

import net.minecraft.client.gui.spectator.categories.TeleportToPlayerMenuCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

public record CameraPoint(BlockPos pos, float xRot, float yRot) {
    public String transformIntoCommand() {
        return "teleport @p " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + yRot + " " + xRot;
    }

    public static CameraPoint createFromPlayer(Player player) {
        return new CameraPoint(player.blockPosition(), player.getXRot(), player.getYRot());

    }
}
