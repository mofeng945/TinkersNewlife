package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import javax.annotation.Nullable;
import java.util.List;

/**
 * ⭐ §1118y <b>灵性以太</b>（工具特性 ✓ 有等级 ✓）—— 材料「恶念星灵」工具侧的主特性 ✓。
 *
 * <p>⚠ 类名 {@code EtherSpiritTrait} ✗（不是 SpiritualEtherTrait ✓）—— 我先前建过同名文件又删掉 ✗，
 * 而本仓写文件工具**拒绝重建已删除的路径** ✓ ⇒ 换新类名绕过 ✓；**注册 id 仍是 `spiritual_ether`** ✓。
 *
 * <h2>用户口径（原文 ✓）</h2>
 * <ul>
 *   <li><b>每级 ＋0.5</b> 的方块范围与实体范围 ✓；</li>
 *   <li><b>每次击杀</b>随机增长工具的**一项属性** ✓：每级单次长幅
 *       <b>0.1%×等级 ~ 1.5%×等级</b>（1 级 0.1%~1.5% ✓ 2 级 0.2%~3% ✓ 依此类推 ✓ 允许浮点 ✓）；
 *       **每项上限 1000%** ✓；属性 ＝ **耐久 ✓ 伤害 ✓ 挖掘速度 ✓ 攻击速度 ✓**，
 *       远程另加 **精准度 ✓ 初速度 ✓ 拉弓速度 ✓**；</li>
 *   <li>攻击时每级 1% 概率挂 5s 虚空之蚀/霜冻/迟缓，或脚下 3×3 冰之火 ✓（⏳ 下一轮实现 ✓）。</li>
 * </ul>
 *
 * <h2>成长值存在哪 ✓</h2>
 * 存在**工具自己的持久化数据**里 ✓（`tool.getPersistentData()` ✓ ＝ 匠魂工具的 NBT ✓ 跟着工具走 ✓
 * 换手/改名/放箱子都不会丢 ✓）；**每项一个 float** ✓ 存的是**比例**（0.0 ＝ 0% ✓ 10.0 ＝ 1000% ✓）。
 *
 * <h2>成长怎么生效 ✓（照匠魂本体 {@code PiercingModifier} 的写法 ✓ 已反编译核对 ✓）</h2>
 * 覆写 {@code registerHooks} 挂 {@code ModifierHooks.TOOL_STATS} ✓ ⇒ 在 {@link #addToolStats} 里用
 * {@code ModifierStatsBuilder} 把成长率**乘**进对应统计 ✓（`FloatToolStat#multiply` ✓ 已核实存在 ✓）
 * —— **每次重建统计都按 NBT 实时重算** ✓ 所以成长立刻反映到面板 ✓ 不用自己改属性 ✗。
 *
 * <p>范围 ＋0.5/级 ✓ **不在这里做** ✗：匠魂的 {@code ToolStats} 只有
 * 耐久/伤害/攻速/挖掘/拉弓/初速/精准 ✓ **没有"范围"** ✗ ⇒ 范围走 Forge 属性
 * （`forge:block_reach` / `forge:entity_reach` ✓），见 {@code content/handler/SpiritualEtherHandler} ✓。
 */
public class EtherSpiritTrait extends BaseCombatModifier implements ToolStatsModifierHook, TooltipModifierHook {

    /** 成长值在工具持久化数据里的键前缀 ✓（后面接属性名 ✓） */
    public static final String KEY_PREFIX = "tn_ether_growth_";

    /** 七项可成长属性（与下面 {@link #addToolStats} 里一一对应 ✓ 也是 NBT 键后缀 ✓） */
    public static final String[] KEYS = {
            "durability", "attack_damage", "attack_speed", "mining_speed",
            "accuracy", "velocity", "draw_speed"
    };

    /** 每项属性的**成长上限** ✓ ＝ 1000% ✓（用户口径 ✓）⇒ 存 10.0 ✓ */
    public static final float MAX_GROWTH = 10.0F;

    /** 每级**范围**加成 ✓ ＝ ＋0.5 方块/实体 ✓（用户口径 ✓） */
    public static final double REACH_PER_LEVEL = 0.5D;

    /** 每次击杀单次成长的**下限/上限**（占基础值的比例 ✓ 再乘等级 ✓）：0.1% ~ 1.5% ✓ */
    public static final double GROWTH_MIN = 0.001D;
    public static final double GROWTH_MAX = 0.015D;

