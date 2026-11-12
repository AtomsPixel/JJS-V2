package net.jjs;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class JJSMod implements ModInitializer {
    public static final String MOD_ID = "jjs";

    @Override
    public void onInitialize() {
        BlackFlash.register();

        ServerPlayNetworking.registerGlobalReceiver(Net.SELECT, (server, player, handler, buf, sender) -> {
            int idx = buf.readInt();
            server.execute(() -> {
                if (idx < 0 || idx >= Sorcerer.values().length) return;
                PlayerState.setCharacter(player, idx);
                PlayerState.Data d = PlayerState.data(player);
                d.reinforced = false;
                long now = player.getServerWorld().getTime();
                for (int i = 0; i < 6; i++) d.readyAt[i] = now + 40; // anti-swap abuse
                PlayerState.sync(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(Net.CAST, (server, player, handler, buf, sender) -> {
            int slot = buf.readInt();
            server.execute(() -> PlayerState.cast(player, slot));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PlayerState.sync(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            PlayerState.reset(newP);
            PlayerState.sync(newP);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Effects.tick();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) PlayerState.tick(p);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { Effects.clear(); PlayerState.clearAll(); });
    }
}
