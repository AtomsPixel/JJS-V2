package net.jjs.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jjs.Vfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Screen shake, flashes, Black Flash overlay and domain vignette. */
public final class ScreenFx {
    private static final class Fx {
        int kind, dur, age, color; float intensity; long seed;
    }
    private static final List<Fx> LIST = new ArrayList<>();
    private static final Random RNG = new Random();
    private static float prevDx, prevDy;

    public static void add(int kind, int dur, float intensity, int color) {
        Fx f = new Fx();
        f.kind = kind; f.dur = Math.max(1, dur); f.intensity = intensity; f.color = color; f.seed = RNG.nextLong();
        if (LIST.size() < 40) LIST.add(f);
    }

    public static void tick(MinecraftClient mc) {
        float amp = 0;
        for (Fx f : new ArrayList<>(LIST)) {
            if (f.kind == Vfx.SCREEN_SHAKE) amp += f.intensity * (1f - f.age / (float) f.dur);
            f.age++;
            if (f.age >= f.dur) LIST.remove(f);
        }
        if (mc.player == null) { prevDx = prevDy = 0; return; }
        amp = Math.min(amp, 3f);
        float dx = amp > 0 ? (RNG.nextFloat() - 0.5f) * 2f * amp : 0;
        float dy = amp > 0 ? (RNG.nextFloat() - 0.5f) * 2f * amp : 0;
        mc.player.setYaw(mc.player.getYaw() + dx - prevDx);
        mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() + dy - prevDy, -90f, 90f));
        prevDx = dx; prevDy = dy;
    }

    public static void overlay(DrawContext ctx, float td) {
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        for (Fx f : LIST) {
            float t = f.age + td;
            float life = MathHelper.clamp(t / f.dur, 0, 1);
            switch (f.kind) {
                case Vfx.SCREEN_FLASH -> {
                    float a = (float) Math.pow(1 - life, 2) * Math.min(1f, f.intensity);
                    ctx.fill(0, 0, w, h, argb((int) (a * 235), f.color));
                }
                case Vfx.SCREEN_VIGNETTE -> {
                    float a = (float) Math.sin(Math.PI * life) * 0.55f * f.intensity;
                    vignette(ctx, w, h, f.color, a);
                }
                case Vfx.SCREEN_BLACKFLASH -> {
                    if (t < 2.5f) ctx.fill(0, 0, w, h, argb(245, 0x000000));
                    else ctx.fill(0, 0, w, h, argb((int) (150 * (1 - life)), 0x000000));
                    vignette(ctx, w, h, 0xff1030, 0.8f * (1 - life));
                    if (t < 6f) bolts(ctx, w, h, f, (int) (t / 1.5f));
                }
                default -> {}
            }
        }
    }

    private static void vignette(DrawContext ctx, int w, int h, int color, float a) {
        int steps = 18;
        int sx = Math.max(2, w / 14), sy = Math.max(2, h / 8);
        for (int i = 0; i < steps; i++) {
            float k = 1f - i / (float) steps;
            int col = argb((int) (a * 120 * k * k), color);
            int ox = (int) (sx * (i / (float) steps)), oy = (int) (sy * (i / (float) steps));
            ctx.fill(0, oy, w, oy + Math.max(1, sy / steps), col);
            ctx.fill(0, h - oy - Math.max(1, sy / steps), w, h - oy, col);
            ctx.fill(ox, 0, ox + Math.max(1, sx / steps), h, col);
            ctx.fill(w - ox - Math.max(1, sx / steps), 0, w - ox, h, col);
        }
    }

    private static void bolts(DrawContext ctx, int w, int h, Fx f, int frame) {
        Random r = new Random(f.seed + frame * 7919L);
        for (int k = 0; k < 7; k++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double x = w / 2.0 + (r.nextDouble() - 0.5) * w * 0.15, y = h / 2.0 + (r.nextDouble() - 0.5) * h * 0.15;
            for (int s = 0; s < 9; s++) {
                ang += (r.nextDouble() - 0.5) * 1.1;
                double len = Math.max(w, h) * (0.07 + r.nextDouble() * 0.06);
                double nx = x + Math.cos(ang) * len, ny = y + Math.sin(ang) * len;
                seg(ctx, x, y, nx, ny, 3, 0xff1030);
                seg(ctx, x, y, nx, ny, 1, 0xffffff);
                x = nx; y = ny;
            }
        }
    }

    private static void seg(DrawContext ctx, double x1, double y1, double x2, double y2, int th, int color) {
        double dx = x2 - x1, dy = y2 - y1;
        int len = (int) Math.sqrt(dx * dx + dy * dy);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x1, y1, 0);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotation((float) Math.atan2(dy, dx)));
        ctx.fill(0, -th, len, th, 0xFF000000 | color);
        ctx.getMatrices().pop();
    }

    public static int argb(int a, int rgb) { return (MathHelper.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF); }
    private ScreenFx() {}
}
