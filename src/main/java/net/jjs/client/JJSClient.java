package net.jjs.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jjs.Net;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class JJSClient implements ClientModInitializer {
    public static final KeyBinding[] ABIL = new KeyBinding[6];
    public static KeyBinding MENU;

    public static String keyName(int idx) {
        return ABIL[idx].getBoundKeyLocalizedText().getString().toUpperCase();
    }

    @Override
    public void onInitializeClient() {
        int[] keys = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_G};
        String[] names = {"ability1", "ability2", "ability3", "ability4", "rct", "reinforce"};
        for (int i = 0; i < 6; i++)
            ABIL[i] = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.jjs." + names[i], InputUtil.Type.KEYSYM, keys[i], "category.jjs"));
        MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.jjs.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.jjs"));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) return;
            for (int i = 0; i < 6; i++) {
                while (ABIL[i].wasPressed()) {
                    if (mc.currentScreen == null && ClientState.character >= 0) {
                        PacketByteBuf b = PacketByteBufs.create();
                        b.writeInt(i);
                        ClientPlayNetworking.send(Net.CAST, b);
                    }
                }
            }
            while (MENU.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new CharacterSelectScreen());
            ClientState.tick();
            VfxRenderer.tick();
            ScreenFx.tick(mc);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
            VfxRenderer.clear();
            ClientState.character = -1;
            ClientState.prompted = false;
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.STATE, (mc, handler, buf, sender) -> {
            int ch = buf.readInt();
            float ce = buf.readFloat();
            boolean re = buf.readBoolean();
            int zone = buf.readInt();
            int[] cd = new int[6];
            for (int i = 0; i < 6; i++) cd[i] = buf.readInt();
            mc.execute(() -> {
                ClientState.character = ch;
                ClientState.ce = ce;
                ClientState.reinforced = re;
                ClientState.zone = zone;
                System.arraycopy(cd, 0, ClientState.cd, 0, 6);
                if (ch < 0 && !ClientState.prompted && mc.currentScreen == null) {
                    ClientState.prompted = true;
                    mc.setScreen(new CharacterSelectScreen());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.VFX, (mc, handler, buf, sender) -> {
            int type = buf.readInt();
            Vec3d pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3d dir = new Vec3d(buf.readFloat(), buf.readFloat(), buf.readFloat());
            int dur = buf.readInt(), color = buf.readInt();
            float scale = buf.readFloat();
            int style = buf.readInt();
            float extra = buf.readFloat();
            mc.execute(() -> VfxRenderer.add(type, pos, dir, dur, color, scale, style, extra));
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.SCREEN, (mc, handler, buf, sender) -> {
            int kind = buf.readInt(), dur = buf.readInt();
            float inten = buf.readFloat();
            int color = buf.readInt();
            mc.execute(() -> ScreenFx.add(kind, dur, inten, color));
        });

        HudRenderCallback.EVENT.register(Hud::render);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(VfxRenderer::render);
    }
}
