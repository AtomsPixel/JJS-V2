package net.jjs.client;

import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jjs.Ability;
import net.jjs.Net;
import net.jjs.Sorcerer;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;

public class CharacterSelectScreen extends Screen {
    private final float[] hover = new float[4];
    private int cardW, cardH, gap, x0, y0;

    public CharacterSelectScreen() { super(Text.literal("Choose your sorcerer")); }

    @Override public boolean shouldPause() { return false; }

    private void layout() {
        gap = 10;
        cardW = Math.max(90, Math.min(124, (width - 40 - gap * 3) / 4));
        cardH = Math.min(214, height - 80);
        int total = cardW * 4 + gap * 3;
        x0 = (width - total) / 2;
        y0 = Math.max(34, (height - cardH) / 2 - 8);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        layout();
        TextRenderer tr = this.textRenderer;
        long now = System.currentTimeMillis();
        double time = now / 1000.0;

        ctx.fillGradient(0, 0, width, height, 0xF2030008, 0xF2140028);
        for (int i = 0; i < 70; i++) {
            double sp = 0.02 + (i % 7) * 0.008;
            double px = ((i * 977.0) % width + Math.sin(time * 0.5 + i) * 18) % width;
            double py = height - ((time * sp * height * 6 + i * 131.0) % height);
            int a = (int) (40 + 60 * (0.5 + 0.5 * Math.sin(time * 2 + i)));
            int col = i % 3 == 0 ? 0x4aa8ff : (i % 3 == 1 ? 0xb060ff : 0xff3a5a);
            ctx.fill((int) px, (int) py, (int) px + 2, (int) py + 2, ScreenFx.argb(a, col));
        }

        // title
        String t = "CHOOSE YOUR SORCERER";
        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, 10, 0);
        ctx.getMatrices().scale(2f, 2f, 1f);
        int tw = tr.getWidth(t);
        ctx.drawTextWithShadow(tr, t, -tw / 2, 0, 0xFFFFFFFF);
        ctx.getMatrices().pop();
        String sub = "Pick an archetype. You can change it any time with the menu key.";
        ctx.drawCenteredTextWithShadow(tr, sub, width / 2, 28, 0xFF8890b0);

        Ability hoveredAb = null;
        Sorcerer hoveredAbOwner = null;
        for (int i = 0; i < 4; i++) {
            Sorcerer s = Sorcerer.values()[i];
            int x = x0 + i * (cardW + gap);
            boolean over = mx >= x && mx < x + cardW && my >= y0 && my < y0 + cardH;
            hover[i] += ((over ? 1f : 0f) - hover[i]) * 0.25f;
            int y = y0 - (int) (7 * hover[i]);
            boolean selected = i == ClientState.character;

            // glow border
            int glow = (int) (50 + 120 * hover[i] + (selected ? 60 : 0));
            for (int g = 4; g >= 1; g--) ctx.fill(x - g, y - g, x + cardW + g, y + cardH + g, ScreenFx.argb(glow / (g + 1), s.color));
            ctx.fill(x - 1, y - 1, x + cardW + 1, y + cardH + 1, ScreenFx.argb(255, s.color));
            ctx.fillGradient(x, y, x + cardW, y + cardH, ScreenFx.argb(250, Hud.darken(s.color, 0.32f)), 0xFA090912);

            // emblem
            int es = Math.min(64, cardW - 24);
            ctx.drawTexture(Hud.emblem(s.id), x + (cardW - es) / 2, y + 8, es, es, 0f, 0f, 128, 128, 128, 128);

            // name + tagline
            String nm = s.title.toUpperCase();
            List<OrderedText> nameLines = tr.wrapLines(StringVisitable.plain(nm), cardW - 8);
            int ny = y + 8 + es + 4;
            for (OrderedText l : nameLines) { ctx.drawCenteredTextWithShadow(tr, l, x + cardW / 2, ny, ScreenFx.argb(255, Hud.lighten(s.color, 0.35f))); ny += 10; }
            List<OrderedText> lines = tr.wrapLines(StringVisitable.plain(s.tagline), cardW - 12);
            int ty = ny + 2;
            for (int k = 0; k < Math.min(4, lines.size()); k++) { ctx.drawText(tr, lines.get(k), x + 6, ty, 0xFFA0A8C0, false); ty += 9; }

            // ability rows
            int rowH = 20;
            int ry = y + cardH - rowH * 4 - 8;
            for (int k = 0; k < 4; k++) {
                Ability a = s.abilities[k];
                int rx = x + 5, rw = cardW - 10;
                boolean rowOver = mx >= rx && mx < rx + rw && my >= ry && my < ry + rowH - 1;
                ctx.fill(rx, ry, rx + rw, ry + rowH - 1, ScreenFx.argb(rowOver ? 200 : 110, rowOver ? Hud.darken(a.color(), 0.45f) : 0x000000));
                ctx.drawTexture(Hud.icon(a.icon()), rx + 1, ry + 1, 17, 17, 0f, 0f, 64, 64, 64, 64);
                String an = a.name();
                int maxw = rw - 24;
                if (tr.getWidth(an) > maxw) an = tr.trimToWidth(an, maxw - 4) + "..";
                ctx.drawTextWithShadow(tr, an, rx + 21, ry + 6, ScreenFx.argb(255, k == 3 ? 0xffd9ff : 0xffffff));
                if (rowOver) { hoveredAb = a; hoveredAbOwner = s; }
                ry += rowH;
            }

            if (selected) {
                String b = "SELECTED";
                ctx.fill(x + 4, y + 4, x + 8 + tr.getWidth(b), y + 15, ScreenFx.argb(230, s.color));
                ctx.drawTextWithShadow(tr, b, x + 6, y + 6, 0xFFFFFFFF);
            }
        }

        // description panel
        int py = y0 + cardH + 12;
        int pw = cardW * 4 + gap * 3;
        ctx.fill(x0, py, x0 + pw, py + 40, 0xCC000000);
        if (hoveredAb != null) {
            ctx.fill(x0, py, x0 + 2, py + 40, ScreenFx.argb(255, hoveredAb.color()));
            ctx.drawTextWithShadow(tr, hoveredAb.name(), x0 + 8, py + 4, ScreenFx.argb(255, hoveredAb.color()));
            String stats = "Cost " + hoveredAb.cost() + " CE   Cooldown " + (hoveredAb.cooldown() / 20) + "s";
            ctx.drawTextWithShadow(tr, stats, x0 + pw - 8 - tr.getWidth(stats), py + 4, 0xFF80c8ff);
            List<OrderedText> d = tr.wrapLines(StringVisitable.plain(hoveredAb.desc()), pw - 16);
            for (int k = 0; k < Math.min(2, d.size()); k++) ctx.drawText(tr, d.get(k), x0 + 8, py + 17 + k * 10, 0xFFD0D4E8, false);
        } else {
            ctx.drawCenteredTextWithShadow(tr, "Hover an ability for details  |  Click a card to choose", width / 2, py + 15, 0xFF8890b0);
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        for (int i = 0; i < 4; i++) {
            int x = x0 + i * (cardW + gap);
            if (mx >= x && mx < x + cardW && my >= y0 - 8 && my < y0 + cardH) {
                PacketByteBuf b = PacketByteBufs.create();
                b.writeInt(i);
                ClientPlayNetworking.send(Net.SELECT, b);
                ClientState.character = i;
                this.close();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
