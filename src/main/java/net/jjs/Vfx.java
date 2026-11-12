package net.jjs;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/** Server-side senders for the client-rendered visual effects. */
public final class Vfx {
    public static final int ORB = 0, SHOCK = 1, BEAM = 2, DOMAIN = 3, SLASH = 4, LIGHTNING = 5,
            SPIKES = 6, HEAL = 7, AURA = 8, CHARGE = 9, BOLT = 10, POOL = 11;
    public static final int SCREEN_SHAKE = 0, SCREEN_FLASH = 1, SCREEN_BLACKFLASH = 2, SCREEN_VIGNETTE = 3;

    public static void spawn(ServerWorld w, int type, Vec3d pos, Vec3d dir, int dur, int color, float scale, int style) {
        spawn(w, type, pos, dir, dur, color, scale, style, 0f);
    }

    public static void spawn(ServerWorld w, int type, Vec3d pos, Vec3d dir, int dur, int color, float scale, int style, float extra) {
        for (ServerPlayerEntity sp : w.getPlayers()) {
            if (sp.squaredDistanceTo(pos) > 128 * 128 && type != DOMAIN) continue;
            PacketByteBuf b = PacketByteBufs.create();
            b.writeInt(type);
            b.writeDouble(pos.x); b.writeDouble(pos.y); b.writeDouble(pos.z);
            b.writeFloat((float) dir.x); b.writeFloat((float) dir.y); b.writeFloat((float) dir.z);
            b.writeInt(dur); b.writeInt(color); b.writeFloat(scale); b.writeInt(style); b.writeFloat(extra);
            ServerPlayNetworking.send(sp, Net.VFX, b);
        }
    }

    /** Screen effect for everyone within radius, scaled by distance. */
    public static void screen(ServerWorld w, Vec3d pos, double radius, int kind, int dur, float intensity, int color) {
        for (ServerPlayerEntity sp : w.getPlayers()) {
            double d = Math.sqrt(sp.squaredDistanceTo(pos));
            double f = 1.0 - d / radius;
            if (f <= 0.05) continue;
            screenTo(sp, kind, dur, (float) (intensity * Math.max(0.25, f)), color);
        }
    }

    public static void screenTo(ServerPlayerEntity sp, int kind, int dur, float intensity, int color) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeInt(kind); b.writeInt(dur); b.writeFloat(intensity); b.writeInt(color);
        ServerPlayNetworking.send(sp, Net.SCREEN, b);
    }

    private Vfx() {}
}
