package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>古老者水晶的"寒冷反噬"</b>（细雪同款 ✓ 用户口径 2026-09-21）。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>身上（<b>背包 / 副手 / 饰品</b>）带着<b>有电</b>的水晶时，每 tick 累积
 *       {@code ceil(2.0 × 已存EE / 1000)} 点 frozen ticks（{@link #FROZEN_PER_TICK_AT_FULL}）；
 *       <b>空水晶完全不冷</b> ✓（存量为 0 ⇒ 直接不做事 ✓）；</li>
 *   <li>到上限（{@link net.minecraft.world.entity.Entity#getTicksRequiredToFreeze()} = <b>140</b>）就不再涨 ✓
 *       —— 与细雪一致 ✓；</li>
 *   <li>冻满之后<b>直接吃原版冻结伤害</b>：{@code LivingEntity#aiStep} 里那句
 *       {@code tickCount % 40 == 0 && isFullyFrozen() && canFreeze() → hurt(damageSources().freeze(), 1.0F)}
 *       （每 2 秒 1 点 ✓ 自己一行都不用写 ✗ 也绝不会重复扣 ✗）。</li>
 * </ul>
 *
 * <h2>⚠ 为什么这里要"多写 2 点"（{@link #DECAY_COMPENSATION}）</h2>
 * 原版 {@code LivingEntity#aiStep}（反编译实锤 ✓ 1.20.1 官方映射）：
 * <pre>
 *   i = this.getTicksFrozen();
 *   if (this.isInPowderSnow &amp;&amp; this.canFreeze()) setTicksFrozen(min(getTicksRequiredToFreeze(), i + 1));
 *   else                                       setTicksFrozen(max(0, i - 2));      // ← 不在细雪里每 tick 掉 2
 * </pre>
 * 我们<b>不是</b>细雪 ⇒ 原版每 tick 都会先扣 2 点 ✗。若只加 {@code ceil(2×存量/容量)}：
 * 满水晶刚好 +2 / −2 ⇒ <b>净 0，永远冻不上</b> ✗（这就白做了）。
 * 所以每次写回时补上这 2 点（在细雪里时不补，因为那边是 +1 不是 −2）⇒
 * <b>净累积速率 = ceil(2.0 × 已存EE / 1000)</b> ✓ 与用户给的公式完全一致 ✓。
 * <p>数字来源：`getTicksRequiredToFreeze() = 140`、衰减 `-2`、
 * 冻伤 `tickCount % 40 == 0 → 1.0F` —— 全部由 CFR 反编译
 * {@code forge-1.20.1-47.4.22_mapped_official} 的 {@code Entity.java} / {@code LivingEntity.java} 核对 ✓。
 *
 * <h2>免疫：<b>复用原版/整合包已有的防寒装备</b>（不新做手套 ✓）</h2>
 * 一切写入前先问 {@link net.minecraft.world.entity.LivingEntity#canFreeze()} ✓ ——
 * 原版它已经是"<b>头/胸/腿/脚 任一格穿了 {@code minecraft:freeze_immune_wearables} 里的东西就免疫</b>"✓
 * （反编译实锤：{@code LivingEntity#canFreeze()} 逐格查 {@code ItemTags.FREEZE_IMMUNE_WEARABLES}）。
 * 本整合包里这个标签已经被好几家扩过（逐一读过 mods/*.jar 里的 tag 文件 ✓）：
 * <pre>
 *   原版          : 皮革四件 + 皮革马铠
 *   匠魂 TConstruct: 旅行者四件套（travelers_*）
 *   通用机械 Mekanism: 机甲四件（mekasuit_*）
 *   诡厄巫法 Goety : goety:frost_robe（霜袍）
 *   铁魔法 Iron's  : irons_spellbooks:frostward_ring（霜御指环）
 * </pre>
 * ⇒ <b>不设任何自创免疫、不做抗寒手套</b> ✓（用户口径）；谁想免疫就穿上面这些 ✓。
 * <p>⚠ 诚实记一笔：{@code frostward_ring} 是<b>饰品</b>，而原版 {@code canFreeze()} 只查
 * 头/胸/腿/脚四格 ⇒ 单靠这个标签它在原版逻辑里<b>其实免疫不了</b>（这是 ISS 那边的既有状况，
 * 与本模组无关 ✓ 我们<b>不</b>去替它打补丁 ✗）。
 *
 * <p>⚠ 全程 try/catch（fail-safe ✓）：可选内容出错绝不影响玩家 tick ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElderCrystalColdHandler {

    private ElderCrystalColdHandler() {}

    /**
     * 满水晶（1000 EE）每 tick 累积的 frozen ticks —— 用户建议值 ✓
     * <p>折算：满水晶 2/tick ⇒ 70 tick（3.5 秒）冻满；4 个满水晶（4000 EE）8/tick ⇒ 18 tick 冻满。
     * <p>⭐ <b>调参入口</b>：改这一个数字即可（越大越冷）。
     */
    public static final double FROZEN_PER_TICK_AT_FULL = 2.0D;

    /** 抵消原版"不在细雪里每 tick −2"的衰减（见类注释 ✓ 别删 ✗ 删了满水晶就冻不上） */
    private static final int DECAY_COMPENSATION = 2;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player == null || player.level() == null || player.level().isClientSide) return;
        try {
            // ① 免疫：完全交给原版（防寒装备标签 ⇒ canFreeze()=false）
            if (!player.canFreeze()) return;

            // ② 存量：背包 + 副手 + 饰品（空水晶贡献 0 ⇒ 空水晶完全不冷 ✓）
            int stored = ElderCrystalStorage.totalCarriedEe(player);
            if (stored <= 0) return;

            // ③ 累积速率（用户公式：ceil(2.0 × 已存 / 容量)）—— 容量取单颗水晶的 1000
            int rate = (int) Math.ceil(FROZEN_PER_TICK_AT_FULL * stored
                    / (double) ElderCrystalStorage.CRYSTAL_CAPACITY);
            if (rate <= 0) return;

            // ④ 写 frozen ticks：到上限就不再涨（与原版细雪同一条上限 ✓）
            int comp = player.isInPowderSnow ? 0 : DECAY_COMPENSATION;
            int next = Math.min(player.getTicksRequiredToFreeze(), player.getTicksFrozen() + rate + comp);
            if (next > player.getTicksFrozen()) player.setTicksFrozen(next);
            // 冻满后的伤害/减速/冻结外观全部由原版 aiStep + baseTick 自己结算 ✓ 不再插手 ✗
        } catch (Throwable ignored) {
            // fail-safe：出错就当作"这一 tick 不加冷" ✓ 绝不影响玩家 tick 流程
        }
    }
}
