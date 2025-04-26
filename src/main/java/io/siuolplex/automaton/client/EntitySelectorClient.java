package io.siuolplex.automaton.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.AbstractClientPlayer;

import java.util.List;

@Environment(EnvType.CLIENT)
public interface EntitySelectorClient {
    List<AbstractClientPlayer> automaton$findPlayersClient(FabricClientCommandSource source);
    AbstractClientPlayer automaton$findSinglePlayerClient(FabricClientCommandSource source) throws CommandSyntaxException;
}
