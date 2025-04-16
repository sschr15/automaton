package io.siuolplex.automaton;

import net.minecraft.world.entity.Entity;

public record EntityLockOn(Entity lockedOnEntity) {

    public boolean verifyLockExistance() {
        return (lockedOnEntity != null && lockedOnEntity.isAlive());
    }
}
