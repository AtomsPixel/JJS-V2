package net.jjs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/** Saves each player's chosen sorcerer with the world. */
public class JJSData extends PersistentState {
    public final Map<UUID, Integer> chars = new HashMap<>();

    public static JJSData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(JJSData::fromNbt, JJSData::new, "jjs_data");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound c = new NbtCompound();
        chars.forEach((k, v) -> c.putInt(k.toString(), v));
        nbt.put("chars", c);
        return nbt;
    }

    public static JJSData fromNbt(NbtCompound nbt) {
        JJSData d = new JJSData();
        NbtCompound c = nbt.getCompound("chars");
        for (String k : c.getKeys()) d.chars.put(UUID.fromString(k), c.getInt(k));
        return d;
    }
}
