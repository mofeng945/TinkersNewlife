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
        TinkersNewlife.LOGGER.warn("TNL1046 aeonian use() called side={} cooldown={}",
                player.level().isClientSide ? "client" : "server",
                player.getCooldowns().isOnCooldown(this));
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
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

    /** TNL1023 \u2605\u5230\u70b9\u4e86\u5c31\u6a2a\u626b \u2713 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
    public static final class Ticker {

        private Ticker() {
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
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