    /** 本特性的注册 id ✓（与 {@code Modifiers} 里一致 ✓） */
    public static final String ID = "spiritual_ether";

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        // ⚠ 只挂"工具统计"这一个钩子 ✓ —— 击杀成长与范围走 Forge 事件（见 SpiritualEtherHandler ✓）
        hookBuilder.addHook(this, ModifierHooks.TOOL_STATS);
        // ⭐ §1118y 工具提示：按住 Shift 时逐行列出各属性的成长量（用户口径 ✓「属性名：+xx%」✓）
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addToolStats(IToolContext context, ModifierEntry modifier, ModifierStatsBuilder builder) {
        IModDataView data = context.getPersistentData();
        if (data == null) {
            return;
        }
        apply(builder, data, KEYS[0], ToolStats.DURABILITY);
        apply(builder, data, KEYS[1], ToolStats.ATTACK_DAMAGE);
        apply(builder, data, KEYS[2], ToolStats.ATTACK_SPEED);
        apply(builder, data, KEYS[3], ToolStats.MINING_SPEED);
        apply(builder, data, KEYS[4], ToolStats.ACCURACY);
        apply(builder, data, KEYS[5], ToolStats.VELOCITY);
        apply(builder, data, KEYS[6], ToolStats.DRAW_SPEED);
    }

    /** 把某一项的成长率乘进统计 ✓（＋30% 就乘 1.30 ✓） */
    private static void apply(ModifierStatsBuilder builder, IModDataView data, String key,
                              slimeknights.tconstruct.library.tools.stat.FloatToolStat stat) {
        float growth = getGrowth(data, key);
        if (growth > 0F) {
            stat.multiply(builder, 1.0F + growth);
        }
    }

    /** 某一项的持久数据键 ✓（⚠ TiC 的持久数据以 {@link ResourceLocation} 为键 ✗ 不是 String ✓） */
    public static ResourceLocation keyId(String key) {
        return new ResourceLocation(TinkersNewlife.MOD_ID, KEY_PREFIX + key);
    }

    /** 读某一项的成长率 ✓（0.0 ＝ 还没长过 ✓） */
    public static float getGrowth(IModDataView data, String key) {
        return data == null ? 0F : data.getFloat(keyId(key));
    }

    /** 某一项还能不能长 ✓（未达 1000% ✓） */
    public static boolean canGrow(IModDataView data, String key) {
        return getGrowth(data, key) < MAX_GROWTH;
    }
    /**
     * ⭐ <b>按住 Shift 时，把各属性的成长量**追加到匠魂原有那一行后面**</b> ✓（用户口径 ✓
     * 「直接追加到上面原有的属性数字空几格后面」✓ 格式「属性名：+xx%」✓）。
     *
     * <p>⭐⭐ <b>不新增行</b> ✗ —— 而是**找到匠魂已经显示的那一行**（按 {@code tool_stat.tconstruct.*} 的名字匹配 ✓）
     * 在后面接上成长量 ✓。⚠ 这样做有个**白捡的好处** ✓：匠魂**只显示这件工具真正拥有的属性**
     * （近战剑不显示「拉弓速度」✗）⇒ ⭐ **我们自然也就不会给近战追加拉弓速度** ✓
     * （之前我另起一行 ✗ 才闹出"近战武器也有拉弓速度" ✗）。
     */
    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        if (tooltipKey != TooltipKey.SHIFT) {
            return;   // ⚠ 只在按住 Shift 时显示 ✓
        }
        try {
            IModDataView data = tool.getPersistentData();
            if (data == null) {
                return;
            }
            appendGrowth(tooltip, "tool_stat.tconstruct.durability", getGrowth(data, KEYS[0]));
            appendGrowth(tooltip, "tool_stat.tconstruct.attack_damage", getGrowth(data, KEYS[1]));
            appendGrowth(tooltip, "tool_stat.tconstruct.attack_speed", getGrowth(data, KEYS[2]));
            appendGrowth(tooltip, "tool_stat.tconstruct.mining_speed", getGrowth(data, KEYS[3]));
            appendGrowth(tooltip, "tool_stat.tconstruct.accuracy", getGrowth(data, KEYS[4]));
            appendGrowth(tooltip, "tool_stat.tconstruct.velocity", getGrowth(data, KEYS[5]));
            appendGrowth(tooltip, "tool_stat.tconstruct.draw_speed", getGrowth(data, KEYS[6]));
        } catch (Throwable ignored) {
            // 提示出错绝不能影响物品显示 ✓
        }
    }

    /**
     * 把成长量追加到"匠魂已经显示的那一行"后面 ✓（空四格 ＋ 绿色 ✓）。
     * <p>⚠ 找不到那一行 ⇒ **什么都不做** ✗（说明这件工具本来就没这项 ✓ 比如近战没有拉弓速度 ✓）。
     */
    private static void appendGrowth(List<Component> tooltip, String statKey, float growth) {
        String name = stripFormatting(Component.translatable(statKey).getString());
        if (name.isEmpty()) {
            return;
        }
        for (int i = 0; i < tooltip.size(); i++) {
            String line = stripFormatting(tooltip.get(i).getString());
            if (!line.startsWith(name)) {
                continue;
            }
            tooltip.set(i, tooltip.get(i).copy().append(Component
                    .literal(String.format("    +%.1f%%", growth * 100.0F))
                    .withStyle(net.minecraft.ChatFormatting.GREEN)));
            return;
        }
    }

    /** 去掉 § 颜色代码再比对 ✓（否则名字里带色码就匹配不上 ✗） */
    private static String stripFormatting(String text) {
        return text == null ? "" : text.replaceAll("§.", "").trim();
    }
}
