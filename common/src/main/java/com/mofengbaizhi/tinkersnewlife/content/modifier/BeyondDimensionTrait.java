package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.build.ToolStatsModifierHook;
import slimeknights.tconstruct.library.tools.nbt.IModDataView;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.stat.FloatToolStat;
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
 * ⭐ §1118y <b>超越维度</b>（盔甲特性 ✓ 有等级 ✓）—— 材料「恶念星灵」护甲侧主特性 ✓。
 *
 * <p>⚠ 类名 {@code BeyondDimensionTrait} ✗（不是 TranscendentDimensionTrait ✓）—— 同样是
 * "写文件工具拒绝重建我删过的同名路径" ✓；**注册 id 仍是 `transcendent_dimension`** ✓。
 *
 * <h2>用户口径（原文 ✓）</h2>
 * <ul>
 *   <li>与**灵性以太**类似 ✓：盔甲的 <b>耐久 ✓ 护甲值 ✓ 盔甲韧性</b>随**击杀**增长 ✓
 *       每级长幅与灵性以太相同（**0.1%×等级 ~ 1.5%×等级** ✓ 随机一项 ✓），**每项上限 1000%** ✓；</li>
 *   <li>此外随击杀获得**护甲减伤之后的再一次全类型伤害减免** ✓：初始 0 ✓
 *       **每级每次击杀 ＋0.05%** ✓ **最高 80%** ✓；</li>
 *   <li>⭐ **全身盔甲总等级 ≥ 4** 时免除七咒之戒全部诅咒 ＋ 提示划线追加粉色文本 ✓
 *       ＋ 该盔甲**死亡不掉落 / 不被岩浆仙人掌销毁 / 不可被其他生物穿戴** ✓（见
 *       {@code content/handler/TranscendentDimensionHandler} 与 {@code SevenCursesWaiverHandler} ✓）。</li>
 * </ul>
 *
 * <h2>数值怎么生效 ✓（与灵性以太同一套已验证写法 ✓）</h2>
 * 覆写 {@code registerHooks} 挂 {@code ModifierHooks.TOOL_STATS} ✓ ⇒ 在 {@link #addToolStats} 里用
 * {@code ModifierStatsBuilder} 把成长率**乘**进 `DURABILITY` / `ARMOR` / `ARMOR_TOUGHNESS` ✓
 * （⚠ 护甲统计名是**反编译核实**的 ✓：`ToolStats.ARMOR` ✓ `ToolStats.ARMOR_TOUGHNESS` ✓）。
 *
 * <p>全类型减伤（DR ✓）**不在这里做** ✗ —— 它不是"工具统计" ✓ 而是**伤害结算** ✓
 * ⇒ 走 {@code LivingHurtEvent} ✓（见处理器 ✓），DR 值同样存在护甲自己的持久化数据里 ✓。
 */
public class BeyondDimensionTrait extends BaseCombatModifier implements ToolStatsModifierHook, TooltipModifierHook {

    /** 成长值键前缀 ✓（⚠ 与灵性以太**分开** ✓ 免得两套混用 ✓） */
    public static final String KEY_PREFIX = "tn_dim_growth_";

    /** 三项可成长属性 ✓（NBT 键后缀 ✓） */
    public static final String[] KEYS = {"durability", "armor", "toughness"};

    /** 每项成长上限 ✓ ＝ 1000% ✓（用户口径 ✓）⇒ 存 10.0 ✓ */
    public static final float MAX_GROWTH = 10.0F;

    /** 全类型减伤的持久数据键 ✓（存比例 ✓ 0.0~0.80 ✓） */
    public static final String KEY_DR = "tn_dim_dr";

    /** 每次击杀 DR 增量 ✓：每级 **0.05%** ✓（用户口径 ✓） */
    public static final float DR_PER_LEVEL_PER_KILL = 0.0005F;

    /** DR 上限 ✓ ＝ **80%** ✓（用户口径 ✓） */
    public static final float DR_MAX = 0.80F;

    /** 单次成长长幅区间 ✓（与灵性以太相同 ✓）：0.1% ~ 1.5% ✓ 再乘等级 ✓ */
    public static final double GROWTH_MIN = 0.001D;
    public static final double GROWTH_MAX = 0.015D;

    /** 本特性的注册 id ✓ */
    public static final String ID = "transcendent_dimension";

    /** 全身总等级达到此值 ⇒ 免除七咒 ✓（用户口径 ✓「大于等于 4」✓） */
    public static final int WAIVER_TOTAL_LEVEL = 4;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOL_STATS);
        // ⭐ §1118y 工具提示：按住 Shift 时逐行列出成长量与全类型减伤（用户口径 ✓「属性名：+xx%」✓）
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addToolStats(IToolContext context, ModifierEntry modifier, ModifierStatsBuilder builder) {
        IModDataView data = context.getPersistentData();
        if (data == null) {
            return;
        }
        // ⚠ 三项成长都"乘"进去 ✓（每次重建统计都按 NBT 实时重算 ✓）
        float growth = getGrowth(data, KEYS[0]);
        if (growth > 0F) {
            ToolStats.DURABILITY.multiply(builder, 1.0F + growth);
        }
        growth = getGrowth(data, KEYS[1]);
        if (growth > 0F) {
            ToolStats.ARMOR.multiply(builder, 1.0F + growth);
        }
        growth = getGrowth(data, KEYS[2]);
        if (growth > 0F) {
            ToolStats.ARMOR_TOUGHNESS.multiply(builder, 1.0F + growth);
        }
    }

    /** 某一项的持久数据键 ✓（⚠ 匠魂持久数据以 {@link ResourceLocation} 为键 ✗ 不是 String ✓） */
    public static ResourceLocation keyId(String key) {
        return new ResourceLocation(TinkersNewlife.MOD_ID, KEY_PREFIX + key);
    }

    /** 全类型减伤的键 ✓ */
    public static ResourceLocation drKey() {
        return new ResourceLocation(TinkersNewlife.MOD_ID, KEY_DR);
    }

    /** 读某项成长率 ✓（⚠ 单独暴露给处理器用 ✓ 参数用 {@link IModDataView} 只读 ✓） */
    public static float growthOf(IModDataView data, String key) {
        return data == null ? 0F : data.getFloat(keyId(key));
    }

    /** 读这项成长率 ✓ @deprecated 别名，保持与灵性以太一致的命名习惯 ✓ */
    public static float getGrowth(IModDataView data, String key) {
        return growthOf(data, key);
    }

    /** 还能不能长 ✓（未达 1000% ✓） */
    public static boolean canGrow(IModDataView data, String key) {
        return growthOf(data, key) < MAX_GROWTH;
    }

    /** 静态工具方法：三项统计的取用 ✓（供处理器按名匹配 ✓） */
    public static FloatToolStat statOf(String key) {
        if (KEYS[0].equals(key)) {
            return ToolStats.DURABILITY;
        }
        if (KEYS[1].equals(key)) {
            return ToolStats.ARMOR;
        }
        return ToolStats.ARMOR_TOUGHNESS;
    }
    /**
     * ⭐ <b>按住 Shift 时把成长量追加到匠魂原有那一行后面</b> ✓（用户口径 ✓ 与工具侧同一套 ✓）；
     * ⭐ **全类型减伤**匠魂没有对应行 ✗ ⇒ 它**单开一行** ✓（那是新信息 ✓ 不算冗余 ✓）。
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
            appendGrowth(tooltip, "tool_stat.tconstruct.durability", growthOf(data, KEYS[0]));
            appendGrowth(tooltip, "tool_stat.tconstruct.armor", growthOf(data, KEYS[1]));
            appendGrowth(tooltip, "tool_stat.tconstruct.armor_toughness", growthOf(data, KEYS[2]));
            // ⭐ 护甲减伤之后的那一次全类型减伤 ✓（用户口径 ✓ 上限 80% ✓）—— 匠魂没有这一行 ✓ 故单开 ✓
            String drName = stripFormatting(Component
                    .translatable("modifier.tinkersnewlife.transcendent_dimension.damage_reduction").getString());
            if (!drName.isEmpty()) {
                tooltip.add(Component.translatable("modifier.tinkersnewlife.transcendent_dimension.damage_reduction")
                        .append(Component.literal(String.format("%.1f%%", data.getFloat(drKey()) * 100.0F))
                                .withStyle(net.minecraft.ChatFormatting.GREEN)));
            }
        } catch (Throwable ignored) {
            // 提示出错绝不能影响物品显示 ✓
        }
    }

    /** 追加到匠魂已经显示的那一行后面 ✓（找不到 ⇒ 什么都不做 ✗） */
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

    /** 去掉 § 颜色代码再比对 ✓ */
    private static String stripFormatting(String text) {
        return text == null ? "" : text.replaceAll("§.", "").trim();
    }
}
