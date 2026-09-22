package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.ElderCrystalBlockEntity;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * <b>古老者水晶的"寒冷反噬"</b>（细雪同款 ✓ 用户口径 2026-09-21 / §532）。
 *
 * <h2>两个冷源（取和，不重复计同一块方块 ✓）</h2>
 * <ol>
 *   <li><b>随身水晶</b>：背包 / 副手 / 饰品里带着<b>有电</b>的水晶（{@link ElderCrystalStorage#totalCarriedEe}）✓；</li>
 *   <li><b>水晶方块</b>（§532 用户口径：「让古老者水晶方块在生物贴上或站上时给生物积攒寒冷 tick」✓）：
 *       取"**脚下那格**"与"**与生物判定框相交的方块**"两处 ⇒ **站在上面**、**贴着侧面**都算 ✓
 *       （用 {@link Set} 去重 ⇒ 站在方块顶时不会把同一块算两遍 ✗）；
 *       对**所有生物**生效（不只玩家 ✓ 怪物踩上去一样会冷 ✓ 它们身上没有水晶 ⇒ 只有方块这一路 ✓）。</li>
 * </ol>
 *
 * <h2>速率与上限（与原版细雪同款 ✓）</h2>
 * <pre>
 *   rate = ceil(2.0 × 已存EE / 1000)         // 1000 = 单颗水晶容量，方块按 4000 折算 ⇒ 满方块 = 8/tick
 *   上限 = getTicksRequiredToFreeze() = 140   // 到顶不再涨 ✓
 *   冻满后由原版自己结算：LivingEntity#aiStep 里 tickCount % 40 == 0 → hurt(freeze, 1.0F)（每 2 秒 1 点 ✓）
 * </pre>
 *
 * <h2>⚠ 为什么这里要"多写 2 点"（{@link #DECAY_COMPENSATION}）</h2>
 * 原版 {@code LivingEntity#aiStep}（反编译实锤 ✓ 1.20.1 官方映射）：
 * <pre>
 *   i = this.getTicksFrozen();
 *   if (this.isInPowderSnow &amp;&amp; this.canFreeze()) setTicksFrozen(min(getTicksRequiredToFreeze(), i + 1));
 *   else                                       setTicksFrozen(max(0, i - 2));      // ← 不在细雪里每 tick 掉 2
 * </pre>
 * 我们<b>不是</b>细雪 ⇒ 原版每 tick 都会先扣 2 点 ✗。若只加 {@code ceil(2×存量/容量)}：
 * 满水晶刚好 +2 / −2 ⇒ <b>净 0，永远冻不上</b> ✗。所以每次写回时补上这 2 点
 * （在细雪里时不补，因为那边是 +1 不是 −2）⇒ <b>净累积速率 = ceil(2.0 × 已存EE / 1000)</b> ✓。
 *
 * <h2>免疫：复用原版/整合包已有的防寒装备（不新做手套 ✓ 用户口径）</h2>
 * 一切写入前先问 {@link LivingEntity#canFreeze()} ✓ —— 原版它已经是
 * "头/胸/腿/脚 任一格穿了 {@code minecraft:freeze_immune_wearables} 里的东西就免疫" ✓。
 * 本整合包里这个标签已被好几家扩过：原版皮革四件、匠魂旅行者四件、通用机械机甲四件、诡厄霜袍 ✓
 * （铁魔法 {@code frostward_ring} 是**饰品**，而原版只查四个护甲格 ⇒ 实际免疫不了，属 ISS 既有状况，我们不替它打补丁 ✗）。
 *
 * <p>⚠ 全程 try/catch（fail-safe ✓）：可选内容出错绝不影响生物 tick ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElderCrystalColdHandler {

    private ElderCrystalColdHandler() {}

    /**
     * 满水晶（1000 EE）每 tick 累积的 frozen ticks —— 用户建议值 ✓
     * <p>折算：满水晶 2/tick ⇒ 70 tick（3.5 秒）冻满；4 个满水晶 / 一块满方块（4000 EE）8/tick ⇒ 18 tick 冻满。
     * <p>⭐ <b>调参入口</b>：改这一个数字即可（越大越冷）。
     */
    public static final double FROZEN_PER_TICK_AT_FULL = 2.0D;

    /**
     * §533 **"碰到就算"的外扩量（格）**：贴着方块站时，生物判定框的边界正好<b>等于</b>方块边界，
     * 而 {@code BlockPos.containing} 是向下取整 ⇒ 那一列会被排除掉 ✗（表现就是"贴着也不冷"✗）。
     * 外扩 0.1 格即可把"面接触/擦边"覆盖进来 ✓ 又不会把**隔一格**的方块算进来 ✗。
     */
    private static final double TOUCH_EPSILON = 0.1D;

    /** 抵消原版"不在细雪里每 tick −2"的衰减（见类注释 ✓ 别删 ✗ 删了满水晶就冻不上） */
    private static final int DECAY_COMPENSATION = 2;

    // ============================================================
    //  玩家：随身水晶 + 脚踏/贴着的水晶方块
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player == null || player.level() == null || player.level().isClientSide) return;
        try {
            int stored = ElderCrystalStorage.totalCarriedEe(player) + touchingBlockEe(player);
            chill(player, stored);
        } catch (Throwable ignored) {
            // fail-safe：出错就当作"这一 tick 不加冷" ✓ 绝不影响玩家 tick 流程
        }
    }

    // ============================================================
    //  其它生物：只有"站在 / 贴着水晶方块"这一路（它们身上没有水晶 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (living == null || living.level() == null || living.level().isClientSide) return;
        if (living instanceof Player) return;      // 玩家由上面那条处理（含随身水晶 ✓ 不重复 ✗）
        try {
            chill(living, touchingBlockEe(living));
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  核心：按"已存 EE"给 frozen ticks（细雪同款）
    // ============================================================

    private static void chill(LivingEntity living, int stored) {
        if (stored <= 0) return;                             // 空水晶 / 空方块 ⇒ 完全不冷 ✓
        if (!living.canFreeze()) return;                     // 免疫：完全交给原版标签 ✓
        int rate = (int) Math.ceil(FROZEN_PER_TICK_AT_FULL * stored
                / (double) ElderCrystalStorage.CRYSTAL_CAPACITY);
        if (rate <= 0) return;
        int comp = living.isInPowderSnow ? 0 : DECAY_COMPENSATION;
        int next = Math.min(living.getTicksRequiredToFreeze(), living.getTicksFrozen() + rate + comp);
        if (next > living.getTicksFrozen()) living.setTicksFrozen(next);
        // 冻满后的伤害/减速/冻结外观全部由原版 aiStep + baseTick 自己结算 ✓ 不再插手 ✗
    }

    // ============================================================
    //  方块侧：脚下 + 与判定框相交的水晶方块，EE 之和（去重 ✓）
    // ============================================================

    /**
     * 生物**站上 / 贴上**的古老者水晶方块里的 EE 之和 ✓（§532 用户口径）。
     * <p>两个来源：①**脚下那一格**（站在方块顶上时它并不在判定框内 ⇒ 必须显式带上 ✗ 不然"站上去"就不冷 ✗）；
     * ②**与判定框相交的方块**（贴着侧面、半个身子嵌进去都算 ✓）。
     * <p>用 {@link Set} 去重 ⇒ 站上去时不会把同一块算两遍（那会双倍变冷 ✗）。
     */
    private static int touchingBlockEe(LivingEntity living) {
        if (living.level() == null) return 0;
        Set<BlockPos> seen = new HashSet<>();
        int total = 0;
        BlockPos feet = living.blockPosition();
        // ① 脚下那格（站台上）
        total += blockEeAt(living, feet.below(), seen);
        // ② 判定框覆盖到的格子（贴侧面 / 嵌进去）
        // §533 外扩 TOUCH_EPSILON ⇒ "贴着/碰到方块"也算 ✓（原来只算"确实嵌进方块那一列"✗）
        var box = living.getBoundingBox().inflate(TOUCH_EPSILON);
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            total += blockEeAt(living, pos, seen);
        }
        return total;
    }

    private static int blockEeAt(LivingEntity living, BlockPos pos, Set<BlockPos> seen) {
        BlockPos key = pos.immutable();
        if (!seen.add(key)) return 0;                        // 去重 ✓
        return living.level().getBlockEntity(key) instanceof ElderCrystalBlockEntity be ? be.getEe() : 0;
    }
}
