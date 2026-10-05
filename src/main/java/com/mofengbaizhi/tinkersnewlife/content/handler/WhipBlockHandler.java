package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.entity.WhipLashEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>鞭子格挡 / 完美格挡</b>（§1058）—— 用户口径（2026-10-05）：
 * <blockquote>
 * 「<b>右键改为收回没有收回的鞭身并开启格挡，如果在开启格挡前后1s内受到攻击，判定为完美格挡并挥鞭将所有伤害反射出去，
 * 自身不受任何伤害。否则格挡时受到的伤害只会被减免40%</b>」
 * </blockquote>
 *
 * <h2>实现要点</h2>
 * <ol>
 *   <li><b>格挡状态</b> ＝ "正举着鞭子"（{@link Player#isUsingItem()} ＋ 手持鞭 ✓）——
 *       ⚠ <b>故意不用 {@code UseAnim.BLOCK}</b> ✗：原版 {@code LivingEntity#isBlocking()} **只看使用动画** ✗
 *       ⇒ 任何用 BLOCK 动画的物品都会被原版当成盾牌**全额免伤** ✗，那样"只减 40%"就永远不生效 ✗。
 *       所以鞭子用 {@code UseAnim.SPEAR}（举械防御的姿势 ✓），伤害结算**全部由本类接管** ✓。</li>
 *   <li><b>完美格挡</b>（窗口 {@link #PERFECT_WINDOW_TICKS} ＝ 1 秒 ✓）：
 *       <ul>
 *         <li><b>开格挡后 1 秒内</b>挨打 ⇒ 在 {@link LivingHurtEvent} 里<b>取消该次伤害</b> ✓
 *             ＋ {@link #reflect} 把<b>全额</b>打回攻击者 ✓ ＋ 挥一鞭（视觉 ✓）；</li>
 *         <li><b>开格挡前 1 秒内</b>挨过打 ⇒ 右键按下那一刻（{@link #onBlockStarted} ✓）
 *             把<b>那一次</b>的伤害<b>退回来</b>（回血 ✓）＋ 同样反射 ✓ —— 这就是"前后 1 秒"的完整口径 ✓。</li>
 *       </ul></li>
 *   <li><b>普通格挡</b> ⇒ 只减免 {@link #BLOCK_REDUCTION}（40% ✓）⇒ 玩家只吃 60% ✓。</li>
 * </ol>
 *
 * <p>⚠ 与 §696 的"无下限完全格挡"等既有机制共存 ✓：本类只在**手持鞭子且正在举着**时介入 ✓，
 * 其余情况一律不动别人的伤害数值 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WhipBlockHandler {

    /** 完美格挡窗口（tick ✓）：1 秒 ✓（用户口径「开启格挡前后 1s 内」✓） */
    public static final int PERFECT_WINDOW_TICKS = 20;
    /** 普通格挡的减伤比例 ✓：40% ⇒ 只吃 60% ✓ */
    public static final float BLOCK_REDUCTION = 0.40F;

    /** "最近一次没格挡时挨的打" ✓ —— 供"开格挡前 1 秒"的完美判定用 ✓ */
    private record RecentHit(int tick, float amount, UUID attacker) {
    }

    private static final Map<UUID, RecentHit> RECENT_HITS = new HashMap<>();

    private WhipBlockHandler() {
    }

    /** 是否正举着鞭子格挡 ✓ */
    public static boolean isWhipBlocking(Player player) {
        return player.isUsingItem() && player.getUseItem().is(ModItems.WHIP.get());
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
            if (blockElapsedTicks(player) <= PERFECT_WINDOW_TICKS) {
                // 完美格挡 ⇒ 自身不受任何伤害 ✓ ＋ 全额反射 ✓
                event.setCanceled(true);
                reflect(player, event.getSource(), event.getAmount());
            } else {
                // 普通格挡 ⇒ 只减免 40% ✓
                event.setAmount(event.getAmount() * (1.0F - BLOCK_REDUCTION));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.7F, 1.35F);
            }
            return;
        }
        // 没在格挡 ⇒ 记下这一击，供"开格挡前 1 秒内"的完美判定 ✓
        if (event.getAmount() > 0.0F) {
            RECENT_HITS.put(player.getUUID(),
                    new RecentHit(player.tickCount, event.getAmount(), attackerId(event.getSource())));
        }
    }

    /** 右键按下（开启格挡）时调用 ✓ —— "开格挡**前** 1 秒内挨过打"也算完美格挡 ✓ */
    public static void onBlockStarted(Player player) {
        RecentHit hit = RECENT_HITS.remove(player.getUUID());
        if (hit == null || player.tickCount - hit.tick() > PERFECT_WINDOW_TICKS) {
            return;
        }
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
    }

    /** 松手（结束格挡）时调用 ✓ —— 清掉记录，免得留到下一次 ✓ */
    public static void onBlockEnded(Player player) {
        RECENT_HITS.remove(player.getUUID());
    }

    // ==================== 内部 ====================

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

    /** 取"该找谁算账" ✓：直接实体优先 ✓ 否则来源实体 ✓ */
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
