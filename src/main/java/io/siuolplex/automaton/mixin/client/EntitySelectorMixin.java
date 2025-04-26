package io.siuolplex.automaton.mixin.client;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.siuolplex.automaton.client.EntitySelectorClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

@Mixin(EntitySelector.class)
@Environment(EnvType.CLIENT)
public abstract class EntitySelectorMixin implements EntitySelectorClient {
    @Shadow @Final private @Nullable String playerName;
    @Shadow @Final private @Nullable UUID entityUUID;
    @Shadow @Final private Function<Vec3, Vec3> position;
    @Shadow @Final private boolean currentEntity;
    @Shadow @Nullable protected abstract AABB getAbsoluteAabb(Vec3 pos);
    @Shadow protected abstract Predicate<Entity> getPredicate(Vec3 pos, @Nullable AABB box, @Nullable FeatureFlagSet enabledFeatures);
    @Shadow protected abstract int getResultLimit();
    @Shadow protected abstract <T extends Entity> List<T> sortAndLimit(Vec3 pos, List<T> entities);

    @Override
    public List<AbstractClientPlayer> automaton$findPlayersClient(FabricClientCommandSource source) {
        if (this.playerName != null) {
            List<AbstractClientPlayer> players = source.getWorld().players();
            return players.stream().filter(p -> p.getName().getString().equals(this.playerName)).toList();
        }

        if (this.entityUUID != null) {
            AbstractClientPlayer player = (AbstractClientPlayer) source.getWorld().getPlayerByUUID(this.entityUUID);
            return player == null ? List.of() : List.of(player);
        }

        Vec3 position = this.position.apply(source.getPosition());
        AABB box = this.getAbsoluteAabb(position);
        Predicate<Entity> checker = this.getPredicate(position, box, null);
        if (this.currentEntity) {
            return checker.test(source.getPlayer()) ? List.of(source.getPlayer()) : List.of();
        }

        int max = this.getResultLimit();
        int[] current = {0};
        List<AbstractClientPlayer> players = source.getWorld().players().stream()
                .filter(checker)
                .takeWhile(ignored -> current[0]++ < max)
                .toList();

        return this.sortAndLimit(position, players);
    }

    @Override
    public AbstractClientPlayer automaton$findSinglePlayerClient(FabricClientCommandSource source) throws CommandSyntaxException {
        List<AbstractClientPlayer> players = this.automaton$findPlayersClient(source);
        if (players.size() == 1) {
            return players.get(0);
        } else {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        }
    }
}
