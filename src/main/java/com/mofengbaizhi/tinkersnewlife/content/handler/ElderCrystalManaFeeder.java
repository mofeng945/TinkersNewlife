package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import com.mofengbaizhi.tinkersnewlife.content.modifier.AllPathsOneTrait;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>古老者水晶 → Iron's Spellbooks 法力"供能"</b>（用户口径：放<b>副手 / 饰品</b>时为施法供能 ✓）。
 *
 * <h2>怎么对接（为什么是"每 tick 补"而不是"拦截扣蓝"）</h2>
 * 铁魔法是<b>纯反射软依赖</b>（{@code build.gradle} 不引用它 ✗）⇒ 想在"扣蓝那一行"插钩子只能写 mixin ✗；
 * 而本模组已经在用的 {@link IronSpellsSpellAccess} 已经能<b>读/写真实法力</b>（{@code MagicData#getMana/setMana}，
 * 反射核对过签名：{@code getMana():float} / {@code setMana(float)} ✓）。
 * 于是本节的做法是：
 * <ol>
 *   <li>只在玩家<b>拿着施法物品</b>（铁魔法 / 本模组法杖，见 {@link AllPathsOneTrait#holdingCastItem}）
 *       或<b>正在读条</b>（{@code MagicData#isCasting()}）时工作 ✓ —— 这就是"为施法供能"的语义边界 ✓；</li>
 *   <li>法力 <b>不满</b>时，从<b>主手 + 副手</b>的水晶里抽 EE 补进法力 ✓；
 *       <b>饰品与背包里的水晶都不参与</b> ✗（§520 用户口径：「拿在主手或副手才能供能」✓）；</li>
 *   <li>每 tick 最多抽 {@link #MAX_EE_PER_TICK} 点 EE（防止一瞬间抽干 ✗ 也给玩家反应时间 ✓）。</li>
 * </ol>
 *
 * <h2>汇率</h2>
 * {@code 1 EE = 1 法力} ✓（{@link EnergyUnits#MANA_PER_EE} —— 全模组唯一换算表 ✓ 这里不写裸数字 ✗）。
 * 所以"抽了多少 EE"就是"补了多少法力"，一眼能算 ✓。
 *
 * <h2>与「万法有道」的关系（不打架 ✓）</h2>
 * 「万法有道」（{@link AllPathsOneTrait}）走的是<b>咒力/灵魂</b>垫付法力，本节走的是<b>水晶 EE</b>，
 * 两者互不引用 ✗、也不会互相触发 ✗ —— 谁先谁后只影响"这 1 点法力最后是谁付的"，
 * 不会出现双重扣费 ✓（本节只碰水晶 NBT 与法力数值 ✓）。
 *
 * <p>全程 try/catch（fail-safe ✓）：没装铁魔法 / 反射失败 / 没有水晶 ⇒ 全部安静地什么都不做 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElderCrystalManaFeeder {

    private ElderCrystalManaFeeder() {}

    /**
     * 每 tick 最多抽多少 EE 换算成法力 —— 用户没点名 ⇒ 取的保守值 ✓
     * <p>20 EE/tick = 400 EE/秒 ⇒ 一颗满水晶（1000 EE）够连续供能 2.5 秒，
     * 一块水晶方块（4000 EE）够 10 秒 ✓（不会"一戴上就秒空" ✗）。
     * <p>⭐ <b>调参入口</b>：只改这一个数字。
     */
    public static final int MAX_EE_PER_TICK = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        try {
            // ① 铁魔法不在场 / 反射没就绪 ⇒ 什么都不做
            if (!IronSpellsSpellAccess.available()) return;

            // ② 只在"施法场景"工作（手上拿着施法物品 or 正在读条）
            if (!AllPathsOneTrait.holdingCastItem(player) && !IronSpellsSpellAccess.isCastingAny(player)) return;

            // ③ 读法力/上限（读不到 = -1 ⇒ 退出 ✓ 不猜 ✗）
            int mana = IronSpellsSpellAccess.manaOf(player);
            int max = IronSpellsSpellAccess.maxManaOf(player);
            if (mana < 0 || max <= 0) return;
            int missing = max - mana;
            if (missing <= 0) return;   // 满了 ⇒ 一点不动 ✓

            // ④ 1 EE = 1 法力（EnergyUnits 是唯一换算表 ✓），本 tick 最多 MAX_EE_PER_TICK EE
            int want = (int) Math.min(missing, MAX_EE_PER_TICK);
            int drained = ElderCrystalStorage.drainSuppliedEe(player, want);
            if (drained <= 0) return;   // 副手/饰品没有有电的水晶 ⇒ 不供能 ✓

            // ⑤ 真写回去（写的是"真实法力" ✓ 与万法有道同一口径：不虚报 ✗）
            //    EE→法力 一律经 EnergyUnits ✓（当前 1:1，但改了汇率这里自动跟着走 ✓）
            // ⑤ **走官方加法**（§542）：不要再 getMana+setMana 硬写 ✗ —— 那会打断铁魔法自己的回蓝记账
            //    （用户报：拿着水晶时法力不再自然回复 ✗）。官方 addMana 不在（老版本/反射失败）才退回旧写法 ✓
            float gain = (float) EnergyUnits.eeToMana(drained);
            if (!IronSpellsSpellAccess.addMana(player, gain)) {
                IronSpellsSpellAccess.setMana(player, mana + (int) Math.floor(gain));
            }
        } catch (Throwable ignored) {
            // fail-safe：可选内容出错绝不影响玩家 tick ✓
        }
    }
}
