package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * TNL1023 \u2605\u2605 \u6052\u6b63\u79e9\u5668 Aeonian Order \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a
 * <ul>
 *   <li>\u2605\u53f3\u952e\u6325\u51fb\uff1a\u2605**\u5411\u524d\u63a8\u529b\u7a81\u8fdb**\uff08\u2605\u4e0d\u662f\u77ac\u79fb \u2713\uff09\uff0c\u2605\u8fd1\u4f3c **5 \u683c** \u2713\uff1b</li>
 *   <li>\u2605\u968f\u540e\u5bf9\u9762\u524d\u4e00\u7247 **2 \u5bbd \u00d7 5 \u957f** \u7684\u533a\u57df\u505a\u4e00\u6b21\u6a2a\u626b \u2713\uff1b</li>
 *   <li>\u2605\u4f24\u5bb3\uff1a\u2605\u7269\u7406 **12** \u2713 \u002b \u2605\u5176\u4e2d **50%** \u7684**\u9b54\u6cd5**\u4f24\u5bb3\uff08\u2605**\u4e0d\u662f\u771f\u4f24** \u2717\uff09\uff1b</li>
 *   <li>\u2605\u4f24\u5bb3\u4ee5 **2:1** \u6cbb\u6108\u81ea\u8eab\uff08\u2605\u6cbb\u7597 = \u4f24\u5bb3 / 2 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u51b7\u5374\uff1a\u2605\u8d70\u539f\u7248 ``ItemCooldowns``\uff0c\u2605**10 \u79d2** \u2713\uff08\u2605\u8010\u4e45\u7528\u6237\u8bf4\u6700\u540e\u518d\u505a \u2713\uff09\u3002</li>
 * </ul>
 */
public class AeonianOrderItem extends SwordItem {

    /** \u2605\u7a81\u8fdb\u521d\u901f\u5ea6\uff08\u2605\u914d\u5408\u6469\u64e6\u7ea6\u8dd1 5 \u683c \u2713\uff09 */
    private static final double THRUST = 1.55D;
    /** \u2605\u6a2a\u626b\u5ef6\u8fdf\uff08\u2605tick \u2713\uff09 */
    private static final int SWEEP_DELAY = 8;
    /** \u2605\u51b7\u5374 10 \u79d2 \u2713 */
    private static final int COOLDOWN = 200;
    /** \u2605\u7269\u7406\u4f24\u5bb3 12 \u2713 */
    private static final float PHYS = 12.0F;
    /** \u2605\u9b54\u6cd5\u4f24\u5bb3 = \u7269\u7406 \u00d7 50% \u2713 */
    private static final float MAGIC = PHYS * 0.5F;

    private static final List<Pending> PENDING = new ArrayList<>();

