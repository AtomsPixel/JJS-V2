package net.jjs.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Minimal immediate-mode geometry toolkit for glowing VFX. All positions are world coordinates. */
public final class Prims {
    public static final Vec3d UP = new Vec3d(0, 1, 0);
    private static BufferBuilder bb;
    private static Matrix4f mat;
    private static double cx, cy, cz;
    private static Vec3d camRight = new Vec3d(1, 0, 0), camUp = new Vec3d(0, 1, 0);
    private static int count;

    public static void frame(MatrixStack ms, Camera cam) {
        mat = ms.peek().getPositionMatrix();
        Vec3d p = cam.getPos();
        cx = p.x; cy = p.y; cz = p.z;
        Vec3d f = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d r = f.crossProduct(UP);
        camRight = r.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : r.normalize();
        camUp = camRight.crossProduct(f).normalize();
    }

    public static void begin(boolean additive) {
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE);
        else RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        bb = Tessellator.getInstance().getBuffer();
        bb.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        count = 0;
    }

    public static void end() {
        if (count == 0) for (int i = 0; i < 3; i++) bb.vertex(mat, 0, 0, 0).color(0f, 0f, 0f, 0f).next();
        Tessellator.getInstance().draw();
    }

    public static void recover() {
        try { if (bb != null && bb.isBuilding()) bb.end(); } catch (Exception ignored) {}
    }

    public static void finish() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static float cl(float a) { return a < 0 ? 0 : Math.min(a, 1f); }

    public static void v(double x, double y, double z, float r, float g, float b, float a) {
        bb.vertex(mat, (float) (x - cx), (float) (y - cy), (float) (z - cz)).color(r, g, b, cl(a)).next();
        count++;
    }

    private static void v(Vec3d p, float r, float g, float b, float a) { v(p.x, p.y, p.z, r, g, b, a); }

    public static Vec3d[] basis(Vec3d n) {
        n = n.normalize();
        Vec3d t = Math.abs(n.y) < 0.99 ? UP : new Vec3d(1, 0, 0);
        Vec3d u = n.crossProduct(t).normalize();
        Vec3d v = n.crossProduct(u).normalize();
        return new Vec3d[]{u, v};
    }

    private static float fres(Vec3d p, Vec3d n, float aC, float aR) {
        double rx = p.x - cx, ry = p.y - cy, rz = p.z - cz;
        double len = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (len < 1e-6) return aC;
        double facing = Math.abs((n.x * rx + n.y * ry + n.z * rz) / len);
        return (float) (aC + (aR - aC) * Math.pow(1 - facing, 1.5));
    }

    // ------------------------------------------------------------ sphere (fresnel alpha)
    public static void sphere(Vec3d c, double R, float r, float g, float b, float aC, float aR) {
        sphere(c, R, r, g, b, aC, aR, 12, 20);
    }

    public static void sphere(Vec3d c, double R, float r, float g, float b, float aC, float aR, int S, int L) {
        Vec3d[][] pt = new Vec3d[S + 1][L + 1];
        Vec3d[][] nm = new Vec3d[S + 1][L + 1];
        for (int i = 0; i <= S; i++) {
            double th = Math.PI * i / S;
            for (int j = 0; j <= L; j++) {
                double ph = 2 * Math.PI * j / L;
                Vec3d n = new Vec3d(Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph));
                nm[i][j] = n;
                pt[i][j] = c.add(n.multiply(R));
            }
        }
        for (int i = 0; i < S; i++)
            for (int j = 0; j < L; j++) {
                float a00 = fres(pt[i][j], nm[i][j], aC, aR), a10 = fres(pt[i + 1][j], nm[i + 1][j], aC, aR);
                float a11 = fres(pt[i + 1][j + 1], nm[i + 1][j + 1], aC, aR), a01 = fres(pt[i][j + 1], nm[i][j + 1], aC, aR);
                v(pt[i][j], r, g, b, a00); v(pt[i + 1][j], r, g, b, a10); v(pt[i + 1][j + 1], r, g, b, a11);
                v(pt[i][j], r, g, b, a00); v(pt[i + 1][j + 1], r, g, b, a11); v(pt[i][j + 1], r, g, b, a01);
            }
    }

    // ------------------------------------------------------------ flat ring / disc
    public static void ring(Vec3d c, Vec3d n, double r0, double r1, int seg, float r, float g, float b, float a0, float a1) {
        Vec3d[] bs = basis(n);
        for (int i = 0; i < seg; i++) {
            double A0 = 2 * Math.PI * i / seg, A1 = 2 * Math.PI * (i + 1) / seg;
            Vec3d d0 = bs[0].multiply(Math.cos(A0)).add(bs[1].multiply(Math.sin(A0)));
            Vec3d d1 = bs[0].multiply(Math.cos(A1)).add(bs[1].multiply(Math.sin(A1)));
            Vec3d i0 = c.add(d0.multiply(r0)), o0 = c.add(d0.multiply(r1));
            Vec3d i1 = c.add(d1.multiply(r0)), o1 = c.add(d1.multiply(r1));
            v(i0, r, g, b, a0); v(o0, r, g, b, a1); v(o1, r, g, b, a1);
            v(i0, r, g, b, a0); v(o1, r, g, b, a1); v(i1, r, g, b, a0);
        }
    }

    // ------------------------------------------------------------ tube (open cylinder, fresnel alpha)
    public static void tube(Vec3d a, Vec3d b, double rad, int seg, float r, float g, float bl, float aC, float aR) {
        Vec3d axis = b.subtract(a);
        if (axis.length() < 1e-4) return;
        Vec3d[] bs = basis(axis);
        for (int i = 0; i < seg; i++) {
            double A0 = 2 * Math.PI * i / seg, A1 = 2 * Math.PI * (i + 1) / seg;
            Vec3d d0 = bs[0].multiply(Math.cos(A0)).add(bs[1].multiply(Math.sin(A0)));
            Vec3d d1 = bs[0].multiply(Math.cos(A1)).add(bs[1].multiply(Math.sin(A1)));
            Vec3d p00 = a.add(d0.multiply(rad)), p01 = b.add(d0.multiply(rad));
            Vec3d p10 = a.add(d1.multiply(rad)), p11 = b.add(d1.multiply(rad));
            float f00 = fres(p00, d0, aC, aR), f01 = fres(p01, d0, aC, aR);
            float f10 = fres(p10, d1, aC, aR), f11 = fres(p11, d1, aC, aR);
            v(p00, r, g, bl, f00); v(p01, r, g, bl, f01); v(p11, r, g, bl, f11);
            v(p00, r, g, bl, f00); v(p11, r, g, bl, f11); v(p10, r, g, bl, f10);
        }
    }

    // ------------------------------------------------------------ camera-facing ribbon
    /** mode 0 flat, 1 fade tail (bright at end), 2 taper both ends, 3 fade head (bright at start). */
    public static void ribbon(Vec3d[] pts, double width, float r, float g, float b, float a, int mode) {
        int n = pts.length;
        if (n < 2) return;
        Vec3d[] L = new Vec3d[n], R = new Vec3d[n];
        float[] al = new float[n];
        for (int i = 0; i < n; i++) {
            double u = i / (double) (n - 1);
            Vec3d tan = pts[Math.min(i + 1, n - 1)].subtract(pts[Math.max(i - 1, 0)]);
            Vec3d toCam = new Vec3d(cx - pts[i].x, cy - pts[i].y, cz - pts[i].z);
            Vec3d side = tan.crossProduct(toCam);
            side = side.lengthSquared() < 1e-9 ? camRight : side.normalize();
            double wf = 1;
            float af = 1;
            switch (mode) {
                case 1 -> { wf = 0.25 + 0.75 * u; af = (float) u; }
                case 2 -> { double s = Math.sin(Math.PI * u); wf = 0.1 + 0.9 * s; af = (float) s; }
                case 3 -> { wf = 1 - 0.7 * u; af = (float) (1 - u); }
                default -> {}
            }
            Vec3d off = side.multiply(width * 0.5 * wf);
            L[i] = pts[i].add(off);
            R[i] = pts[i].subtract(off);
            al[i] = a * af;
        }
        for (int i = 0; i < n - 1; i++) {
            v(L[i], r, g, b, al[i]); v(R[i], r, g, b, al[i]); v(R[i + 1], r, g, b, al[i + 1]);
            v(L[i], r, g, b, al[i]); v(R[i + 1], r, g, b, al[i + 1]); v(L[i + 1], r, g, b, al[i + 1]);
        }
    }

    // ------------------------------------------------------------ cone
    public static void cone(Vec3d base, Vec3d axis, double rad, double h, int seg, float r, float g, float b, float aBase, float aTip) {
        if (h < 0.01) return;
        Vec3d[] bs = basis(axis);
        Vec3d tip = base.add(axis.normalize().multiply(h));
        for (int i = 0; i < seg; i++) {
            double A0 = 2 * Math.PI * i / seg, A1 = 2 * Math.PI * (i + 1) / seg;
            Vec3d p0 = base.add(bs[0].multiply(Math.cos(A0) * rad)).add(bs[1].multiply(Math.sin(A0) * rad));
            Vec3d p1 = base.add(bs[0].multiply(Math.cos(A1) * rad)).add(bs[1].multiply(Math.sin(A1) * rad));
            v(p0, r, g, b, aBase); v(p1, r, g, b, aBase); v(tip, r, g, b, aTip);
        }
    }

    // ------------------------------------------------------------ sparkle (camera-facing diamond)
    public static void star(Vec3d p, double size, float r, float g, float b, float a) {
        Vec3d rr = camRight.multiply(size), uu = camUp.multiply(size);
        Vec3d A = p.add(rr), B = p.add(uu), C = p.subtract(rr), D = p.subtract(uu);
        v(A, r, g, b, a); v(B, r, g, b, a); v(C, r, g, b, a);
        v(A, r, g, b, a); v(C, r, g, b, a); v(D, r, g, b, a);
    }

    private Prims() {}
}
