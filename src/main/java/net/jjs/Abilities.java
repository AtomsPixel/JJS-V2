package net.jjs;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public final class Abilities {
    private Abilities() {}

    // =================================================================== helpers
    static boolean valid(Entity e) { return e.isAlive() && !(e instanceof ArmorStandEntity); }
    static Vec3d mid(Entity e) { return e.getPos().add(0, e.getHeight() / 2, 0); }

    static void launch(Entity e, Vec3d v) {
        e.setVelocity(v);
        e.velocityModified = true;
        e.fallDistance = 0;
        if (e instanceof ServerPlayerEntity sp) sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
    }

    static void hurt(ServerWorld w, ServerPlayerEntity p, LivingEntity e, float dmg) {
        e.timeUntilRegen = 0;
        e.damage(w.getDamageSources().playerAttack(p), dmg);
    }

    static void sound(ServerWorld w, Vec3d at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.x, at.y, at.z, s, SoundCategory.PLAYERS, vol, pitch);
    }

    static void fx(LivingEntity e, StatusEffect t, int dur, int amp) {
        e.addStatusEffect(new StatusEffectInstance(t, dur, amp, false, false));
    }

    static int color(ServerPlayerEntity p) {
        Sorcerer s = PlayerState.sorcerer(p);
        return s == null ? 0x58b8ff : s.color;
    }

    /** First living entity along the crosshair (stops at walls). */
    static LivingEntity target(ServerWorld w, ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        for (double d = 0.8; d <= range; d += 0.5) {
            Vec3d pt = eye.add(look.multiply(d));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) return null;
            LivingEntity best = null;
            double bd = 1e9;
            for (Entity e : w.getOtherEntities(p, new Box(pt, pt).expand(2.0), en -> en instanceof LivingEntity && valid(en))) {
                if (e.getBoundingBox().expand(0.35).contains(pt)) {
                    double dd = e.squaredDistanceTo(pt);
                    if (dd < bd) { bd = dd; best = (LivingEntity) e; }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    static LivingEntity nearest(ServerWorld w, ServerPlayerEntity p, double radius) {
        LivingEntity best = null;
        double bd = 1e9;
        for (Entity e : w.getOtherEntities(p, p.getBoundingBox().expand(radius), en -> en instanceof LivingEntity && valid(en))) {
            double dd = e.squaredDistanceTo(p);
            if (dd < bd) { bd = dd; best = (LivingEntity) e; }
        }
        return best;
    }

    /** Where a projectile along the crosshair would hit (block or entity). */
    static Vec3d impact(ServerWorld w, ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        for (double d = 1.5; d <= range; d += 0.5) {
            Vec3d pt = eye.add(look.multiply(d));
            BlockPos bp = BlockPos.ofFloored(pt);
            boolean solid = !w.getBlockState(bp).getCollisionShape(w, bp).isEmpty();
            if (solid) return eye.add(look.multiply(d - 0.5));
            if (!w.getOtherEntities(p, new Box(pt, pt).expand(0.8), e -> e instanceof LivingEntity && valid(e)).isEmpty()) return pt;
        }
        return eye.add(look.multiply(range));
    }

    static double groundY(ServerWorld w, double x, double y, double z) {
        BlockPos.Mutable m = new BlockPos.Mutable(x, y + 1.5, z);
        for (int i = 0; i < 10; i++) {
            if (!w.getBlockState(m).getCollisionShape(w, m).isEmpty()) return m.getY() + 1;
            m.move(0, -1, 0);
        }
        return y;
    }

    static void carve(ServerWorld w, Vec3d c, int r) {
        BlockPos base = BlockPos.ofFloored(c);
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r + 1) continue;
                    BlockPos bp = base.add(dx, dy, dz);
                    BlockState st = w.getBlockState(bp);
                    if (st.isAir() || st.getHardness(w, bp) < 0) continue;
                    if (!w.getFluidState(bp).isEmpty()) w.setBlockState(bp, Blocks.AIR.getDefaultState(), 3);
                    else w.breakBlock(bp, false);
                }
    }

    // =================================================================== LIMITLESS
    public static boolean blue(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        Vec3d full = eye.add(look.multiply(9));
        BlockHitResult hr = w.raycast(new RaycastContext(eye, full, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        Vec3d c = hr.getType() == HitResult.Type.MISS ? full : hr.getPos().subtract(look.multiply(0.6));

        Vfx.spawn(w, Vfx.ORB, c, look, 80, 0x3d8bff, 1.5f, 0);
        Vfx.screen(w, c, 30, Vfx.SCREEN_SHAKE, 80, 0.3f, 0);
        sound(w, c, SoundEvents.BLOCK_BEACON_POWER_SELECT, 2f, 0.6f);

        Effects.run(80, age -> {
            if (age % 20 == 0) sound(w, c, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
            for (Entity e : w.getOtherEntities(p, new Box(c, c).expand(11), en -> (en instanceof LivingEntity || en instanceof ItemEntity) && valid(en))) {
                Vec3d d = c.subtract(mid(e));
                double dist = d.length();
                if (dist < 0.9) launch(e, e.getVelocity().multiply(0.3));
                else launch(e, d.normalize().multiply(Math.min(1.1, 0.35 + 1.5 / dist)));
                if (dist < 3 && age % 5 == 0 && e instanceof LivingEntity le) {
                    le.timeUntilRegen = 0;
                    le.damage(w.getDamageSources().magic(), 2.5f);
                }
            }
        });
        return true;
    }

    public static boolean red(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        Vec3d start = eye.add(look.multiply(1.2));
        Vec3d at = impact(w, p, 45);
        double dist = Math.max(2, start.distanceTo(at));
        int travel = Math.max(2, (int) (dist / 4.0));

        Vfx.spawn(w, Vfx.BOLT, start, look, travel, 0xff2a2a, (float) dist, 0);
        sound(w, start, SoundEvents.ENTITY_BLAZE_SHOOT, 2f, 0.5f);

        Effects.later(travel, () -> {
            w.createExplosion(p, at.x, at.y, at.z, 2.5f, World.ExplosionSourceType.NONE);
            Vfx.spawn(w, Vfx.SHOCK, at, look, 16, 0xff2a2a, 10f, 0);
            Vfx.spawn(w, Vfx.SHOCK, at, Vec3d.ZERO, 22, 0xffffff, 5f, 0);
            Vfx.screen(w, at, 40, Vfx.SCREEN_SHAKE, 20, 1f, 0);
            Vfx.screen(w, at, 14, Vfx.SCREEN_FLASH, 6, 0.5f, 0xff4040);
            for (Entity e : w.getOtherEntities(p, new Box(at, at).expand(7), en -> en instanceof LivingEntity && valid(en))) {
                Vec3d dir = mid(e).subtract(at);
                dir = dir.lengthSquared() < 1e-4 ? new Vec3d(0, 1, 0) : dir.normalize();
                launch(e, new Vec3d(dir.x * 2.8, 0.7 + dir.y * 1.2, dir.z * 2.8));
                hurt(w, p, (LivingEntity) e, 12f);
            }
        });
        return true;
    }

    public static boolean purple(ServerWorld w, ServerPlayerEntity p) {
        fx(p, StatusEffects.SLOWNESS, 26, 5);
        sound(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
        Vec3d eye0 = p.getEyePos(), look0 = p.getRotationVec(1f);
        Vfx.spawn(w, Vfx.CHARGE, eye0.add(look0.multiply(3)), look0, 22, 0xa020ff, 1f, 0);

        Vec3d[] dir = new Vec3d[1], origin = new Vec3d[1];
        Set<UUID> hit = new HashSet<>();
        Effects.run(36, age -> {
            Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
            if (age < 22) {
                if (age % 6 == 0) sound(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, 2f, 0.5f + age * 0.05f);
                return;
            }
            if (age == 22) {
                dir[0] = look;
                origin[0] = eye.add(look.multiply(2));
                sound(w, p.getPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 3f, 0.8f);
                Vfx.spawn(w, Vfx.BEAM, origin[0], look, 18, 0xa020ff, 46f, 0);
                Vfx.spawn(w, Vfx.SHOCK, origin[0], look, 16, 0xc080ff, 9f, 0);
                Vfx.screen(w, origin[0], 60, Vfx.SCREEN_FLASH, 10, 0.8f, 0xe0c0ff);
                Vfx.screen(w, origin[0], 60, Vfx.SCREEN_SHAKE, 36, 1.4f, 0);
            }
            int start = (age - 22) * 3;
            for (int s = start; s < start + 3; s++) {
                Vec3d c = origin[0].add(dir[0].multiply(s));
                carve(w, c, 3);
                for (Entity e : w.getOtherEntities(p, new Box(c, c).expand(4), en -> en instanceof LivingEntity && valid(en))) {
                    if (hit.add(e.getUuid())) {
                        hurt(w, p, (LivingEntity) e, 60f);
                        launch(e, dir[0].multiply(2).add(0, 0.4, 0));
                    }
                }
            }
        });
        return true;
    }

    public static boolean voidDomain(ServerWorld w, ServerPlayerEntity p) {
        return domain(w, p, 0, 0x7a6cff, 14, 240, "Infinite Void", 10,
                owner -> {
                    PlayerState.data(owner).ce = PlayerState.MAX_CE;
                    fx(owner, StatusEffects.STRENGTH, 30, 2);
                    fx(owner, StatusEffects.SPEED, 30, 2);
                    fx(owner, StatusEffects.RESISTANCE, 30, 1);
                    fx(owner, StatusEffects.REGENERATION, 30, 1);
                },
                (ww, owner, e, age) -> {
                    fx(e, StatusEffects.SLOWNESS, 30, 9);
                    fx(e, StatusEffects.MINING_FATIGUE, 30, 4);
                    fx(e, StatusEffects.NAUSEA, 60, 0);
                    fx(e, StatusEffects.WEAKNESS, 30, 3);
                    hurt(ww, owner, e, 2.5f);
                },
                null);
    }

    // =================================================================== SHRINE
    public static boolean dismantle(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        int[] rolls = {0, 40, -40};
        double[] yaws = {0, 12, -12};
        Set<UUID> hit = new HashSet<>();
        sound(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.6f);
        for (int k = 0; k < 3; k++) {
            Vec3d d = look.rotateY((float) Math.toRadians(yaws[k])).normalize();
            Vfx.spawn(w, Vfx.SLASH, eye.add(d.multiply(1.5)).add(0, -0.4, 0), d, 9, 0xff2a3a, k == 0 ? 3.8f : 3.0f, rolls[k], 24f);
            for (Entity e : w.getOtherEntities(p, new Box(eye, eye).expand(26), en -> en instanceof LivingEntity && valid(en))) {
                Vec3d m = mid(e).subtract(eye);
                double t = m.dotProduct(d);
                if (t < 0 || t > 24) continue;
                if (m.subtract(d.multiply(t)).length() > 1.9) continue;
                if (hit.add(e.getUuid())) {
                    hurt(w, p, (LivingEntity) e, 16f);
                    launch(e, d.multiply(0.8).add(0, 0.25, 0));
                }
            }
            for (int s = 2; s <= 24; s++) {
                BlockPos base = BlockPos.ofFloored(eye.add(d.multiply(s)));
                for (int dx = -1; dx <= 1; dx++)
                    for (int dy = -1; dy <= 1; dy++)
                        for (int dz = -1; dz <= 1; dz++) {
                            BlockPos bp = base.add(dx, dy, dz);
                            BlockState st = w.getBlockState(bp);
                            float h = st.getHardness(w, bp);
                            if (!st.isAir() && h >= 0 && h <= 1.0f && w.getFluidState(bp).isEmpty()) w.breakBlock(bp, false);
                        }
            }
        }
        Vfx.screen(w, p.getPos(), 30, Vfx.SCREEN_SHAKE, 8, 0.4f, 0);
        return true;
    }

    public static boolean cleave(ServerWorld w, ServerPlayerEntity p) {
        LivingEntity t = target(w, p, 24);
        if (t == null) { p.sendMessage(Text.literal("\u00a77No target."), true); return false; }
        float dmg = Math.min(70f, 10f + t.getMaxHealth() * 0.25f);
        Vec3d m = mid(t);
        Vec3d d = m.subtract(p.getEyePos()).normalize();
        Vfx.spawn(w, Vfx.SLASH, m.subtract(d.multiply(0.6)), d, 10, 0xff4455, 2.8f, 55, 0f);
        Vfx.spawn(w, Vfx.SLASH, m.subtract(d.multiply(0.6)), d, 10, 0xff4455, 2.8f, -55, 0f);
        Vfx.spawn(w, Vfx.SHOCK, m, Vec3d.ZERO, 10, 0xffffff, 4f, 0);
        Vfx.screen(w, m, 30, Vfx.SCREEN_SHAKE, 10, 0.7f, 0);
        sound(w, m, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 2f, 0.6f);
        hurt(w, p, t, dmg);
        for (Entity e : w.getOtherEntities(p, new Box(m, m).expand(3), en -> en instanceof LivingEntity && valid(en) && en != t))
            hurt(w, p, (LivingEntity) e, dmg * 0.4f);
        Effects.later(5, () -> {
            if (!t.isAlive()) return;
            Vec3d m2 = mid(t);
            Vfx.spawn(w, Vfx.SLASH, m2.subtract(d.multiply(0.6)), d, 8, 0xff4455, 2.2f, 0, 0f);
            Vfx.spawn(w, Vfx.SLASH, m2.subtract(d.multiply(0.6)), d, 8, 0xff4455, 2.2f, 90, 0f);
            hurt(w, p, t, dmg * 0.4f);
        });
        return true;
    }

    public static boolean flameArrow(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        Vec3d start = eye.add(look.multiply(1.2));
        Vec3d at = impact(w, p, 50);
        double dist = Math.max(2, start.distanceTo(at));
        int travel = Math.max(3, (int) (dist / 3.5));
        Vfx.spawn(w, Vfx.BOLT, start, look, travel, 0xff8a1f, (float) dist, 1);
        sound(w, start, SoundEvents.ITEM_FIRECHARGE_USE, 2f, 0.6f);
        Effects.later(travel, () -> {
            w.createExplosion(p, at.x, at.y, at.z, 2.5f, World.ExplosionSourceType.NONE);
            Vfx.spawn(w, Vfx.SHOCK, at, Vec3d.ZERO, 20, 0xff8a1f, 11f, 0);
            Vfx.spawn(w, Vfx.SHOCK, at, look, 14, 0xffe0a0, 6f, 0);
            w.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 120, 2.5, 1.5, 2.5, 0.12);
            w.spawnParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 30, 2, 1, 2, 0);
            Vfx.screen(w, at, 40, Vfx.SCREEN_SHAKE, 18, 0.9f, 0);
            for (Entity e : w.getOtherEntities(p, new Box(at, at).expand(6), en -> en instanceof LivingEntity && valid(en))) {
                hurt(w, p, (LivingEntity) e, 18f);
                e.setOnFireFor(8);
                launch(e, new Vec3d(0, 0.6, 0).add(mid(e).subtract(at).normalize().multiply(1.2)));
            }
        });
        return true;
    }

    public static boolean shrineDomain(ServerWorld w, ServerPlayerEntity p) {
        Random rnd = new Random();
        return domain(w, p, 1, 0xe0102a, 16, 240, "Malevolent Shrine", 4,
                owner -> {
                    PlayerState.data(owner).ce = PlayerState.MAX_CE;
                    fx(owner, StatusEffects.STRENGTH, 30, 2);
                    fx(owner, StatusEffects.SPEED, 30, 1);
                    fx(owner, StatusEffects.RESISTANCE, 30, 1);
                },
                (ww, owner, e, age) -> {
                    hurt(ww, owner, e, 2.4f);
                    if (age % 12 == 0) {
                        Vec3d m = mid(e);
                        Vec3d d = new Vec3d(rnd.nextGaussian(), rnd.nextGaussian() * 0.3, rnd.nextGaussian()).normalize();
                        Vfx.spawn(ww, Vfx.SLASH, m.subtract(d), d, 8, 0xff2a3a, 2.2f, rnd.nextInt(180) - 90, 0f);
                    }
                },
                (ww, owner, c, R, age) -> {
                    if (age % 20 != 0) return;
                    for (int i = 0; i < 30; i++) {
                        Vec3d v = new Vec3d(rnd.nextGaussian(), rnd.nextGaussian() * 0.4, rnd.nextGaussian()).normalize().multiply(rnd.nextDouble() * (R - 1));
                        BlockPos bp = BlockPos.ofFloored(c.add(v));
                        BlockState st = ww.getBlockState(bp);
                        float h = st.getHardness(ww, bp);
                        if (!st.isAir() && h >= 0 && h <= 1.5f && ww.getFluidState(bp).isEmpty()) ww.breakBlock(bp, false);
                    }
                });
    }

    // =================================================================== BRAWLER
    public static boolean rush(ServerWorld w, ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d dir = new Vec3d(look.x, Math.max(look.y * 0.3, 0.05), look.z).normalize();
        Vec3d start = p.getPos().add(0, 1, 0);
        launch(p, dir.multiply(2.4));
        Vfx.spawn(w, Vfx.BEAM, start, dir, 8, 0xffa040, 10f, 1);
        Vfx.spawn(w, Vfx.SHOCK, start, dir, 8, 0xffd080, 4f, 0);
        sound(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 2f, 0.7f);
        Set<UUID> hit = new HashSet<>();
        Effects.run(8, age -> {
            for (Entity e : w.getOtherEntities(p, p.getBoundingBox().expand(1.6), en -> en instanceof LivingEntity && valid(en))) {
                if (hit.add(e.getUuid())) {
                    hurt(w, p, (LivingEntity) e, 9f);
                    launch(e, dir.multiply(1.4).add(0, 0.4, 0));
                    Vfx.spawn(w, Vfx.SHOCK, mid(e), Vec3d.ZERO, 8, 0xffd080, 3.5f, 0);
                    Vfx.screen(w, mid(e), 20, Vfx.SCREEN_SHAKE, 6, 0.5f, 0);
                }
            }
        });
        return true;
    }

    public static boolean fist(ServerWorld w, ServerPlayerEntity p) {
        LivingEntity t = target(w, p, 6);
        if (t == null) { p.sendMessage(Text.literal("\u00a77No target in reach."), true); return false; }
        Vec3d d = mid(t).subtract(p.getEyePos()).normalize();
        hurt(w, p, t, 8f);
        Vfx.spawn(w, Vfx.SHOCK, mid(t), d, 8, 0xffe0a0, 3f, 0);
        sound(w, mid(t), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 2f, 0.7f);
        Effects.later(10, () -> {
            if (!t.isAlive()) return;
            Vec3d m = mid(t);
            hurt(w, p, t, 16f);
            launch(t, d.multiply(1.9).add(0, 0.5, 0));
            Vfx.spawn(w, Vfx.SHOCK, m, d, 14, 0xff9030, 8f, 0);
            Vfx.spawn(w, Vfx.SHOCK, m, Vec3d.ZERO, 10, 0xffffff, 4f, 0);
            Vfx.spawn(w, Vfx.LIGHTNING, m, Vec3d.ZERO, 8, 0xffa040, 2f, 0);
            Vfx.screen(w, m, 30, Vfx.SCREEN_SHAKE, 12, 0.9f, 0);
            Vfx.screen(w, m, 20, Vfx.SCREEN_FLASH, 4, 0.4f, 0xffc070);
            sound(w, m, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.5f, 1.2f);
        });
        return true;
    }

    public static boolean barrage(ServerWorld w, ServerPlayerEntity p) {
        LivingEntity t = target(w, p, 14);
        if (t == null) t = nearest(w, p, 8);
        if (t == null) { p.sendMessage(Text.literal("\u00a77No target nearby."), true); return false; }
        final LivingEntity tg = t;
        Vec3d to = mid(tg).subtract(p.getPos());
        Vec3d flat = new Vec3d(to.x, 0, to.z);
        flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        final Vec3d fl = flat;
        Vec3d dest = tg.getPos().subtract(fl.multiply(1.6));
        if (w.isSpaceEmpty(p, p.getBoundingBox().offset(dest.subtract(p.getPos())))) {
            Vfx.spawn(w, Vfx.BEAM, p.getPos().add(0, 1, 0), dest.subtract(p.getPos()).normalize(), 6, 0xff4020, (float) dest.distanceTo(p.getPos()), 1);
            float yaw = (float) (MathHelper.atan2(fl.z, fl.x) * 180 / Math.PI) - 90f;
            p.networkHandler.requestTeleport(dest.x, dest.y, dest.z, yaw, 0f);
        }
        fx(p, StatusEffects.RESISTANCE, 40, 2);
        Effects.run(24, age -> {
            if (!tg.isAlive() || !p.isAlive()) return;
            if (age % 4 != 0 || age / 4 >= 5) return;
            Vec3d m = mid(tg);
            Vec3d d = fl;
            if (age / 4 < 4) {
                hurt(w, p, tg, 6f);
                launch(tg, d.multiply(0.15).add(0, 0.05, 0));
                Vfx.spawn(w, Vfx.SHOCK, m, d, 8, 0xffa040, 3f + age / 4f, 0);
                Vfx.screen(w, m, 20, Vfx.SCREEN_SHAKE, 5, 0.4f, 0);
                sound(w, m, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.5f, 0.8f + age * 0.02f);
            } else {
                BlackFlash.strike(w, p, tg, 36f);
                launch(tg, d.multiply(2.2).add(0, 0.5, 0));
            }
        });
        return true;
    }

    public static boolean overdrive(ServerWorld w, ServerPlayerEntity p) {
        PlayerState.Data d = PlayerState.data(p);
        d.overdriveUntil = w.getTime() + 200;
        int col = color(p);
        Vfx.spawn(w, Vfx.AURA, p.getPos(), Vec3d.ZERO, 200, col, 1.8f, p.getId());
        Vfx.spawn(w, Vfx.SHOCK, p.getPos().add(0, 0.2, 0), Vec3d.ZERO, 18, col, 12f, 0);
        Vfx.screen(w, p.getPos(), 40, Vfx.SCREEN_FLASH, 10, 0.7f, 0xff5030);
        Vfx.screen(w, p.getPos(), 40, Vfx.SCREEN_SHAKE, 20, 1f, 0);
        sound(w, p.getPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 2f, 1.2f);
        p.getServer().getPlayerManager().broadcast(Text.literal("\u00a7c\u00a7l" + p.getName().getString() + " enters OVERDRIVE"), false);
        Effects.run(200, age -> {
            if (!p.isAlive()) return;
            if (age % 5 == 0) {
                fx(p, StatusEffects.STRENGTH, 20, 3);
                fx(p, StatusEffects.SPEED, 20, 3);
                fx(p, StatusEffects.RESISTANCE, 20, 1);
                fx(p, StatusEffects.REGENERATION, 20, 2);
                fx(p, StatusEffects.JUMP_BOOST, 20, 2);
            }
            if (age % 20 == 10) {
                Vec3d f = p.getPos().add(0, 0.2, 0);
                Vfx.spawn(w, Vfx.SHOCK, f, Vec3d.ZERO, 12, col, 6f, 0);
                Vfx.spawn(w, Vfx.LIGHTNING, p.getPos().add(0, 1, 0), Vec3d.ZERO, 8, 0xff3040, 2.2f, 0);
                for (Entity e : w.getOtherEntities(p, p.getBoundingBox().expand(5), en -> en instanceof LivingEntity && valid(en))) {
                    hurt(w, p, (LivingEntity) e, 6f);
                    launch(e, mid(e).subtract(p.getPos()).normalize().multiply(1.1).add(0, 0.35, 0));
                }
            }
        });
        return true;
    }

    // =================================================================== SHADOW
    public static boolean shadowStep(ServerWorld w, ServerPlayerEntity p) {
        LivingEntity t = target(w, p, 25);
        Vec3d from = p.getPos();
        Vec3d dest;
        float yaw = p.getYaw();
        if (t != null) {
            Vec3d face = t.getRotationVec(1f);
            Vec3d flat = new Vec3d(face.x, 0, face.z);
            flat = flat.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : flat.normalize();
            dest = t.getPos().subtract(flat.multiply(1.6));
            yaw = (float) (MathHelper.atan2(flat.z, flat.x) * 180 / Math.PI) - 90f;
        } else {
            Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
            Vec3d end = eye.add(look.multiply(12));
            BlockHitResult hr = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            Vec3d tgt = hr.getType() == HitResult.Type.MISS ? end : hr.getPos().subtract(look.multiply(1.0));
            dest = tgt.subtract(0, p.getStandingEyeHeight(), 0);
        }
        if (!w.isSpaceEmpty(p, p.getBoundingBox().offset(dest.subtract(from)))) {
            p.sendMessage(Text.literal("\u00a77Blocked."), true);
            return false;
        }
        int col = color(p);
        Vfx.spawn(w, Vfx.SHOCK, from.add(0, 0.2, 0), Vec3d.ZERO, 12, col, 5f, 0);
        Vec3d line = dest.subtract(from);
        if (line.length() > 0.5) Vfx.spawn(w, Vfx.BEAM, from.add(0, 1, 0), line.normalize(), 8, col, (float) line.length(), 1);
        p.networkHandler.requestTeleport(dest.x, dest.y, dest.z, yaw, p.getPitch());
        Vfx.spawn(w, Vfx.SHOCK, dest.add(0, 0.2, 0), Vec3d.ZERO, 12, col, 5f, 0);
        sound(w, dest, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);
        fx(p, StatusEffects.SPEED, 40, 2);
        if (t != null) {
            fx(p, StatusEffects.STRENGTH, 60, 1);
            Effects.later(2, () -> {
                if (!t.isAlive()) return;
                hurt(w, p, t, 12f);
                Vfx.spawn(w, Vfx.SLASH, mid(t).subtract(0, 0, 0), p.getRotationVec(1f), 8, 0x9a7cff, 2.2f, 30, 0f);
                Vfx.screen(w, mid(t), 20, Vfx.SCREEN_SHAKE, 6, 0.5f, 0);
            });
        }
        return true;
    }

    public static boolean shadowSpikes(ServerWorld w, ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        dir = dir.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : dir.normalize();
        final Vec3d d = dir;
        final Vec3d base = p.getPos();
        Set<UUID> hit = new HashSet<>();
        sound(w, base, SoundEvents.ENTITY_WITHER_SHOOT, 1.5f, 0.5f);
        Effects.run(14, age -> {
            double dist = 2 + age * 1.5;
            Vec3d pt = base.add(d.multiply(dist));
            double gy = groundY(w, pt.x, pt.y, pt.z);
            Vec3d gp = new Vec3d(pt.x, gy, pt.z);
            Vfx.spawn(w, Vfx.SPIKES, gp, Vec3d.ZERO, 18, 0x9a7cff, 1.6f, 0);
            for (Entity e : w.getOtherEntities(p, new Box(gp, gp).expand(2.0, 3, 2.0), en -> en instanceof LivingEntity && valid(en))) {
                if (hit.add(e.getUuid())) {
                    hurt(w, p, (LivingEntity) e, 13f);
                    launch(e, new Vec3d(0, 0.9, 0));
                    fx((LivingEntity) e, StatusEffects.SLOWNESS, 40, 2);
                }
            }
            if (age % 3 == 0) Vfx.screen(w, gp, 24, Vfx.SCREEN_SHAKE, 4, 0.3f, 0);
        });
        return true;
    }

    public static boolean shadowPool(ServerWorld w, ServerPlayerEntity p) {
        Vec3d at = impact(w, p, 15);
        double gy = groundY(w, at.x, at.y, at.z);
        Vec3d c = new Vec3d(at.x, gy, at.z);
        double R = 6.5;
        int col = color(p);
        Vfx.spawn(w, Vfx.POOL, c, Vec3d.ZERO, 120, col, (float) R, 0);
        sound(w, c, SoundEvents.ENTITY_WARDEN_EMERGE, 1.5f, 1.2f);
        Effects.run(120, age -> {
            if (age % 5 != 0) return;
            for (Entity e : w.getOtherEntities(p, new Box(c, c).expand(R, 3, R), en -> en instanceof LivingEntity && valid(en))) {
                double dx = e.getX() - c.x, dz = e.getZ() - c.z;
                if (dx * dx + dz * dz > R * R) continue;
                fx((LivingEntity) e, StatusEffects.SLOWNESS, 12, 4);
                fx((LivingEntity) e, StatusEffects.WEAKNESS, 12, 1);
                if (age % 20 == 0) hurt(w, p, (LivingEntity) e, 2f);
            }
            double ox = p.getX() - c.x, oz = p.getZ() - c.z;
            if (p.isAlive() && ox * ox + oz * oz <= R * R) {
                fx(p, StatusEffects.SPEED, 12, 2);
                fx(p, StatusEffects.INVISIBILITY, 12, 0);
            }
        });
        return true;
    }

    public static boolean garden(ServerWorld w, ServerPlayerEntity p) {
        return domain(w, p, 2, 0xb060ff, 14, 240, "Chimera Garden", 10,
                owner -> {
                    PlayerState.data(owner).ce = PlayerState.MAX_CE;
                    fx(owner, StatusEffects.STRENGTH, 30, 2);
                    fx(owner, StatusEffects.SPEED, 30, 2);
                    fx(owner, StatusEffects.RESISTANCE, 30, 1);
                    fx(owner, StatusEffects.INVISIBILITY, 30, 0);
                    fx(owner, StatusEffects.REGENERATION, 30, 1);
                },
                (ww, owner, e, age) -> {
                    fx(e, StatusEffects.SLOWNESS, 30, 3);
                    fx(e, StatusEffects.WEAKNESS, 30, 2);
                    fx(e, StatusEffects.DARKNESS, 40, 0);
                    hurt(ww, owner, e, 6f);
                    launch(e, new Vec3d(0, 0.5, 0));
                    Vec3d g = new Vec3d(e.getX(), groundY(ww, e.getX(), e.getY(), e.getZ()), e.getZ());
                    Vfx.spawn(ww, Vfx.SPIKES, g, Vec3d.ZERO, 16, 0xb060ff, 1.3f, 0);
                },
                null);
    }

    // =================================================================== SHARED
    public static boolean rct(ServerWorld w, ServerPlayerEntity p) {
        if (p.getHealth() >= p.getMaxHealth() && p.getStatusEffects().isEmpty()) {
            p.sendMessage(Text.literal("\u00a77You're already in perfect shape."), true);
            return false;
        }
        p.heal(12f);
        List<StatusEffect> bad = new ArrayList<>();
        for (StatusEffectInstance inst : p.getStatusEffects())
            if (inst.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) bad.add(inst.getEffectType());
        for (StatusEffect e : bad) p.removeStatusEffect(e);
        fx(p, StatusEffects.REGENERATION, 80, 1);
        Vfx.spawn(w, Vfx.HEAL, p.getPos(), Vec3d.ZERO, 24, 0x40ff80, 1f, p.getId());
        sound(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_PLACE, 2f, 1.5f);
        return true;
    }

    public static boolean reinforce(ServerWorld w, ServerPlayerEntity p) {
        PlayerState.Data d = PlayerState.data(p);
        if (d.reinforced) {
            d.reinforced = false;
            p.sendMessage(Text.literal("\u00a77Reinforcement released."), true);
        } else {
            if (d.ce < 20 && !p.isCreative()) {
                p.sendMessage(Text.literal("\u00a7cNot enough cursed energy!"), true);
                return false;
            }
            d.reinforced = true;
            Vfx.spawn(w, Vfx.SHOCK, p.getPos().add(0, 0.2, 0), Vec3d.ZERO, 12, color(p), 5f, 0);
            sound(w, p.getPos(), SoundEvents.ENTITY_BLAZE_SHOOT, 1.5f, 0.6f);
            p.sendMessage(Text.literal("\u00a7bReinforcement ON"), true);
        }
        return true;
    }

    // =================================================================== DOMAIN ENGINE
    interface Inside { void apply(ServerWorld w, ServerPlayerEntity owner, LivingEntity e, int age); }
    interface DomainTick { void tick(ServerWorld w, ServerPlayerEntity owner, Vec3d c, double R, int age); }

    private static boolean domain(ServerWorld w, ServerPlayerEntity p, int style, int color, double R, int dur,
                                  String title, int period, Consumer<ServerPlayerEntity> ownerBuff,
                                  Inside inside, DomainTick extra) {
        final Vec3d c = p.getPos().add(0, 1, 0);
        sound(w, c, SoundEvents.ENTITY_WITHER_SPAWN, 2f, 0.6f);
        p.getServer().getPlayerManager().broadcast(
                Text.literal("\u00a7d\u00a7l" + p.getName().getString() + ": DOMAIN EXPANSION - " + title), false);
        Vfx.spawn(w, Vfx.DOMAIN, c, Vec3d.ZERO, dur, color, (float) R, style);
        Vfx.screen(w, c, 50, Vfx.SCREEN_FLASH, 14, 1f, 0xffffff);
        Vfx.screen(w, c, 50, Vfx.SCREEN_SHAKE, 40, 1.2f, 0);

        Effects.run(dur, age -> {
            if (!p.isAlive() || p.isRemoved()) return;
            if (age % 40 == 0) sound(w, c, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 3f, 0.7f);
            if (extra != null) extra.tick(w, p, c, R, age);
            if (age % 10 == 0 && p.squaredDistanceTo(c) <= R * R) ownerBuff.accept(p);
            boolean tickEnemies = age % period == 0;
            for (Entity e : w.getOtherEntities(p, new Box(c, c).expand(R), en -> en instanceof LivingEntity && valid(en) && en.squaredDistanceTo(c) <= R * R)) {
                if (tickEnemies) inside.apply(w, p, (LivingEntity) e, age);
                if (age % 10 == 0 && e instanceof ServerPlayerEntity sp) Vfx.screenTo(sp, Vfx.SCREEN_VIGNETTE, 14, 1f, color);
            }
            if (age == dur - 1) {
                sound(w, c, SoundEvents.BLOCK_GLASS_BREAK, 3f, 0.5f);
                Vfx.screen(w, c, 50, Vfx.SCREEN_SHAKE, 15, 1f, 0);
                p.sendMessage(Text.literal("\u00a75Domain collapsed."), true);
            }
        });
        return true;
    }
}
