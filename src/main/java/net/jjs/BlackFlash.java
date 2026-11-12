package net.jjs;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Melee hits can land a Black Flash. Land one and you enter the zone. */
public final class BlackFlash {

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld w)) return ActionResult.PASS;
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) return ActionResult.PASS;
            Sorcerer s = PlayerState.sorcerer(sp);
            if (s == null || sp.getAttackCooldownProgress(0.5f) < 0.9f) return ActionResult.PASS;

            PlayerState.Data d = PlayerState.data(sp);
            long now = w.getTime();
            boolean zone = d.zoneUntil > now;
            double chance = s == Sorcerer.BRAWLER ? 0.14 : 0.07;
            if (zone) chance = 0.35;
            if (d.overdriveUntil > now) chance = 0.6;
            if (w.random.nextDouble() > chance) return ActionResult.PASS;

            strike(w, sp, target, zone ? 22f : 15f);
            return ActionResult.PASS;
        });
    }

    public static void strike(ServerWorld w, ServerPlayerEntity sp, LivingEntity target, float dmg) {
        PlayerState.Data d = PlayerState.data(sp);
        long now = w.getTime();
        boolean chain = d.zoneUntil > now;

        target.timeUntilRegen = 0;
        target.damage(w.getDamageSources().playerAttack(sp), dmg);
        target.timeUntilRegen = 0; // the normal hit still lands

        Vec3d m = target.getPos().add(0, target.getHeight() / 2, 0);
        Vfx.spawn(w, Vfx.LIGHTNING, m, Vec3d.ZERO, 12, 0xff1030, 3.4f, 0);
        Vfx.spawn(w, Vfx.SHOCK, m, Vec3d.ZERO, 12, 0xff2040, 7f, 0);
        Vfx.screen(w, m, 26, Vfx.SCREEN_BLACKFLASH, 9, 1f, 0xff1030);
        Vfx.screen(w, m, 26, Vfx.SCREEN_SHAKE, 12, 1f, 0);
        w.playSound(null, m.x, m.y, m.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.6f, 1.7f);
        w.playSound(null, m.x, m.y, m.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6f, 1.2f);

        d.zoneUntil = now + 200;
        sp.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 2, false, true));
        PlayerState.add(sp, 30f);
        sp.sendMessage(Text.literal(chain ? "\u00a74\u00a7lBLACK FLASH x CHAIN" : "\u00a7c\u00a7lBLACK FLASH"), true);
    }

    private BlackFlash() {}
}
