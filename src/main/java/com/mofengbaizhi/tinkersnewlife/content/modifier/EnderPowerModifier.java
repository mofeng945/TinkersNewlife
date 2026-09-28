package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 虚空金属工具特性·<b>末影之力</b>（<b>无等级</b>，<b>铁魔法联动</b>）
 * ——「末影维度意志正在注视着你……」
 *
 * <p>规格（用户口径）：工具<b>自带注入 3 级传送术 + 3 级法术镣铐</b> ⇒
 * 走铁魔法自己的"注入"机制（{@code ISpellContainer} ✓，`tooltip.irons_spellbooks.imbued_tooltip` = 「注入的法术：」✓）：
 * <ul>
 *   <li>{@code irons_spellbooks:teleport}（<b>传送术</b> ✓）× 等级 3；</li>
 *   <li>{@code irons_spellbooks:arcane_shackle}（<b>法术镣铐 / Arcane Shackle</b> ✓，用户点名的那个 × 等级 3）。</li>
 * </ul>
 *
 * <p>⚠ 只在物品**还没有**法术容器时写入（{@code IronSpellsSpellAccess#ensureInscribedPair} 同一口径 ✓）
 * ⇒ 不会跟奥术铁砧上的玩家编辑打架 ✓；铁魔法不在场时整段静默跳过 ✓。
 * 行为在 {@code content/modifier/events/EnderPowerHandler} ✓。
 *
 * <h2>§724：为什么不装铁魔法时也要给一句提示</h2>
 * 材料 traits 是"整份文件"加载的 ⇒ 没法只把这一条按模组藏掉 ✗。
 * 于是没装铁魔法时：特性仍在（**不报错、不刷日志** ✓，处理器直接跳过 ✓），但自动显示的
 * {@code .description} 里写着两个法术 ✗ —— 玩家会以为"坏了" ✗。这里实现 {@link TooltipModifierHook}：
 * <b>装了 ⇒ 正常提示；没装 ⇒ 明确告诉玩家需要铁魔法</b> ✓。
 */
public class EnderPowerModifier extends LevelLessModifier implements TooltipModifierHook {

    /** 强化 id */
    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "ender_power"));

    /** 注入的第一个法术：铁魔法·传送术 ✓ */
    public static final String SPELL_TELEPORT = "irons_spellbooks:teleport";
    /** 注入的第二个法术：铁魔法·法术镣铐 ✓（英文名 Arcane Shackle，用户点名 ✓） */
    public static final String SPELL_SHACKLE = "irons_spellbooks:arcane_shackle";
    /** 注入等级：两个都是 3 级 ✓（用户口径） */
    public static final int INSCRIBED_LEVEL = 3;
    /** 容器槽位数（两个法术 ⇒ 给 3 格，和"破法"那条特性统一 ✓） */
    public static final int CONTAINER_SLOTS = 3;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.TOOLTIP);
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable(IronSpellsReflector.isIronSpellsAvailable()
                ? "modifier.tinkersnewlife.ender_power.tip"
                : "modifier.tinkersnewlife.ender_power.tip.missing"));
    }

    /** 该物品是否带本强化（工具/盔甲通用，损坏时不算 ✓） */
    public static boolean has(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = com.mofengbaizhi.tinkersnewlife.util.ToolHelper.getToolStack(stack);
        return com.mofengbaizhi.tinkersnewlife.util.ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }
}