    public AeonianOrderItem() {
        super(Tiers.NETHERITE, 8, -2.4F, new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // TNL1046 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6ca1\u6302\u4e0a\u554a" \u2713
        //   \u2605\u73b0\u8c61 \u2717\uff1a\u2605\u65e5\u5fd7\u91cc `TNL1023` \u4e0e `TNL1043` **\u4e00\u884c\u90fd\u6ca1\u6709** \u2713
        //     \u21d2 \u2605\u8bf4\u660e\u2605**\u53f3\u952e\u6839\u672c\u6ca1\u8fdb\u5230\u8fd9\u91cc** \u2717\uff08\u2605\u4e0d\u662f\u53e0 tick \u7684\u95ee\u9898 \u2713\uff09
        //   \u2605\u6240\u4ee5\u5148\u6253\u4e00\u884c**\u5165\u53e3**\u63a2\u9488 \u2713\uff08\u2605\u4e24\u4fa7\u90fd\u6253 \u2713\uff09
        boolean tnl$shift = player.isShiftKeyDown();
        TinkersNewlife.LOGGER.warn("TNL1046 aeonian use() called side={} cooldown={} shift={}",
                player.level().isClientSide ? "client" : "server",
                player.getCooldowns().isOnCooldown(this), tnl$shift);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        // TNL1050 \u2605\u2605 \u7528\u6237\uff1a\u2605"shift \u53f3\u952e\u8fd8\u662f\u7a81\u523a\u6280\u80fd" \u2713 \u21d2 \u2605\u73b0\u5728\u5206\u5f00 \u2713
        if (tnl$shift) {
            if (!level.isClientSide) {
                // TNL1055 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u6293\u94a9\u6539\u6210\u671d\u89c6\u7ebf\u65b9\u5411\u98de\u7136\u540e\u62c9\u7740\u73a9\u5bb6\u8fc7\u53bb\uff0c\u6700\u591a\u98de20\u683c\u3002\u522b\u7528\u98de\u5251\u8ffd\u8e2a" \u2713
                //   \u2605\u6240\u4ee5\u628a\u4e0a\u4e00\u7248\u7684"\u2605\u751f\u6210 FlyingSwordEntity"**\u6574\u6bb5\u5220\u6389** \u2713
                //     \uff08\u2605\u5b83\u5e26\u81ea\u52a8\u9501\u5b9a/\u5236\u5bfc\u8ffd\u8e2a \u2717 \u2014\u2014 \u2605\u6b63\u662f\u4f60\u4e0d\u8981\u7684 \u2713\uff09
                //   \u2605\u73b0\u5728 \u2713\uff1a\u2605\u7eaf\u76f4\u7ebf\u3001\u65e0\u91cd\u529b\u3001\u6cbf\u89c6\u7ebf\u3001**\u6700\u591a 20 \u683c** \u2713
                tnl$throwHook(level, player, stack);
                player.getCooldowns().addCooldown(this, 40);
            }
            player.swing(hand, true);
            return InteractionResultHolder.success(stack);
        }
        if (!level.isClientSide) {
            // \u2605\u2460 \u5411\u524d\u63a8\u529b\uff08\u2605\u4e0d\u662f\u77ac\u79fb \u2713\uff09
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            if (flat.lengthSqr() < 1.0E-6D) {
                flat = Vec3.directionFromRotation(0.0F, player.getYRot());
            }
            flat = flat.normalize();
            player.setDeltaMovement(flat.x * THRUST, 0.16D, flat.z * THRUST);
            player.hurtMarked = true;                 // \u2605\u540c\u6b65\u7ed9\u5ba2\u6237\u7aef \u2713
            player.hasImpulse = true;
            player.fallDistance = 0.0F;
            // \u2605\u2461 \u9884\u7ea6\u6a2a\u626b \u2713
            PENDING.add(new Pending(player.getUUID(), SWEEP_DELAY, flat));
            player.getCooldowns().addCooldown(this, COOLDOWN);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 1.4F, 0.9F);
            TinkersNewlife.LOGGER.warn("TNL1023 aeonian order thrust fired, sweep in {} ticks", SWEEP_DELAY);
        }
        player.swing(hand, true);
        return InteractionResultHolder.success(stack);
    }

    /** \u2605\u5f85\u6267\u884c\u7684\u6a2a\u626b \u2713 */
    private record Pending(java.util.UUID owner, int ticks, Vec3 dir) {
    }

    /** TNL1050 \u2605\u5f85\u6267\u884c\u7684"\u2605\u628a\u73a9\u5bb6\u62c9\u8fc7\u53bb" \u2713 */
    private record Pull(java.util.UUID owner, Vec3 target, int ticks) {
    }

    private static final List<Pull> PULLS = new ArrayList<>();

    /** TNL1057 user: launch an actual scythe that flies out, then pull the player to where it stopped */
    private record Hook(java.util.UUID owner, java.util.UUID item, int ticks, Vec3 dir) {
    }

    private static final List<Hook> HOOKS = new ArrayList<>();
    private static final int HOOK_TICKS = 14;   // TNL1059 2.6 blocks per tick, enough for 20

