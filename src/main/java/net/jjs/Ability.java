package net.jjs;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public record Ability(String name, String desc, int cost, int cooldown, String icon, int color, Cast cast) {
    public interface Cast { boolean cast(ServerWorld w, ServerPlayerEntity p); }
}
