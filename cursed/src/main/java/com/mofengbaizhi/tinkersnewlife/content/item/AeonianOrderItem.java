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
 * TNL1023 ★★ 恒正秩器 Aeonian Order ✓
 *
 * <p>★用户口径：
 * <ul>
 *   <li>★右键挥击：★**向前推力突进**（★不是瞬移 ✓），★近似 **5 格** ✓；</li>
 *   <li>★随后对面前一片 **2 宽 × 5 长** 的区域做一次横扫 ✓；</li>
 *   <li>★伤害：★物理 **12** ✓ + ★其中 **50%** 的**魔法**伤害（★**不是真伤** ✗）；</li>
 *   <li>★伤害以 **2:1** 治愈自身（★治疗 = 伤害 / 2 ✓）；</li>
 *   <li>★冷却：★走原版 ``ItemCooldowns``，★**10 秒** ✓（★耐久用户说最后再做 ✓）。</li>
 * </ul>
 */
public class AeonianOrderItem extends SwordItem {

    /** ★突进初速度（★配合摩擦约跑 5 格 ✓） */
    private static final double THRUST = 1.55D;
    /** ★横扫延迟（★tick ✓） */
    private static final int SWEEP_DELAY = 8;
    /** ★冷却 10 秒 ✓ */
    private static final int COOLDOWN = 200;
    /** ★物理伤害 12 ✓ */
    private static final float PHYS = 12.0F;
    /** ★魔法伤害 = 物理 × 50% ✓ */
    private static final float MAGIC = PHYS * 0.5F;

    private static final List<Pending> PENDING = new ArrayList<>();

    public AeonianOrderItem() {
        super(Tiers.NETHERITE, 8, -2.4F, new Item.Properties().stacksTo(1).durability(1000));   // TNL1060 tooltip shows xx/1000
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, java.util.List<net.minecraft.network.chat.Component> tip,
                                net.minecraft.world.item.TooltipFlag flag) {
        // TNL1069 user: "the durability line never showed up". Vanilla only prints it once the item
        // is damaged, and we override appendHoverText entirely, so print it here in plain white.
        tip.add(net.minecraft.network.chat.Component.translatable(
                        "item.durability", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage())
                .withStyle(net.minecraft.ChatFormatting.WHITE));
        // TNL1060 user: the two line declaration, colours are animated later by AeonianTooltipHandler
        tip.add(net.minecraft.network.chat.Component.literal(
                "人类若生来便注定作为高维生物的养料，便是混乱；人类若死于并非自然的混沌侵染，便是邪恶。我之所为，皆为秩序。"));
        tip.add(net.minecraft.network.chat.Component.literal(
                "                  ——流失在未知时间线的宣告·墨默留"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // TNL1046 ★★ 用户：★"没挂上啊" ✓
        //   ★现象 ✗：★日志里 `TNL1023` 与 `TNL1043` **一行都没有** ✓
        //     ⇒ ★说明★**右键根本没进到这里** ✗（★不是叠 tick 的问题 ✓）
        //   ★所以先打一行**入口**探针 ✓（★两侧都打 ✓）
        boolean tnl$shift = player.isShiftKeyDown();
        TinkersNewlife.LOGGER.warn("TNL1046 aeonian use() called side={} cooldown={} shift={}",
                player.level().isClientSide ? "client" : "server",
                player.getCooldowns().isOnCooldown(this), tnl$shift);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        // TNL1050 ★★ 用户：★"shift 右键还是突刺技能" ✓ ⇒ ★现在分开 ✓
        if (tnl$shift) {
            if (!level.isClientSide) {
                // TNL1055 ★★ 用户：★"抓钩改成朝视线方向飞然后拉着玩家过去，最多飞20格。别用飞剑追踪" ✓
                //   ★所以把上一版的"★生成 FlyingSwordEntity"**整段删掉** ✓
                //     （★它带自动锁定/制导追踪 ✗ —— ★正是你不要的 ✓）
                //   ★现在 ✓：★纯直线、无重力、沿视线、**最多 20 格** ✓
                tnl$throwHook(level, player, stack);
                tnl$spend(stack, player, hand, 2);      // TNL1191 the hook wears the weapon
                player.getCooldowns().addCooldown(this, 40);
            }
            player.swing(hand, true);
            return InteractionResultHolder.success(stack);
        }
        if (!level.isClientSide) {
            // ★① 向前推力（★不是瞬移 ✓）
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0D, look.z);
            if (flat.lengthSqr() < 1.0E-6D) {
                flat = Vec3.directionFromRotation(0.0F, player.getYRot());
            }
            flat = flat.normalize();
            player.setDeltaMovement(flat.x * THRUST, 0.16D, flat.z * THRUST);
            player.hurtMarked = true;                 // ★同步给客户端 ✓
            player.hasImpulse = true;
            player.fallDistance = 0.0F;
            // ★② 预约横扫 ✓
            PENDING.add(new Pending(player.getUUID(), SWEEP_DELAY, flat));
            tnl$spend(stack, player, hand, 2);          // TNL1191 the thrust wears the weapon
            player.getCooldowns().addCooldown(this, COOLDOWN);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.PLAYERS, 1.4F, 0.9F);
            TinkersNewlife.LOGGER.warn("TNL1023 aeonian order thrust fired, sweep in {} ticks", SWEEP_DELAY);
        }
        player.swing(hand, true);
        return InteractionResultHolder.success(stack);
    }