    /** TNL1050 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u53ef\u4ee5\u5c06\u9570\u5200\u5411\u89c6\u7ebf\u65b9\u5411\u7529\u51fa\u53bb\uff08\u65e0\u89c6\u91cd\u529b\uff09\uff0c
     *   \u9570\u5200\u4e0a\u4f1a\u8fde\u7740\u4e00\u6839\u9ed1\u8272\u7684\u7ebf\uff0c\u5982\u679c\u547d\u4e2d\u65b9\u5757\uff0c\u4f1a\u5c06\u73a9\u5bb6\u62c9\u5230\u90a3\u4e2a\u4f4d\u7f6e\uff0c
     *   \u5982\u679c\u547d\u4e2d\u5b9e\u4f53\uff0c\u5219\u4f1a\u5c06\u5b9e\u4f53\u52fe\u8fc7\u6765" \u2713
     *
     * <p>\u2605\u672c\u7248\u5148\u7528"\u2605\u65e0\u91cd\u529b\u7684\u76f4\u7ebf\u5c04\u7ebf"\u505a\u884c\u4e3a \u2713\uff08\u2605\u76f4\u7ebf\u672c\u8eab\u5c31\u4e0d\u53d7\u91cd\u529b \u2713\uff09
     * \uff0c\u2605\u7ef3\u5b50\u5148\u7528\u9ed1\u8272\u7c92\u5b50\u7ebf\u8868\u793a \u2713\uff0c
     * \u2605\u4e0b\u4e00\u8f6e\u518d\u7167 `FlyingSwordTrailRenderer` \u6362\u6210\u4e13\u7528\u5e26\u72b6\u7ed8\u5236 \u2713\u3002
     */
    private static void tnl$throwHook(Level level, Player player, ItemStack heldStack) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = 20.0D;   // TNL1055 \u2605\u7528\u6237\uff1a\u2605\u6700\u591a\u98de 20 \u683c \u2713
        Vec3 end = eye.add(look.scale(reach));
        // TNL1056 user: the "hook entities" behaviour is cancelled -- blocks only now
        net.minecraft.world.phys.BlockHitResult bhit = level.clip(new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 hit = bhit == null ? end : bhit.getLocation();
        boolean hitBlock = bhit != null;
        // \u2605\u9ed1\u8272\u7c92\u5b50\u7ebf\uff08\u2605\u6682\u4ee3\u7ef3\u5b50 \u2713\uff09
        if (level instanceof ServerLevel sl) {
            // TNL1053 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u9570\u5200\u4e0a\u4f1a\u8fde\u7740\u4e00\u6839\u9ed1\u8272\u7684\u7ebf" \u2713
            //   \u2605\u73b0\u5728\uff1a\u2605\u7528**\u7eaf\u9ed1 dust**\u5806\u6210\u4e00\u6761\u7c97\u7ebf \u2713\uff08\u2605\u6bd4 squid_ink \u66f4\u50cf\u7ef3\u5b50 \u2713\uff09
            //     \u2605\u6bcf 1/6 \u683c\u4e00\u9897 \u2713 \u002b \u2605\u7ec6\u5fae\u6296\u52a8 \u2713 \u21d2 \u2605\u8fdc\u770b\u5c31\u662f\u4e00\u6761\u9ed1\u7ebf \u2713
            var tnl$black = new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(0.02F, 0.02F, 0.03F), 1.6F);
            int steps = (int) Math.min(96, Math.max(6, eye.distanceTo(hit) * 6.0D));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double wob = Math.sin(t * 24.0D + level.getGameTime() * 0.35D) * 0.045D;
                sl.sendParticles(tnl$black,
                        eye.x + (hit.x - eye.x) * t + wob,
                        eye.y + (hit.y - eye.y) * t + wob * 0.6D,
                        eye.z + (hit.z - eye.z) * t,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        // TNL1057 fly our own scythe out: a zero gravity item entity carrying a copy of the stack
        var thrown = new net.minecraft.world.entity.item.ItemEntity(
                player.level(), player.getX(), player.getEyeY() - 0.25D, player.getZ(), heldStack.copy());
        thrown.setNoGravity(true);
        thrown.setNeverPickUp();
        thrown.setDeltaMovement(look.scale(2.4D));
        player.level().addFreshEntity(thrown);
        HOOKS.add(new Hook(player.getUUID(), thrown.getUUID(), HOOK_TICKS, look));
        // hide the held scythe while the hook is out (the effect duration is the lifetime)
        player.addEffect(new MobEffectInstance(
                com.mofengbaizhi.tinkersnewlife.content.ModEffects.AEONIAN_HOOK.get(),
                hitBlock ? HOOK_TICKS : 6, 0, false, false, false));
        TinkersNewlife.LOGGER.warn("TNL1057 threw the scythe ({}), hitBlock={}", thrown.getUUID(), hitBlock);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    /** TNL1023 \u2605\u5230\u70b9\u4e86\u5c31\u6a2a\u626b \u2713 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
    public static final class Ticker {

        private Ticker() {
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            // TNL1050 \u2605\u2605 \u5904\u7406"\u2605\u628a\u73a9\u5bb6\u62c9\u5230\u547d\u4e2d\u70b9" \u2713
            if (!PULLS.isEmpty()) {
                var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    List<Pull> next = new ArrayList<>();
                    for (Pull pl : PULLS) {
                        if (pl.ticks() <= 0) {
                            continue;
                        }
                        net.minecraft.world.entity.Entity owner = null;
                        for (ServerLevel lvl : server.getAllLevels()) {
                            net.minecraft.world.entity.Entity e2 = lvl.getEntity(pl.owner());
                            if (e2 != null) {
                                owner = e2;
                                break;
                            }
                        }
                        if (owner instanceof Player p2 && p2.isAlive()) {
                            Vec3 want = pl.target().subtract(p2.position());
                            if (want.lengthSqr() > 2.25D) {
                                Vec3 v = want.normalize().scale(1.35D);
                                p2.setDeltaMovement(v.x, Math.max(v.y, 0.22D), v.z);
                                p2.hurtMarked = true;
                                p2.fallDistance = 0.0F;
                                next.add(new Pull(pl.owner(), pl.target(), pl.ticks() - 1));
                            }
                        }
                    }
                    PULLS.clear();
                    PULLS.addAll(next);
                }
            }
            // TNL1057 the rope follows the flying scythe; when it expires we pull the player to it
            if (!HOOKS.isEmpty()) {
                var server2 = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server2 != null) {
                    List<Hook> left = new ArrayList<>();
                    for (Hook hk : HOOKS) {
                        net.minecraft.world.entity.Entity owner = null;
                        net.minecraft.world.entity.Entity scythe = null;
                        for (ServerLevel lvl : server2.getAllLevels()) {
                            if (owner == null) {
                                owner = lvl.getEntity(hk.owner());
                            }
                            if (scythe == null) {
                                scythe = lvl.getEntity(hk.item());
                            }
                        }
                        if (owner == null || scythe == null) {
                            continue;
                        }
                        Vec3 hookEye = owner.getEyePosition();
                        Vec3 hookPos = scythe.position();
                        // TNL1059 keep the speed constant so it reliably covers the full 20 blocks
                        scythe.setDeltaMovement(hk.dir().scale(2.6D));
                        // black rope from the eyes to the flying scythe
                        if (owner.level() instanceof ServerLevel sl) {
                            var tnl$black = new net.minecraft.core.particles.DustParticleOptions(
                                    new org.joml.Vector3f(0.02F, 0.02F, 0.03F), 1.6F);
                            int steps = (int) Math.min(96, Math.max(6, hookEye.distanceTo(hookPos) * 6.0D));
                            long gt = owner.level().getGameTime();
                            for (int i = 0; i <= steps; i++) {
                                double t = i / (double) steps;
                                sl.sendParticles(tnl$black,
                                        hookEye.x + (hookPos.x - hookEye.x) * t + Math.sin(t * 24.0D + gt * 0.35D) * 0.045D,
                                        hookEye.y + (hookPos.y - hookEye.y) * t,
                                        hookEye.z + (hookPos.z - hookEye.z) * t,
                                        1, 0.0D, 0.0D, 0.0D, 0.0D);
                            }
                        }
                        // TNL1058 user: "stop flying when it touches a block"
                        net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(hookPos);
                        boolean blocked = !owner.level().getBlockState(bp)
                                .getCollisionShape(owner.level(), bp).isEmpty();
                        boolean tooFar = hookEye.distanceTo(hookPos) > 20.5D;
                        if (hk.ticks() > 1 && !blocked && !tooFar) {
                            left.add(new Hook(hk.owner(), hk.item(), hk.ticks() - 1, hk.dir()));
                        } else {
                            // it hit a block (or ran out): stop there and pull the player over
                            Vec3 stop = hookPos;
                            scythe.discard();
                            if (owner instanceof Player p3) {
                                // TNL1059 user: pull the player even when it simply ran out of range in the air
                                PULLS.add(new Pull(p3.getUUID(), stop, 16));
                                TinkersNewlife.LOGGER.warn(
                                        "TNL1059 scythe stopped at {} (blocked={} tooFar={}), pulling the player",
                                        stop, blocked, tooFar);
                            }
                        }
                    }
                    HOOKS.clear();
                    HOOKS.addAll(left);
                }
            }
            if (PENDING.isEmpty()) {
                return;
            }
            Iterator<Pending> it = PENDING.iterator();
            List<Pending> ready = new ArrayList<>();
            while (it.hasNext()) {
                Pending p = it.next();
                if (p.ticks() <= 1) {
                    ready.add(p);
                    it.remove();
                } else {
                    ready.add(null);
                }
            }
            // \u2605\u628a\u5269\u4f59\u7684\u5012\u8ba1\u65f6\u51cf\u4e00 \u2713
            List<Pending> next = new ArrayList<>();
            for (int i = 0; i < ready.size(); i++) {
                Pending p = ready.get(i);
                if (p == null) {
                    continue;
                }
            }
            for (Pending p : ready) {
                if (p != null) {
                    sweep(p);
                }
            }
            synchronized (PENDING) {
                List<Pending> dec = new ArrayList<>();
                for (Pending p : PENDING) {
                    dec.add(new Pending(p.owner(), p.ticks() - 1, p.dir()));
                }
                PENDING.clear();
                PENDING.addAll(dec);
            }
        }

        private static void sweep(Pending p) {
            Entity owner = null;
            for (ServerLevel lvl : net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getAllLevels()) {
                Entity e = lvl.getEntity(p.owner());
                if (e != null) {
                    owner = e;
                    break;
                }
            }
            if (!(owner instanceof Player player) || !player.isAlive()) {
                return;
            }
            Level level = player.level();
            Vec3 dir = p.dir().normalize();
            Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);          // \u2605\u5782\u76f4\u65b9\u5411 \u2713
            Vec3 mid = player.position().add(dir.scale(2.75D)); // \u26050.25~5.25 \u683c \u2713
            AABB box = new AABB(mid.x - 1.0D, player.getY() - 1.0D, mid.z - 1.0D,
                    mid.x + 1.0D, player.getY() + 2.0D, mid.z + 1.0D);
            float dealt = 0.0F;
            int hits = 0;
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box)) {
                if (target == player || target.isAlliedTo(player) || player.isAlliedTo(target)) {
                    continue;
                }
                if (target.hurt(level.damageSources().playerAttack(player), PHYS)) {
                    dealt += PHYS;
                    hits++;
                }
                if (target.hurt(level.damageSources().indirectMagic(player, player), MAGIC)) {
                    dealt += MAGIC;
                    hits++;
                }
                // TNL1043 \u2605\u547d\u4e2d\u53e0 tick\uff08\u2605\u6bcf\u6b21 +10 \u2713\uff09
                com.mofengbaizhi.tinkersnewlife.content.effect.AeonianWeaken.add(target, com.mofengbaizhi.tinkersnewlife.content.effect.AeonianWeaken.PER_HIT);
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            target.getX(), target.getY() + 1.0D, target.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.01D);
                }
            }
            if (dealt > 0.0F) {
                player.heal(dealt * 0.5F);                      // \u2605\u26052:1 \u6cbb\u6108 \u2713
            }
            if (level instanceof ServerLevel sl) {
                Vec3 c = player.position().add(dir.scale(2.5D));
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, player.getY() + 1.0D, c.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 1.6F, 0.8F);
            TinkersNewlife.LOGGER.warn("TNL1023 aeonian order sweep hit {} targets, dealt {}, healed {}",
                    hits, dealt, dealt * 0.5F);
        }
    }
}
