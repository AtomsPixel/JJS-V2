package net.jjs.client;

import net.jjs.Ability;
import net.jjs.PlayerState;
import net.jjs.Sorcerer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public final class Hud {
    public static Identifier icon(String id) { return new Identifier("jjs", "textures/gui/icon_" + id + ".png"); }
    public static Identifier emblem(String id) { return new Identifier("jjs", "textures/gui/emblem_" + id + ".png"); }
    public static int argb(int a, int rgb) { return ScreenFx.argb(a, rgb); }

    public static void render(DrawContext ctx, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ScreenFx.overlay(ctx, td);
        if (mc.options.hudHidden || mc.player == null) return;
        if (ClientState.character < 0 || ClientState.character >= Sorcerer.values().length) return;
        Sorcerer s = Sorcerer.values()[ClientState.character];
        TextRenderer tr = mc.textRenderer;
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        double time = System.currentTimeMillis() / 1000.0;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 4);

        int slot = 32, gap = 4;
        int total = 4 * slot + 3 * gap;
        int x0 = w - total - 12, y0 = h - slot - 12;

        // ---- cursed energy bar
        int bx = x0, by = y0 - 14, bw = total, bh = 7;
        float frac = Math.max(0, Math.min(1, ClientState.ce / PlayerState.MAX_CE));
        int accent = ClientState.reinforced ? 0xff60d0 : s.color;
        ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, argb(220, 0x000000));
        ctx.fill(bx, by, bx + bw, by + bh, argb(235, 0x08081a));
        int fw = (int) (bw * frac);
        if (fw > 0) {
            ctx.fillGradient(bx, by, bx + fw, by + bh, argb(255, lighten(accent, 0.45f)), argb(255, darken(accent, 0.55f)));
            ctx.fill(bx, by, bx + fw, by + 1, argb(200, 0xffffff));
        }
        for (int i = 1; i < 8; i++) ctx.fill(bx + bw * i / 8, by, bx + bw * i / 8 + 1, by + bh, argb(110, 0x000000));
        if (ClientState.reinforced) {
            int ga = (int) (90 + 120 * pulse);
            ctx.fill(bx - 2, by - 2, bx + bw + 2, by - 1, argb(ga, accent));
            ctx.fill(bx - 2, by + bh + 1, bx + bw + 2, by + bh + 2, argb(ga, accent));
        }
        String ceText = (int) ClientState.ce + " / " + (int) PlayerState.MAX_CE;
        ctx.drawTextWithShadow(tr, "CE", bx, by - 10, argb(255, 0x9fd0ff));
        ctx.drawTextWithShadow(tr, ceText, bx + bw - tr.getWidth(ceText), by - 10, 0xFFFFFFFF);
        String title = s.title.toUpperCase();
        ctx.drawTextWithShadow(tr, title, bx + 14, by - 10, argb(255, s.color));

        // ---- zone / black flash timer
        if (ClientState.zone > 0) {
            float zf = Math.min(1f, ClientState.zone / 200f);
            int zy = by - 20;
            ctx.fill(bx, zy, bx + bw, zy + 3, argb(200, 0x000000));
            ctx.fill(bx, zy, bx + (int) (bw * zf), zy + 3, argb(255, 0xff1030));
            ctx.drawTextWithShadow(tr, "THE ZONE", bx, zy - 9, argb((int) (170 + 85 * pulse), 0xff3050));
        }

        // ---- 4 technique slots
        for (int i = 0; i < 4; i++) {
            slot(ctx, tr, x0 + i * (slot + gap), y0, slot, s.abilities[i], i, pulse, mc);
        }
        // ---- RCT + reinforcement (small)
        int sm = 22;
        slot(ctx, tr, x0 - (sm + gap) * 2 - 2, y0 + (slot - sm), sm, Sorcerer.RCT, 4, pulse, mc);
        slot(ctx, tr, x0 - (sm + gap) - 2, y0 + (slot - sm), sm, Sorcerer.REINFORCE, 5, pulse, mc);
    }

    private static void slot(DrawContext ctx, TextRenderer tr, int x, int y, int size, Ability a, int idx, float pulse, MinecraftClient mc) {
        int rem = ClientState.cd[idx];
        boolean ready = rem <= 0;
        boolean poor = ClientState.ce < a.cost() && !(mc.player != null && mc.player.isCreative());
        boolean toggled = idx == 5 && ClientState.reinforced;

        int border = a.color();
        int ba = ready && !poor ? (int) (160 + 95 * pulse) : 110;
        if (toggled) ba = 255;
        ctx.fill(x - 2, y - 2, x + size + 2, y + size + 2, argb(ba, border));
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, argb(250, 0x05050f));
        ctx.fillGradient(x, y, x + size, y + size, argb(255, darken(a.color(), 0.18f)), argb(255, 0x07070f));
        ctx.drawTexture(icon(a.icon()), x, y, size, size, 0f, 0f, 64, 64, 64, 64);

        if (poor) ctx.fill(x, y, x + size, y + size, argb(110, 0x300000));
        if (!ready) {
            float f = Math.min(1f, rem / (float) Math.max(1, a.cooldown()));
            ctx.fill(x, y, x + size, y + (int) (size * f), argb(185, 0x000000));
            String t = rem >= 200 ? String.valueOf(rem / 20) : String.format("%.1f", rem / 20f);
            ctx.drawTextWithShadow(tr, t, x + (size - tr.getWidth(t)) / 2, y + size / 2 - 4, 0xFFFFFFFF);
        }
        String key = JJSClient.keyName(idx);
        small(ctx, tr, key, x + 2, y + 1, 0xFFFFFF, 0.8f);
        if (a.cost() > 0 && size >= 30) small(ctx, tr, String.valueOf(a.cost()), x + size - 2 - (int) (tr.getWidth(String.valueOf(a.cost())) * 0.7f), y + size - 7, 0x80c8ff, 0.7f);
    }

    public static void small(DrawContext ctx, TextRenderer tr, String t, int x, int y, int rgb, float sc) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(sc, sc, 1f);
        ctx.drawTextWithShadow(tr, t, 0, 0, argb(255, rgb));
        ctx.getMatrices().pop();
    }

    public static int darken(int rgb, float k) {
        int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }
    public static int lighten(int rgb, float k) {
        int r = ((rgb >> 16) & 255), g = ((rgb >> 8) & 255), b = (rgb & 255);
        r += (int) ((255 - r) * k); g += (int) ((255 - g) * k); b += (int) ((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }
    private Hud() {}
}
