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
                // TNL1052 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u76f4\u63a5\u505a\u5b8c" \u2713 \u2014\u2014 \u2605\u628a\u9570\u5200**\u771f\u7684\u7529\u51fa\u53bb** \u2713
                //   \u2605\u53c2\u7167\u98de\u5251\uff08\u2605`FlyingSwordEntity` \u2713\uff09\uff1a
                //     \u2605\u5b83**\u5df2\u7ecf\u5199\u597d\u4e86**\u6211\u4eec\u8981\u7684\u4e00\u5207 \u2713\uff1a
                //       \u00b7 \u2605``FlyingSwordEntity(Level, Player, float damage, ItemStack stack)`` \u2713
                //       \u00b7 \u2605``setLaunchDirection(Vec3)`` \u2713 \u002b \u2605``setChaseMode(false)`` \u2713
                //       \u00b7 \u2605\u81ea\u5e26**\u65e0\u91cd\u529b\u98de\u884c \u002b \u98de\u5251\u5f0f\u5236\u5bfc \u002b \u62d6\u5c3e \u002b \u56de\u624b** \u2713\u2713
                //   \u2605\u4e3a\u4ec0\u4e48\u7528\u53cd\u5c04 \u2717\uff1a\u2605\u5b83\u5728 **imagination** \u2717 \u800c\u672c\u6587\u4ef6\u5728 **cursed** \
                //     \u2605\u53cd\u5c04\u53d6\u6784\u9020\u5668\u5c31\u4e0d\u4f1a\u5f15\u5165\u8de8\u6a21\u5757\u7f16\u8bd1\u4f9d\u8d56 \u2713
                try {
                    Class<?> k = Class.forName("com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordEntity");
                    var ctor = k.getConstructor(Level.class, Player.class, float.class, ItemStack.class);
                    Object ent = ctor.newInstance(level, player, 0.0F, stack.copy());
                    k.getMethod("setLaunchDirection", Vec3.class).invoke(ent, player.getLookAngle());
                    k.getMethod("setChaseMode", boolean.class).invoke(ent, false);
                    ((Entity) ent).setPos(player.getX(), player.getEyeY() - 0.2D, player.getZ());
                    ((Entity) ent).setDeltaMovement(player.getLookAngle().scale(2.2D));
                    level.addFreshEntity((Entity) ent);
                    TinkersNewlife.LOGGER.warn("TNL1052 threw the scythe as a flying sword entity");
                } catch (Throwable t) {
                    TinkersNewlife.LOGGER.warn("TNL1052 throw FAILED: {}", t.toString());
                }
                tnl$throwHook(level, player);
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

    /** TNL1050 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u53ef\u4ee5\u5c06\u9570\u5200\u5411\u89c6\u7ebf\u65b9\u5411\u7529\u51fa\u53bb\uff08\u65e0\u89c6\u91cd\u529b\uff09\uff0c
     *   \u9570\u5200\u4e0a\u4f1a\u8fde\u7740\u4e00\u6839\u9ed1\u8272\u7684\u7ebf\uff0c\u5982\u679c\u547d\u4e2d\u65b9\u5757\uff0c\u4f1a\u5c06\u73a9\u5bb6\u62c9\u5230\u90a3\u4e2a\u4f4d\u7f6e\uff0c
     *   \u5982\u679c\u547d\u4e2d\u5b9e\u4f53\uff0c\u5219\u4f1a\u5c06\u5b9e\u4f53\u52fe\u8fc7\u6765" \u2713
     *
     * <p>\u2605\u672c\u7248\u5148\u7528"\u2605\u65e0\u91cd\u529b\u7684\u76f4\u7ebf\u5c04\u7ebf"\u505a\u884c\u4e3a \u2713\uff08\u2605\u76f4\u7ebf\u672c\u8eab\u5c31\u4e0d\u53d7\u91cd\u529b \u2713\uff09
     * \uff0c\u2605\u7ef3\u5b50\u5148\u7528\u9ed1\u8272\u7c92\u5b50\u7ebf\u8868\u793a \u2713\uff0c
     * \u2605\u4e0b\u4e00\u8f6e\u518d\u7167 `FlyingSwordTrailRenderer` \u6362\u6210\u4e13\u7528\u5e26\u72b6\u7ed8\u5236 \u2713\u3002
     */
    private static void tnl$throwHook(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = 24.0D;
        Vec3 end = eye.add(look.scale(reach));
        // \u2605\u5148\u770b\u5b9e\u4f53\uff08\u2605\u66f4\u8fd1\u5c31\u7b97\u547d\u4e2d \u2713\uff09
        net.minecraft.world.phys.EntityHitResult ehit = net.minecraft.world.entity.projectile.ProjectileUtil
                .getEntityHitResult(player, eye, end,
                        player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D),
                        en -> en instanceof LivingEntity && en != player && en.isAlive(), 0.3F);
        net.minecraft.world.phys.BlockHitResult bhit = level.clip(new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        double ed = ehit == null ? Double.MAX_VALUE : ehit.getLocation().distanceToSqr(eye);
        double bd = bhit == null ? Double.MAX_VALUE : bhit.getLocation().distanceToSqr(eye);
        Vec3 hit = ed < bd ? ehit.getLocation() : (bhit == null ? end : bhit.getLocation());
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
        if (ed < bd && ehit != null && ehit.getEntity() instanceof LivingEntity victim) {
            // \u2605\u547d\u4e2d\u5b9e\u4f53 \u21d2 \u2605\u628a\u5b83\u52fe\u8fc7\u6765 \u2713
            Vec3 dir = player.position().subtract(victim.position()).normalize().scale(1.9D);
            victim.setDeltaMovement(dir.x, 0.35D, dir.z);
            victim.hurtMarked = true;
            TinkersNewlife.LOGGER.warn("TNL1050 hook hit entity {}, pulled it in", victim.getName().getString());
        } else {
            // \u2605\u547d\u4e2d\u65b9\u5757 \u21d2 \u2605\u628a\u73a9\u5bb6\u62c9\u8fc7\u53bb \u2713
            PULLS.add(new Pull(player.getUUID(), hit, 12));
            TinkersNewlife.LOGGER.warn("TNL1050 hook hit block at {}, pulling the player", hit);
        }
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
