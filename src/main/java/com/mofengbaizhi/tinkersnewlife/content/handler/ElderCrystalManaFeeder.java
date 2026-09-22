package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import com.mofengbaizhi.tinkersnewlife.content.energy.EnergyUnits;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * <b>手持古老者水晶 ⇒ 一切"要花能量"的行为都**优先花 EE**</b>（用户口径 2026-09-22 ✓）。
 *
 * <h2>口径</h2>
 * 「手持水晶时，任何消耗<b>法力 / 咒力 / 灵魂能量</b>的行为都会优先消耗 EE」✓
 * —— 也就是说：EE 是**第一付款人**，自己的三个池子只是"暂存/上限"，不够了才轮到它们 ✓。
 *
 * <h2>怎么实现（为什么是"监视下降"而不是"按上限顶满"）</h2>
 * 三个池子的<b>上限</b>我们并不都能拿到（灵魂能量走 Goety 的反射桥，只有 get/add/decrease ✗），
 * 而"按上限顶满"还有两个毛病 ✗：① 池子因为<b>别的原因</b>没满（上限变化、换维度）时会白烧 EE ✗；
 * ② 会破坏玩家自己攒下来的资源手感 ✗。
 * <p>所以改成<b>监视每一 tick 的下降量</b>：某个池子这一 tick 比上一 tick <b>少了</b> ⇒ 说明"刚刚被花掉了" ✓
 * ⇒ 用 EE 把这份缺口<b>补回来</b>（并记下新的基准）✓
 * <ul>
 *   <li><b>不消耗就完全不动</b> ✓（池子满着、在自然回复都不碰 EE ✓）；</li>
 *   <li>每 tick 补偿量有上限 {@link #MAX_COVER_EE_PER_TICK} ⇒ 换维度/重生导致池子被重置时只会<b>缓慢回填</b>，
 *       不会一口气烧掉整块水晶 ✓（而且"第一次见到该玩家"只记基准、不补偿 ✓）；</li>
 *   <li>三个池子的换算全部经 {@link EnergyUnits}（唯一换算表 ✓）：<b>1 EE = 10 法力 = 20 咒力 = 40 灵魂</b> ✓。</li>
 * </ul>
 *
 * <h2>顺序与"施法前"的注意点（诚实记）</h2>
 * 补偿发生在**花掉之后的同一 tick 末尾**（PlayerTick END ✓）⇒ 对"一次性大额消耗"来说，
 * 实际顺序仍是"先扣自己的、再由 EE 补回"，只是<b>净值等于 EE 付的</b> ✓。
 * ⚠ 若某次施法要求<b>单次</b>扣掉超过你当前法力（或咒力）的量，铁魔法/我们自己的判定会在扣款前就失败 ✗
 * （那一刻 EE 来不及垫 ✓）—— 这是唯一达不到"严格优先"的情形 ✓（真需要就得在"开始施法"那一刻预充 ✓）。
 *
 * <p>全程 try/catch（fail-safe ✓）：没装铁魔法 / 没有诡厄 / 反射失败 ⇒ 安静地什么都不做 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ElderCrystalManaFeeder {

    private ElderCrystalManaFeeder() {}

    /**
     * 每 tick 最多用多少 EE 去补偿三个池子 —— 防"池子被重置"时一次性抽干水晶 ✗。
     * <p>200 EE/tick = 4000 EE/秒（按 1 EE = 10 法力算就是 2000 法力/秒 ✓ 远超正常消耗 ✓；
     * 而换维度那种"整池重置"最多也就几千点 ⇒ 一两个 tick 内补完 ✓）。
     * <p>⭐ <b>调参入口</b>：只改这一个数字。
     */
    public static final int MAX_COVER_EE_PER_TICK = 200;

    /** 上一 tick 观察到的三个池子读数（按玩家 UUID ✓ 下线时清掉 ✗ 不残留） */
    private static final Map<UUID, double[]> LAST = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        try {
            double mana = IronSpellsSpellAccess.available() ? IronSpellsSpellAccess.manaOf(player) : -1;
            double curse = CursePowerHelper.getCurse(player);
            double souls = SoulEnergyBridge.getSouls(player);

            double[] prev = LAST.get(player.getUUID());
            LAST.put(player.getUUID(), new double[] { mana, curse, souls });
            if (prev == null) return;          // 第一次只记基准 ✓（否则"刚上线/刚换维度"会被当成一次巨额消耗 ✗）
            if (!player.isAlive()) return;

            // 只有"手里真的拿着有电的水晶"才付款 ✓（§520：主手/副手 ✓ 背包/饰品不算 ✓）
            if (ElderCrystalStorage.suppliedEe(player) <= 0) return;

            int budget = MAX_COVER_EE_PER_TICK;

            // ① 法力：1 EE = MANA_PER_EE 点
            if (mana >= 0 && prev[0] >= 0 && mana < prev[0]) {
                budget = cover(player, budget, prev[0] - mana, EnergyUnits.MANA_PER_EE, gained -> {
                    if (gained > 0) IronSpellsSpellAccess.addMana(player, (float) gained);
                });
            }
            // ② 咒力：1 EE = CURSE_PER_EE 点
            if (curse < prev[1]) {
                budget = cover(player, budget, prev[1] - curse, EnergyUnits.CURSE_PER_EE, gained -> {
                    if (gained > 0) CursePowerHelper.addCurse(player, gained);
                });
            }
            // ③ 灵魂能量：1 EE = SOULS_PER_EE 点
            if (souls < prev[2]) {
                int need = (int) Math.ceil(prev[2] - souls);
                int wantEe = (int) Math.ceil(need / EnergyUnits.SOULS_PER_EE);
                int drained = ElderCrystalStorage.drainSuppliedEe(player, Math.min(budget, Math.max(0, wantEe)));
                int gained = (int) Math.floor(drained * EnergyUnits.SOULS_PER_EE);
                if (gained > 0) SoulEnergyBridge.addSouls(player, gained);
            }
        } catch (Throwable ignored) {
            // fail-safe：可选内容出错绝不影响玩家 tick ✓
        }
    }

    /** 小工具：按"缺口 ÷ 汇率"抽 EE，并把换算后的资源加回去（法力/咒力共用 ✓） */
    private static int cover(ServerPlayer player, int budget, double missing, double perEe,
                             java.util.function.DoubleConsumer apply) {
        if (missing <= 0 || perEe <= 0) return budget;
        int wantEe = (int) Math.ceil(missing / perEe);
        int spend = Math.min(budget, wantEe);
        if (spend <= 0) return budget;
        int drained = ElderCrystalStorage.drainSuppliedEe(player, spend);
        if (drained <= 0) return budget;
        apply.accept(drained * perEe);
        return budget - drained;
    }

    /** 玩家下线时清掉基准 ✓（避免下次进来把"池子重置"误判成消耗 ✗） */
    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) LAST.remove(event.getEntity().getUUID());
    }
}
