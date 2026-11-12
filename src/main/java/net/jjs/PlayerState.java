package net.jjs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public final class PlayerState {
    public static final float MAX_CE = 200f;

    public static class Data {
        public float ce = MAX_CE;
        public final long[] readyAt = new long[6];
        public boolean reinforced;
        public long zoneUntil, overdriveUntil;
    }

    private static final Map<UUID, Data> MAP = new HashMap<>();

    public static Data data(PlayerEntity p) { return MAP.computeIfAbsent(p.getUuid(), k -> new Data()); }
    public static void reset(PlayerEntity p) { MAP.put(p.getUuid(), new Data()); }
    public static void clearAll() { MAP.clear(); }

    public static int character(ServerPlayerEntity p) {
        return JJSData.get(p.getServer()).chars.getOrDefault(p.getUuid(), -1);
    }

    public static void setCharacter(ServerPlayerEntity p, int c) {
        JJSData d = JJSData.get(p.getServer());
        d.chars.put(p.getUuid(), c);
        d.markDirty();
    }

    public static Sorcerer sorcerer(ServerPlayerEntity p) {
        int c = character(p);
        return (c >= 0 && c < Sorcerer.values().length) ? Sorcerer.values()[c] : null;
    }

    public static boolean consume(ServerPlayerEntity p, float amt) {
        if (p.isCreative()) return true;
        Data d = data(p);
        if (d.ce < amt) return false;
        d.ce -= amt;
        return true;
    }

    public static void add(ServerPlayerEntity p, float amt) {
        Data d = data(p);
        d.ce = Math.max(0, Math.min(MAX_CE, d.ce + amt));
    }

    public static void cast(ServerPlayerEntity p, int slot) {
        Sorcerer s = sorcerer(p);
        if (s == null || slot < 0 || slot > 5 || !p.isAlive() || p.isSpectator()) return;
        Data d = data(p);
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (now < d.readyAt[slot]) return;
        Ability a = Sorcerer.slot(s, slot);
        if (!consume(p, a.cost())) {
            p.sendMessage(Text.literal("\u00a7cNot enough cursed energy! (" + a.cost() + ")"), true);
            return;
        }
        boolean ok;
        try { ok = a.cast().cast(w, p); } catch (Exception e) { e.printStackTrace(); ok = false; }
        if (!ok) { add(p, a.cost()); return; }
        d.readyAt[slot] = now + a.cooldown();
        sync(p);
    }

    public static void tick(ServerPlayerEntity p) {
        Data d = data(p);
        Sorcerer s = sorcerer(p);
        boolean still = p.isSneaking() && p.getVelocity().horizontalLengthSquared() < 0.0005;
        float regen = still ? 1.6f : 0.4f;

        if (d.reinforced) {
            d.ce -= 0.8f;
            if (d.ce <= 1f) {
                d.ce = 0; d.reinforced = false;
                p.sendMessage(Text.literal("\u00a7cReinforcement faded."), true);
            } else if (p.age % 10 == 0 && s != null) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 2, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 30, 2, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 30, 2, false, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30, 1, false, false));
                Vfx.spawn(p.getServerWorld(), Vfx.AURA, p.getPos(), Vec3d.ZERO, 14, s.color, 1f, p.getId());
            }
        }
        d.ce = Math.max(0, Math.min(MAX_CE, d.ce + regen));
        if (p.age % 2 == 0) sync(p);
    }

    public static void sync(ServerPlayerEntity p) {
        Data d = data(p);
        long now = p.getServerWorld().getTime();
        PacketByteBuf b = PacketByteBufs.create();
        b.writeInt(character(p));
        b.writeFloat(d.ce);
        b.writeBoolean(d.reinforced);
        b.writeInt((int) Math.max(0, d.zoneUntil - now));
        for (int i = 0; i < 6; i++) b.writeInt((int) Math.max(0, d.readyAt[i] - now));
        ServerPlayNetworking.send(p, Net.STATE, b);
    }

    private PlayerState() {}
}