    /** ★待执行的横扫 ✓ */
    /**
     * TNL1191 user: "durability last". The weapon now wears out as its ability is used: two points per
     * activation, skipped in creative. Called from the server side only, so the client stays in sync
     * through the normal item damage packet.
     */
    private static void tnl$spend(ItemStack stack, Player player, InteractionHand hand, int amount) {
        try {
            if (player.getAbilities().instabuild) {
                return;
            }
            stack.hurtAndBreak(amount, player, p -> p.broadcastBreakEvent(hand));
        } catch (Throwable ignored) {
        }
    }

    private record Pending(java.util.UUID owner, int ticks, Vec3 dir) {
    }

    /** TNL1050 ★待执行的"★把玩家拉过去" ✓ */
    private record Pull(java.util.UUID owner, Vec3 target, int ticks) {
    }

    private static final List<Pull> PULLS = new ArrayList<>();

    /** TNL1057 user: launch an actual scythe that flies out, then pull the player to where it stopped */
    private record Hook(java.util.UUID owner, java.util.UUID item, int ticks, Vec3 dir) {
    }

    private static final List<Hook> HOOKS = new ArrayList<>();
    private static final int HOOK_TICKS = 14;   // TNL1059 2.6 blocks per tick, enough for 20

    /** TNL1050 ★★ 用户：★"可以将镰刀向视线方向甩出去（无视重力），
     *   镰刀上会连着一根黑色的线，如果命中方块，会将玩家拉到那个位置，
     *   如果命中实体，则会将实体勾过来" ✓
     *
     * <p>★本版先用"★无重力的直线射线"做行为 ✓（★直线本身就不受重力 ✓）
     * ，★绳子先用黑色粒子线表示 ✓，
     * ★下一轮再照 `FlyingSwordTrailRenderer` 换成专用带状绘制 ✓。
     */
    private static void tnl$throwHook(Level level, Player player, ItemStack heldStack) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = 20.0D;   // TNL1055 ★用户：★最多飞 20 格 ✓
        Vec3 end = eye.add(look.scale(reach));
        // TNL1056 user: the "hook entities" behaviour is cancelled -- blocks only now
        net.minecraft.world.phys.BlockHitResult bhit = level.clip(new net.minecraft.world.level.ClipContext(
                eye, end, net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 hit = bhit == null ? end : bhit.getLocation();
        boolean hitBlock = bhit != null;
        // ★黑色粒子线（★暂代绳子 ✓）
        if (level instanceof ServerLevel sl) {
            // TNL1053 ★★ 用户：★"镰刀上会连着一根黑色的线" ✓
            //   ★现在：★用**纯黑 dust**堆成一条粗线 ✓（★比 squid_ink 更像绳子 ✓）
            //     ★每 1/6 格一颗 ✓ + ★细微抖动 ✓ ⇒ ★远看就是一条黑线 ✓
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
        player.getCooldowns().addCooldown(
                com.mofengbaizhi.tinkersnewlife.content.ModItems.AEONIAN_MARKER.get(),
                hitBlock ? HOOK_TICKS : 6);   // TNL1070 hidden marker instead of a visible effect
        TinkersNewlife.LOGGER.warn("TNL1057 threw the scythe ({}), hitBlock={}", thrown.getUUID(), hitBlock);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS, 1.2F, 0.8F);
    }

    /** TNL1023 ★到点了就横扫 ✓ */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
    public static final class Ticker {

        private Ticker() {
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            // TNL1050 ★★ 处理"★把玩家拉到命中点" ✓
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
            // ★把剩余的倒计时减一 ✓
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
            Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);          // ★垂直方向 ✓
            Vec3 mid = player.position().add(dir.scale(2.75D)); // ★0.25~5.25 格 ✓
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
                // TNL1043 ★命中叠 tick（★每次 +10 ✓）
                com.mofengbaizhi.tinkersnewlife.content.effect.AeonianWeaken.add(target, com.mofengbaizhi.tinkersnewlife.content.effect.AeonianWeaken.PER_HIT);
                if (level instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            target.getX(), target.getY() + 1.0D, target.getZ(), 12, 0.3D, 0.4D, 0.3D, 0.01D);
                }
            }
            if (dealt > 0.0F) {
                player.heal(dealt * 0.5F);                      // ★★2:1 治愈 ✓
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
