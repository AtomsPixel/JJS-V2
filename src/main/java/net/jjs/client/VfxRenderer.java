package net.jjs.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jjs.Vfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class VfxRenderer {
    private static final Vec3d UP = Prims.UP;
    private static final List<Inst> LIST = new ArrayList<>();
    private static final Random SEED = new Random();

    private static final class Inst {
        int type, dur, age, style;
        Vec3d pos, dir;
        float r, g, b, scale, extra;
        long seed;
    }

    public static void add(int type, Vec3d pos, Vec3d dir, int dur, int color, float scale, int style, float extra) {
        if (LIST.size() > 160) return;
        Inst i = new Inst();
        i.type = type; i.pos = pos; i.dir = dir; i.dur = Math.max(1, dur); i.scale = scale; i.style = style; i.extra = extra;
        i.r = ((color >> 16) & 255) / 255f; i.g = ((color >> 8) & 255) / 255f; i.b = (color & 255) / 255f;
        i.seed = SEED.nextLong();
        LIST.add(i);
    }

    public static void tick() {
        for (Inst i : new ArrayList<>(LIST)) { i.age++; if (i.age >= i.dur) LIST.remove(i); }
    }

    public static void clear() { LIST.clear(); }

    public static void render(WorldRenderContext ctx) {
        if (LIST.isEmpty() || ctx.matrixStack() == null) return;
        Prims.frame(ctx.matrixStack(), ctx.camera());
        float pt = ctx.tickDelta();
        ClientWorld world = ctx.world();
        for (Inst i : new ArrayList<>(LIST)) {
            double t = i.age + pt;
            try {
                switch (i.type) {
                    case Vfx.ORB -> orb(i, t);
                    case Vfx.SHOCK -> shock(i, t);
                    case Vfx.BEAM -> beam(i, t);
                    case Vfx.DOMAIN -> domain(i, t);
                    case Vfx.SLASH -> slash(i, t);
                    case Vfx.LIGHTNING -> lightning(i, t);
                    case Vfx.SPIKES -> spikes(i, t);
                    case Vfx.HEAL -> heal(i, t, world, pt);
                    case Vfx.AURA -> aura(i, t, world, pt);
                    case Vfx.CHARGE -> charge(i, t);
                    case Vfx.BOLT -> bolt(i, t);
                    case Vfx.POOL -> pool(i, t);
                    default -> {}
                }
            } catch (Exception e) {
                Prims.recover();
            }
        }
        Prims.finish();
    }

    // ---------------------------------------------------------------- math
    private static double clamp(double x) { return x < 0 ? 0 : Math.min(x, 1); }
    private static double ease(double x) { x = clamp(x); return 1 - Math.pow(1 - x, 3); }
    private static double lerp(double a, double b, double f) { return a + (b - a) * f; }
    private static Vec3d jitter(Random r, double a) {
        return new Vec3d((r.nextDouble() - .5) * 2 * a, (r.nextDouble() - .5) * 2 * a, (r.nextDouble() - .5) * 2 * a);
    }
    private static Vec3d randDir(Random r) {
        Vec3d v = new Vec3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
        return v.lengthSquared() < 1e-6 ? UP : v.normalize();
    }
    private static Vec3d[] jag(Random r, Vec3d a, Vec3d b, int n, double amt) {
        Vec3d[] p = new Vec3d[n];
        for (int k = 0; k < n; k++) {
            double f = k / (double) (n - 1);
            Vec3d pt = a.add(b.subtract(a).multiply(f));
            p[k] = (k == 0 || k == n - 1) ? pt : pt.add(jitter(r, amt));
        }
        return p;
    }

    // ---------------------------------------------------------------- ORB (Blue singularity)
    private static void orb(Inst i, double t) {
        double s = ease(t / 8.0) * ease((i.dur - t) / 8.0);
        double R = i.scale * s;
        if (R < 0.02) return;
        Vec3d c = i.pos;
        Prims.begin(true);
        Prims.sphere(c, R * 0.55, 1, 1, 1, 1f, 1f);
        Prims.sphere(c, R * 0.85, i.r, i.g, i.b, 0.85f, 1f);
        Prims.sphere(c, R * 1.3, i.r, i.g, i.b, 0f, 0.5f);
        Prims.sphere(c, R * 2.0, i.r, i.g, i.b, 0f, 0.2f);
        Prims.sphere(c, R * 3.2, i.r, i.g, i.b, 0f, 0.07f);
        for (int k = 0; k < 3; k++) {
            double a = t * 0.07 + k * 1.05;
            Vec3d n = new Vec3d(Math.sin(a) * 0.8, Math.cos(a * 0.7), Math.cos(a) * 0.8);
            Prims.ring(c, n, R * 1.7, R * 1.95, 40, i.r, i.g, i.b, 0f, 0.6f);
            Prims.ring(c, n, R * 1.95, R * 2.2, 40, i.r, i.g, i.b, 0.6f, 0f);
        }
        for (int k = 0; k < 10; k++) {
            double a0 = k * Math.PI * 2 / 10 + t * 0.25;
            Vec3d[] pts = new Vec3d[18];
            for (int j = 0; j < 18; j++) {
                double f = j / 17.0;
                double rad = lerp(i.scale * 4.2, R * 1.15, f);
                double a = a0 + f * 2.4;
                double h = Math.sin(k * 1.7) * (1 - f) * i.scale * 1.3;
                pts[j] = c.add(Math.cos(a) * rad, h, Math.sin(a) * rad);
            }
            Prims.ribbon(pts, 0.1 * i.scale + 0.05, i.r, i.g, i.b, 0.9f * (float) s, 1);
        }
        Prims.end();
    }

    // ---------------------------------------------------------------- SHOCKWAVE
    private static void shock(Inst i, double t) {
        double u = clamp(t / i.dur);
        double rad = i.scale * ease(u);
        float a = (float) Math.pow(1 - u, 1.5);
        Vec3d c = i.pos;
        Prims.begin(true);
        Prims.ring(c, UP, rad * 0.78, rad, 56, i.r, i.g, i.b, 0f, a);
        Prims.ring(c, UP, rad, rad * 1.08, 56, i.r, i.g, i.b, a, 0f);
        if (i.dir.lengthSquared() > 0.01) {
            Prims.ring(c, i.dir, rad * 0.78, rad, 56, i.r, i.g, i.b, 0f, a);
            Prims.ring(c, i.dir, rad, rad * 1.08, 56, i.r, i.g, i.b, a, 0f);
        }
        Prims.sphere(c, rad * 0.9, i.r, i.g, i.b, 0f, a * 0.55f);
        Prims.sphere(c, Math.max(0.05, i.scale * 0.32 * (1 - u)), 1, 1, 1, a, a);
        Prims.end();
    }

    // ---------------------------------------------------------------- BEAM (Hollow Purple / streak)
    private static void beam(Inst i, double t) {
        Vec3d d = i.dir.normalize();
        double L = i.scale;
        double front = i.style == 0 ? L * ease(t / 5.0) : L * ease(t / 2.0);
        double env = Math.pow(Math.max(0, Math.sin(Math.PI * clamp(t / i.dur))), 0.5);
        double R = (i.style == 0 ? 2.6 : 0.45) * env;
        if (R < 0.02 || front < 0.1) return;
        Vec3d a = i.pos, e = a.add(d.multiply(front));
        Prims.begin(true);
        Prims.tube(a, e, R * 1.25, 22, i.r, i.g, i.b, 0.05f, 0.7f);
        Prims.tube(a, e, R * 0.8, 18, Math.min(1, i.r + 0.3f), Math.min(1, i.g + 0.3f), Math.min(1, i.b + 0.3f), 0.3f, 0.9f);
        Prims.tube(a, e, R * 0.4, 14, 1, 1, 1, 0.95f, 1f);
        if (i.style == 0) {
            for (int h = 0; h < 2; h++) {
                int n = (int) Math.max(4, front / 0.8);
                Vec3d[] pts = new Vec3d[n];
                Vec3d[] bs = Prims.basis(d);
                for (int k = 0; k < n; k++) {
                    double along = front * k / (n - 1.0);
                    double ang = along * 0.5 - t * 0.9 + h * Math.PI;
                    pts[k] = a.add(d.multiply(along)).add(bs[0].multiply(Math.cos(ang) * R * 1.3)).add(bs[1].multiply(Math.sin(ang) * R * 1.3));
                }
                if (h == 0) Prims.ribbon(pts, 0.5, 0.25f, 0.5f, 1f, 0.9f, 3);
                else Prims.ribbon(pts, 0.5, 1f, 0.2f, 0.25f, 0.9f, 3);
            }
            for (int k = 0; k < 8; k++) {
                double s = ((t * 4.0 + k * 6.0) % 48.0);
                if (s > front) continue;
                Prims.ring(a.add(d.multiply(s)), d, R * 1.3, R * 1.6, 28, i.r, i.g, i.b, 0.6f, 0f);
            }
            Prims.sphere(e, R * 1.6, i.r, i.g, i.b, 0.8f, 0.2f);
            Prims.sphere(e, R * 0.8, 1, 1, 1, 1f, 1f);
            Prims.sphere(a, R * 1.4, i.r, i.g, i.b, 0.8f, 0.2f);
        }
        Prims.end();
    }

    // ---------------------------------------------------------------- DOMAIN
    private static void domain(Inst i, double t) {
        double open = ease(t / 14.0);
        double closeF = clamp((i.dur - t) / 12.0);
        double R = i.scale * open * (0.8 + 0.2 * closeF);
        if (R < 0.1) return;
        float fade = (float) closeF;
        float[] base = switch (i.style) {
            case 0 -> new float[]{0.005f, 0f, 0.04f, 0.84f};
            case 1 -> new float[]{0.07f, 0f, 0.01f, 0.82f};
            default -> new float[]{0.02f, 0f, 0.05f, 0.86f};
        };
        Vec3d c = i.pos;
        Prims.begin(false);
        Prims.sphere(c, R, base[0], base[1], base[2], base[3] * fade, base[3] * fade, 22, 36);
        Prims.end();

        Prims.begin(true);
        Prims.sphere(c, R * 0.998, i.r, i.g, i.b, 0f, 0.55f * fade, 22, 36);
        Prims.ring(c.add(0, -0.95, 0), UP, R * 0.88, R * 0.99, 64, i.r, i.g, i.b, 0f, 0.7f * fade);
        double wave = ((t * 0.04) % 1.0);
        Prims.ring(c.add(0, -0.94, 0), UP, R * wave * 0.95, R * wave * 0.95 + 0.6, 48, i.r, i.g, i.b, 0.5f * fade, 0f);
        Random rnd = new Random(i.seed);
        if (i.style == 0) {
            for (int k = 0; k < 180; k++) {
                Vec3d p = c.add(randDir(rnd).multiply(R * 0.97));
                double tw = 0.5 + 0.5 * Math.sin(t * 0.15 + k);
                float sz = (float) ((0.15 + rnd.nextDouble() * 0.4) * (R / 14.0));
                int pick = rnd.nextInt(3);
                float rr = pick == 0 ? 1f : 0.6f, gg = pick == 0 ? 1f : 0.7f, bb = 1f;
                Prims.star(p, sz, rr, gg, bb, (float) tw * fade);
            }
        } else if (i.style == 1) {
            for (int k = 0; k < 16; k++) {
                Vec3d ax = randDir(rnd), bx = randDir(rnd);
                Vec3d u = ax.crossProduct(bx);
                u = u.lengthSquared() < 1e-6 ? UP : u.normalize();
                Vec3d v = ax.crossProduct(u).normalize();
                double start = rnd.nextDouble() * Math.PI * 2;
                Vec3d[] pts = new Vec3d[14];
                for (int j = 0; j < 14; j++) {
                    double ang = start + j * 0.09;
                    pts[j] = c.add(ax.multiply(Math.cos(ang) * R * 0.97)).add(v.multiply(Math.sin(ang) * R * 0.97));
                }
                if (((int) (t * 0.4 + k * 3)) % 5 < 3) {
                    Prims.ribbon(pts, 0.45, 1f, 0.1f, 0.15f, 0.9f * fade, 2);
                    Prims.ribbon(pts, 0.15, 1f, 0.8f, 0.8f, 0.9f * fade, 2);
                }
            }
            for (int k = 0; k < 50; k++) {
                Vec3d p = c.add(randDir(rnd).multiply(R * (0.3 + rnd.nextDouble() * 0.65)));
                double ty = ((t * 0.08 + rnd.nextDouble()) % 1.0);
                Prims.star(new Vec3d(p.x, c.y + R * 0.5 - ty * R, p.z), 0.12, 1f, 0.35f, 0.1f, (float) (1 - ty) * fade);
            }
        } else {
            for (int k = 0; k < 22; k++) {
                Vec3d dd = randDir(rnd);
                Vec3d p = c.add(new Vec3d(dd.x, -Math.abs(dd.y) * 0.5, dd.z).normalize().multiply(R * 0.96));
                if (((int) (t * 0.1 + k * 7)) % 12 == 0) continue;
                Vec3d side = new Vec3d(-dd.z, 0, dd.x).normalize().multiply(0.5);
                Prims.star(p.add(side), 0.22, 0.3f, 1f, 0.9f, 0.9f * fade);
                Prims.star(p.subtract(side), 0.22, 0.3f, 1f, 0.9f, 0.9f * fade);
            }
            for (int k = 0; k < 16; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2, rad = rnd.nextDouble() * R * 0.85;
                Vec3d b0 = c.add(Math.cos(ang) * rad, -0.95, Math.sin(ang) * rad);
                double hgt = 3 + rnd.nextDouble() * 5;
                Vec3d[] pts = new Vec3d[10];
                for (int j = 0; j < 10; j++) {
                    double f = j / 9.0;
                    pts[j] = b0.add(Math.sin(t * 0.1 + k + f * 3) * 0.8 * f, f * hgt * (0.7 + 0.3 * Math.sin(t * 0.15 + k)), Math.cos(t * 0.09 + k + f * 3) * 0.8 * f);
                }
                Prims.ribbon(pts, 0.55, i.r, i.g, i.b, 0.6f * fade, 2);
            }
        }
        Prims.end();
    }

    // ---------------------------------------------------------------- SLASH (crescent)
    private static void slash(Inst i, double t) {
        Vec3d fwd = i.dir.normalize();
        Vec3d base = fwd.crossProduct(UP);
        base = base.lengthSquared() < 1e-3 ? new Vec3d(1, 0, 0) : base.normalize();
        Vec3d up = base.crossProduct(fwd).normalize();
        double roll = Math.toRadians(i.style);
        Vec3d side = base.multiply(Math.cos(roll)).add(up.multiply(Math.sin(roll)));
        double R = i.scale;
        double u = clamp(t / i.dur);
        Vec3d c = i.pos.add(fwd.multiply(i.extra * ease(u * 1.3)));
        double maxT = Math.toRadians(75);
        double sweep = ease(t / (i.dur * 0.45));
        double th1 = -maxT + 2 * maxT * sweep;
        float fade = (float) (1 - clamp((t - i.dur * 0.45) / (i.dur * 0.55)));
        int n = 22;
        Vec3d[] pts = new Vec3d[n];
        for (int j = 0; j < n; j++) {
            double th = lerp(-maxT, th1, j / (double) (n - 1));
            pts[j] = c.add(side.multiply(R * Math.sin(th))).add(fwd.multiply(R * Math.cos(th) - R));
        }
        Prims.begin(true);
        Prims.ribbon(pts, R * 0.3, i.r, i.g, i.b, 0.6f * fade, 1);
        Prims.ribbon(pts, R * 0.12, Math.min(1, i.r + 0.4f), Math.min(1, i.g + 0.4f), Math.min(1, i.b + 0.4f), 0.85f * fade, 1);
        Prims.ribbon(pts, R * 0.05, 1, 1, 1, fade, 1);
        if (i.extra > 0.5) Prims.ribbon(new Vec3d[]{i.pos, c}, R * 0.07, i.r, i.g, i.b, 0.5f * fade, 1);
        Prims.end();
    }

    // ---------------------------------------------------------------- LIGHTNING (Black Flash)
    private static void lightning(Inst i, double t) {
        double u = clamp(t / i.dur);
        float a = (float) (1 - u);
        Random rnd = new Random(i.seed + (long) (t / 2));
        List<Vec3d[]> bolts = new ArrayList<>();
        for (int k = 0; k < 9; k++) {
            Vec3d d = randDir(rnd);
            double len = i.scale * (1.2 + rnd.nextDouble() * 1.8) * (0.5 + 0.5 * ease(u * 3));
            bolts.add(jag(rnd, i.pos, i.pos.add(d.multiply(len)), 9, len * 0.1));
        }
        Prims.begin(false);
        Prims.sphere(i.pos, Math.max(0.05, i.scale * 0.55 * (1 - u * 0.6)), 0f, 0f, 0f, 0.9f * a, 0.9f * a);
        for (Vec3d[] b : bolts) Prims.ribbon(b, 0.55, 0f, 0f, 0f, 0.9f * a, 2);
        Prims.end();
        Prims.begin(true);
        for (Vec3d[] b : bolts) {
            Prims.ribbon(b, 0.28, i.r, i.g, i.b, a, 2);
            Prims.ribbon(b, 0.08, 1, 1, 1, a, 2);
        }
        Prims.ring(i.pos, UP, i.scale * ease(u) * 1.2, i.scale * ease(u) * 1.35, 40, i.r, i.g, i.b, a, 0f);
        Prims.sphere(i.pos, i.scale * 0.25 * (1 - u), 1, 0.9f, 0.9f, a, a);
        Prims.end();
    }

    // ---------------------------------------------------------------- SPIKES
    private static void spikes(Inst i, double t) {
        double u = clamp(t / i.dur);
        double grow = ease(u / 0.25), sink = clamp((1 - u) / 0.25);
        Random rnd = new Random(i.seed);
        Prims.begin(false);
        for (int k = 0; k < 7; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2, rad = rnd.nextDouble() * i.scale;
            double hmax = i.scale * (1.2 + rnd.nextDouble() * 1.8);
            double h = hmax * grow * sink;
            Vec3d b = i.pos.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
            Prims.cone(b, UP, i.scale * 0.22, h, 8, 0.05f, 0.01f, 0.1f, 0.96f, 0.96f);
        }
        Prims.end();
        Prims.begin(true);
        rnd = new Random(i.seed);
        for (int k = 0; k < 7; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2, rad = rnd.nextDouble() * i.scale;
            double hmax = i.scale * (1.2 + rnd.nextDouble() * 1.8);
            double h = hmax * grow * sink;
            Vec3d b = i.pos.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
            Prims.cone(b, UP, i.scale * 0.3, h * 1.03, 8, i.r, i.g, i.b, 0.45f, 0f);
        }
        Prims.ring(i.pos.add(0, 0.05, 0), UP, 0, i.scale * (0.7 + u), 32, i.r, i.g, i.b, 0.4f * (float) (1 - u), 0f);
        Prims.end();
    }

    // ---------------------------------------------------------------- HEAL (attached)
    private static void heal(Inst i, double t, ClientWorld w, float pt) {
        Entity e = w == null ? null : w.getEntityById(i.style);
        Vec3d b = e != null ? e.getLerpedPos(pt) : i.pos;
        double h = e != null ? e.getHeight() : 1.8;
        double u = clamp(t / i.dur);
        float a = (float) Math.sin(Math.PI * u);
        Prims.begin(true);
        for (int k = 0; k < 3; k++) {
            Vec3d[] pts = new Vec3d[24];
            for (int j = 0; j < 24; j++) {
                double f = j / 23.0;
                double ang = f * 7 + t * 0.4 + k * 2.09;
                double rad = 0.9 * (1 - 0.3 * f);
                pts[j] = b.add(Math.cos(ang) * rad, f * h * 1.3, Math.sin(ang) * rad);
            }
            Prims.ribbon(pts, 0.22, i.r, i.g, i.b, 0.9f * a, 2);
            Prims.ribbon(pts, 0.07, 1, 1, 1, 0.9f * a, 2);
        }
        for (int k = 0; k < 2; k++) {
            double f = (u * 1.5 + k * 0.5) % 1.0;
            Prims.ring(b.add(0, f * h * 1.2, 0), UP, 0.9, 1.1, 32, i.r, i.g, i.b, 0.7f * a, 0.0f);
        }
        Prims.sphere(b.add(0, h / 2, 0), h * 0.7, i.r, i.g, i.b, 0f, 0.3f * a);
        Prims.end();
    }

    // ---------------------------------------------------------------- AURA (attached flames)
    private static void aura(Inst i, double t, ClientWorld w, float pt) {
        Entity e = w == null ? null : w.getEntityById(i.style);
        if (e == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean self = e == mc.cameraEntity && mc.options.getPerspective().isFirstPerson();
        Vec3d b = e.getLerpedPos(pt);
        double h = e.getHeight();
        double fadeIn = ease(t / 6.0), fadeOut = ease((i.dur - t) / 6.0);
        float a = (float) (fadeIn * fadeOut);
        if (a <= 0.01f) return;
        double inten = i.scale;
        Random rnd = new Random(i.seed + (long) (t / 3));
        Prims.begin(true);
        if (!self) Prims.sphere(b.add(0, h / 2, 0), h * 0.68, i.r, i.g, i.b, 0f, 0.28f * a);
        for (int k = 0; k < 9; k++) {
            double a0 = k * Math.PI * 2 / 9 + t * 0.12;
            Vec3d[] pts = new Vec3d[8];
            for (int j = 0; j < 8; j++) {
                double f = j / 7.0;
                double rad = 0.55 * (1 - 0.5 * f) + Math.sin(t * 0.3 + k + f * 4) * 0.12;
                double ang = a0 + f * 1.2;
                pts[j] = b.add(Math.cos(ang) * rad, f * h * 1.25 * inten, Math.sin(ang) * rad);
            }
            if (!self) {
                Prims.ribbon(pts, 0.35 * inten, i.r, i.g, i.b, 0.7f * a, 2);
                Prims.ribbon(pts, 0.1, 1, 1, 1, 0.6f * a, 2);
            }
        }
        double pulse = 0.5 + 0.5 * Math.sin(t * 0.4);
        Prims.ring(b.add(0, 0.05, 0), UP, 0.5, 0.9 + 0.3 * pulse, 32, i.r, i.g, i.b, 0.6f * a, 0f);
        if (inten > 1.3) {
            for (int k = 0; k < 3; k++) {
                Vec3d from = b.add(0, h * 0.5, 0);
                Vec3d to = from.add(randDir(rnd).multiply(1.6 + rnd.nextDouble()));
                Vec3d[] bolt = jag(rnd, from, to, 7, 0.25);
                Prims.ribbon(bolt, 0.18, 1f, 0.2f, 0.25f, a, 2);
                Prims.ribbon(bolt, 0.05, 1, 1, 1, a, 2);
            }
        }
        Prims.end();
    }

    // ---------------------------------------------------------------- CHARGE (Hollow Purple wind-up)
    private static void charge(Inst i, double t) {
        double u = clamp(t / i.dur);
        Vec3d d = i.dir.normalize();
        Vec3d right = d.crossProduct(UP);
        right = right.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : right.normalize();
        double sep = 2.2 * (1 - ease(u * 1.1));
        double r0 = 0.5 + 0.3 * u;
        Vec3d bc = i.pos.add(right.multiply(-sep)), rc = i.pos.add(right.multiply(sep));
        Prims.begin(true);
        Prims.sphere(bc, r0, 0.25f, 0.5f, 1f, 0.8f, 1f);
        Prims.sphere(bc, r0 * 2, 0.25f, 0.5f, 1f, 0f, 0.35f);
        Prims.sphere(rc, r0, 1f, 0.2f, 0.25f, 0.8f, 1f);
        Prims.sphere(rc, r0 * 2, 1f, 0.2f, 0.25f, 0f, 0.35f);
        double mr = 0.2 + 2.2 * Math.pow(u, 2.5);
        Prims.sphere(i.pos, mr * 0.55, 1, 1, 1, 1f, 1f);
        Prims.sphere(i.pos, mr, i.r, i.g, i.b, 0.8f, 1f);
        Prims.sphere(i.pos, mr * 1.8, i.r, i.g, i.b, 0f, 0.35f);
        Prims.sphere(i.pos, mr * 3, i.r, i.g, i.b, 0f, 0.1f);
        for (int k = 0; k < 6; k++) {
            double a0 = k * Math.PI / 3 + t * 0.4;
            Vec3d[] pts = new Vec3d[14];
            Vec3d[] bs = Prims.basis(d);
            for (int j = 0; j < 14; j++) {
                double f = j / 13.0;
                double rad = lerp(4.0, mr, f);
                double ang = a0 + f * 3;
                pts[j] = i.pos.add(bs[0].multiply(Math.cos(ang) * rad)).add(bs[1].multiply(Math.sin(ang) * rad)).add(d.multiply(-(1 - f) * 2));
            }
            Prims.ribbon(pts, 0.18, i.r, i.g, i.b, 0.8f * (float) u + 0.2f, 1);
        }
        Prims.end();
    }

    // ---------------------------------------------------------------- BOLT (Red / Flame projectile)
    private static void bolt(Inst i, double t) {
        Vec3d d = i.dir.normalize();
        double head = i.scale * clamp(t / i.dur);
        Vec3d h = i.pos.add(d.multiply(head));
        Vec3d tail = i.pos.add(d.multiply(Math.max(0, head - 14)));
        Random rnd = new Random(i.seed + (long) t);
        Prims.begin(true);
        Prims.ribbon(new Vec3d[]{tail, h}, 0.9, i.r, i.g, i.b, 0.8f, 1);
        Prims.ribbon(new Vec3d[]{tail, h}, 0.3, 1, 1, 1, 0.9f, 1);
        Vec3d[] bs = Prims.basis(d);
        int n = 16;
        Vec3d[] sp = new Vec3d[n];
        double len = h.subtract(tail).length();
        for (int k = 0; k < n; k++) {
            double f = k / (n - 1.0);
            double ang = f * 14 + t * 1.5;
            double rad = 0.55 * f + (i.style == 1 ? rnd.nextDouble() * 0.15 : 0);
            sp[k] = tail.add(d.multiply(len * f)).add(bs[0].multiply(Math.cos(ang) * rad)).add(bs[1].multiply(Math.sin(ang) * rad));
        }
        Prims.ribbon(sp, 0.22, i.r, i.g, i.b, 0.9f, 1);
        Prims.sphere(h, 0.9, i.r, i.g, i.b, 0.9f, 1f);
        Prims.sphere(h, 0.5, 1, 1, 1, 1f, 1f);
        Prims.sphere(h, 1.9, i.r, i.g, i.b, 0f, 0.3f);
        Prims.end();
    }

    // ---------------------------------------------------------------- POOL (Shadow Pool)
    private static void pool(Inst i, double t) {
        double R = i.scale * ease(t / 10.0) * ease((i.dur - t) / 10.0);
        if (R < 0.1) return;
        Vec3d c = i.pos.add(0, 0.04, 0);
        Prims.begin(false);
        Prims.ring(c, UP, 0, R, 48, 0f, 0f, 0.03f, 0.92f, 0.92f);
        Prims.end();
        Prims.begin(true);
        for (int k = 0; k < 3; k++) {
            double f = (t * 0.02 + k / 3.0) % 1.0;
            Prims.ring(c.add(0, 0.02, 0), UP, Math.max(0, R * f - 0.3), R * f, 40, i.r, i.g, i.b, 0f, (float) (1 - f) * 0.7f);
        }
        Prims.ring(c.add(0, 0.02, 0), UP, R * 0.9, R, 48, i.r, i.g, i.b, 0f, 0.7f);
        Random rnd = new Random(i.seed);
        for (int k = 0; k < 14; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2, rad = Math.sqrt(rnd.nextDouble()) * R * 0.9;
            Vec3d b0 = c.add(Math.cos(ang) * rad, 0, Math.sin(ang) * rad);
            double hgt = 1.2 + rnd.nextDouble() * 2.2;
            Vec3d[] pts = new Vec3d[8];
            for (int j = 0; j < 8; j++) {
                double f = j / 7.0;
                pts[j] = b0.add(Math.sin(t * 0.12 + k + f * 3) * 0.4 * f, f * hgt * (0.6 + 0.4 * Math.sin(t * 0.2 + k)), Math.cos(t * 0.1 + k + f * 2) * 0.4 * f);
            }
            Prims.ribbon(pts, 0.35, i.r, i.g, i.b, 0.55f, 2);
        }
        Prims.end();
    }

    private VfxRenderer() {}
}
