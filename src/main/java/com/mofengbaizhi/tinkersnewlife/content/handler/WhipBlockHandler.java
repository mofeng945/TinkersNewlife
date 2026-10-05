package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipLashEntity;
import com.mofengbaizhi.tinkersnewlife.content.item.WhipItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>鞭子格挡 / 完美格挡</b>（§1058～§1061）—— 用户口径（2026-10-05）：
 * <blockquote>
 * 「<b>右键改为收回没有收回的鞭身并开启格挡，如果在开启格挡前后1s内受到攻击，判定为完美格挡并挥鞭将所有伤害反射出去，
 * 自身不受任何伤害。否则格挡时受到的伤害只会被减免40%</b>」
 * </blockquote>
 * <blockquote>
 * 「<b>挨打前时间窗改为0.5s，然后右键是长按的，完美格挡后解除use效果，玩家需要松开右键来重置</b>」
 * </blockquote>
 * <blockquote>
 * 「<b>格挡动画是举盾动画，不是举三叉戟动画</b>」＋「<b>格挡一次伤害会正常消耗耐久，挥鞭也会</b>」
 * </blockquote>
 *
 * <h2>实现要点</h2>
 * <ol>
 *   <li><b>格挡状态</b> ＝ "正举着鞭子"（{@link Player#isUsingItem()} ＋ 手持鞭 ＋ 未被松手锁挡住 ✓）——
 *       <b>姿势用 {@code UseAnim.BLOCK}（举盾 ✓ 用户口径 §1060 ✓）</b>；
 *       但原版 {@code LivingEntity#isBlocking()} **只看使用动画** ✗ ⇒ 会被当成盾牌**全额免伤** ✗
 *       ⇒ 由 {@link #onShieldBlock} **取消原版那次盾牌结算** ✓，伤害与耐久都改由本类接管 ✓。</li>
 *   <li><b>完美格挡窗口</b> ＝ {@link #PERFECT_WINDOW_TICKS} ＝ <b>0.5 秒</b> ✓：
 *       <ul>
 *         <li><b>举盾之后（挨打前）0.5 秒内</b>挨打 ⇒ {@link LivingHurtEvent} 里<b>取消该次伤害</b> ✓
 *             ＋ {@link #reflect} 把<b>全额</b>打回攻击者 ✓ ＋ 挥一鞭（视觉 ✓）；</li>
 *         <li><b>挨打之后 0.5 秒内</b>按下右键 ⇒ {@link #onBlockStarted} 把<b>那一次</b>的伤害<b>退回来</b>（回血 ✓）
 *             ＋ 同样反射 ✓。</li>
 *       </ul></li>
 *   <li><b>普通格挡</b> ⇒ 只减免 {@link #BLOCK_REDUCTION}（40% ✓）⇒ 玩家只吃 60% ✓。</li>
 *   <li><b>§1059 完美格挡后必须松手</b> ✓：完美格挡一旦生效就 {@link #endGuard} ⇒
 *       <b>立刻解除 use 效果</b> ✓ ＋ 上"松手锁" ✓；锁住期间每次 use 尝试都会续锁
 *       （{@link #noteBlockAttempt} ✓）⇒ <b>一直按住右键是举不起盾的</b> ✓；
 *       松开后 8 tick（0.4 秒）锁过期 ✓ ⇒ 再按即可重新举盾 ✓。</li>
 *   <li><b>§1061 耐久消耗</b> ✓（用户口径：「格挡一次伤害会正常消耗耐久，挥鞭也会」✓）：
 *       每次格挡（普通 ✓ 与完美 ✓）按<b>盾牌口径</b>扣耐久 ✓ ——
 *       {@code 1 + floor(受到的伤害)} ✓（{@link #consumeDurability} ✓）；
 *       挥鞭（左键抽击 ✓）在 {@code WhipItem#onEntitySwing} 里每次扣 1 ✓（原有逻辑 ✓）。</li>
 * </ol>
 *
 * <p>⚠ 与 §696 的"无下限完全格挡"等既有机制共存 ✓：本类只在**手持鞭子且正在举着**时介入 ✓，
 * 其余情况一律不动别人的伤害数值 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WhipBlockHandler {

    /** 完美格挡窗口（tick ✓）：<b>0.5 秒</b> ✓（用户口径：「挨打前时间窗改为 0.5s」✓） */
    public static final int PERFECT_WINDOW_TICKS = 10;
    /**
     * §1059 完美格挡后"<b>必须松手才能重置</b>"的锁时长（tick ✓，0.4 秒 ✓）——
     * 锁住期间每次 use 尝试都会把锁续上 ✓ ⇒ 只要还按着右键，锁永不过期 ✓（必须松手 ✓）。
     */
    public static final int RESET_HOLD_TICKS = 8;
    /** 普通格挡的减伤比例 ✓：40% ⇒ 只吃 60% ✓ */
    public static final float BLOCK_REDUCTION = 0.40F;

    /** "最近一次没格挡时挨的打" ✓ —— 供"挨打后 0.5 秒内举盾"的完美判定用 ✓ */
    private record RecentHit(int tick, float amount, UUID attacker) {
    }

    private static final Map<UUID, RecentHit> RECENT_HITS = new HashMap<>();
    /** 玩家 uuid → 松手锁到期 tick ✓ */
    private static final Map<UUID, Integer> NEEDS_RELEASE = new HashMap<>();

    private WhipBlockHandler() {
    }

    // ==================== 松手锁（§1059） ====================

    /** 是否被"完美格挡后的松手锁"挡住 ✓ */
    public static boolean isLocked(Player player) {
        Integer until = NEEDS_RELEASE.get(player.getUUID());
        return until != null && player.tickCount < until;
    }

    /** 能否开始举盾 ✓（完美格挡后必须先松手 ✓） */
    public static boolean canStartBlock(Player player) {
        return !isLocked(player);
    }

    /** 锁住期间的 use 尝试 ⇒ 续锁 ✓（保证"按住不放"永远举不起盾 ✓，必须松手 ✓） */
    public static void noteBlockAttempt(Player player) {
        if (isLocked(player)) {
            NEEDS_RELEASE.put(player.getUUID(), player.tickCount + RESET_HOLD_TICKS);
        }
    }

    /**
     * §1059 完美格挡结算完后：<b>立刻解除 use 效果</b> ✓（用户口径 ✓）并上锁 ⇒
     * <b>玩家必须松开右键来重置</b> ✓（用户口径 ✓）。
     */
    public static void endGuard(Player player) {
        NEEDS_RELEASE.put(player.getUUID(), player.tickCount + RESET_HOLD_TICKS);
        if (player.isUsingItem()) {
            player.stopUsingItem();
        }
    }

    // ==================== 格挡状态 ====================

    /** 是否正举着鞭子格挡 ✓（被松手锁挡住期间一律不算格挡 ✓） */
    public static boolean isWhipBlocking(Player player) {
        return !isLocked(player)
                && player.isUsingItem()
                && player.getUseItem().is(ModItems.WHIP.get());
    }

    /** 本次格挡已经持续了多少 tick ✓ —— 用"使用剩余时间"反推 ✓ 不需要额外状态 ✓ */
    public static int blockElapsedTicks(Player player) {
        ItemStack stack = player.getUseItem();
        if (stack.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        return stack.getUseDuration() - player.getUseItemRemainingTicks();
    }

    // ==================== 伤害侧 ====================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (isWhipBlocking(player)) {
            float incoming = event.getAmount();
            consumeDurability(player, incoming);          // §1061 挡一次就按盾牌口径扣耐久 ✓
            if (blockElapsedTicks(player) <= PERFECT_WINDOW_TICKS) {
                // 完美格挡 ⇒ 自身不受任何伤害 ✓ ＋ 全额反射 ✓ ＋ §1059 解除 use（必须松手重置 ✓）
                event.setCanceled(true);
                reflect(player, event.getSource(), incoming);
                endGuard(player);
            } else {
                // 普通格挡 ⇒ 只减免 40% ✓
                event.setAmount(incoming * (1.0F - BLOCK_REDUCTION));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.35F);
            }
            return;
        }
        // 没在格挡 ⇒ 记下这一击，供"挨打后 0.5 秒内举盾"的完美判定 ✓
        if (event.getAmount() > 0.0F) {
            RECENT_HITS.put(player.getUUID(),
                    new RecentHit(player.tickCount, event.getAmount(), attackerId(event.getSource())));
        }
    }

    /**
     * §1060 <b>取消原版那一次盾牌结算</b> ✓ —— 用户口径：「格挡动画是举盾动画」✓
     * ⇒ 鞭子用 {@code UseAnim.BLOCK}（举盾姿势 ✓），
     * 但原版 {@code LivingEntity#isBlocking()} **只看使用动画** ✗ ⇒ 从正面来的攻击会被原版**全额免伤** ✗，
     * "普通格挡只减 40%" 就永远不生效 ✗。
     * <p>⇒ 在 {@link ShieldBlockEvent} 里把它**取消** ✓：原版不做全免 ✓（耐久也改由
     * {@link #consumeDurability} 按盾牌口径自己扣 ✓），伤害继续按 {@link LivingHurtEvent} 里改好的数值结算 ✓
     * （完美格挡那次已在 {@code LivingHurtEvent} 里被取消 ✓ 根本到不了这里 ✓）。
     */
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (isWhipBlocking(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * 右键按下（开启格挡）时调用 ✓ —— "挨打后 {@link #PERFECT_WINDOW_TICKS} tick 内举盾"也算完美格挡 ✓。
     *
     * @return {@code true} ＝ 这次按右键触发了"退款型完美格挡" ✓ ⇒ 调用方<b>不要</b>再举盾 ✓
     *         （已经 {@link #endGuard} 上锁 ✓，必须松手重置 ✓）
     */
    public static boolean onBlockStarted(Player player) {
        RecentHit hit = RECENT_HITS.remove(player.getUUID());
        if (hit == null || player.tickCount - hit.tick() > PERFECT_WINDOW_TICKS) {
            return false;
        }
        consumeDurability(player, hit.amount());             // §1061 这次格挡同样扣耐久 ✓
        // 把那一次的伤害退回来 ✓（"自身不受任何伤害" ✓）
        player.heal(hit.amount());
        LivingEntity attacker = resolve(player, hit.attacker());
        if (attacker != null && attacker.isAlive()) {
            attacker.invulnerableTime = 0;
            attacker.hurt(player.damageSources().playerAttack(player), hit.amount());
        }
        WhipLashEntity.startLashNow(player);                 // 挥鞭（视觉 ✓）
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4F, 0.55F);
        endGuard(player);                                    // §1059 解除 use ＋ 必须松手重置 ✓
        return true;
    }

    /** 松开右键（正常结束格挡）时调用 ✓ —— 顺手清掉"最近受击"记录 ✓ */
    public static void onBlockEnded(Player player) {
        RECENT_HITS.remove(player.getUUID());
    }

    // ==================== 内部 ====================

    /**
     * §1061 <b>格挡消耗耐久</b> ✓（用户口径：「格挡一次伤害会正常消耗耐久」✓）——
     * 照<b>盾牌口径</b>：{@code 1 + floor(受到的伤害)} ✓（原版 {@code hurtCurrentlyUsedShield} 就是这么算的 ✓）。
     * <p>为什么自己扣 ✗：我们为了不让原版**全额免伤** ✗ 把整段盾牌结算取消了 ✓，
     * 那一份耐久也就一起没了 ✗ ⇒ 由这里补回来 ✓。
     */
    private static void consumeDurability(Player player, float blockedAmount) {
        ItemStack stack = player.getUseItem();
        if (stack.isEmpty()) {
            stack = player.getMainHandItem();
        }
        if (stack.isEmpty() || !(stack.getItem() instanceof WhipItem)) {
            return;
        }
        int amount = 1 + Mth.floor(Math.max(0.0F, blockedAmount));
        try {
            ToolDamageUtil.damageAnimated(ToolStack.from(stack), amount, player, player.getUsedItemHand());
        } catch (Throwable ignored) {
            // 工具数据读不到（创造栏里还没材料等 ✓）就算了 ✓
        }
    }

    /** 挥鞭并把<b>全额伤害</b>打回攻击者 ✓（用户口径：「挥鞭将所有伤害反射出去」✓） */
    private static void reflect(Player player, DamageSource source, float amount) {
        WhipLashEntity.startLashNow(player);                 // 挥鞭（视觉 ✓ 不受抽击冷却限制 ✓）
        LivingEntity attacker = resolve(player, attackerId(source));
        if (attacker != null && attacker != player && attacker.isAlive()) {
            attacker.invulnerableTime = 0;
            attacker.hurt(player.damageSources().playerAttack(player), amount);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.4F, 0.55F);
    }

    /** 取"该找谁算账" ✓：直接实体优先（弹射物则找它的主人 ✓） */
    private static UUID attackerId(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct != null && !(direct instanceof net.minecraft.world.entity.projectile.Projectile)) {
            return direct.getUUID();
        }
        Entity owner = source.getEntity();
        if (owner != null) {
            return owner.getUUID();
        }
        return direct == null ? null : direct.getUUID();
    }

    private static LivingEntity resolve(Player player, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        if (player.level() instanceof ServerLevel server) {
            Entity entity = server.getEntity(uuid);
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        Entity local = player.level().getPlayerByUUID(uuid);
        return local instanceof LivingEntity living ? living : null;
    }
}
