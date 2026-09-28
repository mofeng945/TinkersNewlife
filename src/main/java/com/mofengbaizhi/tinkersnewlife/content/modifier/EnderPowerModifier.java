package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.impl.SingleLevelModifier;

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
 * <p>⚠ 只在物品**还没有**法术容器时写入（{@code IronSpellsSpellAccess#ensureInscribed} 同一口径 ✓）
 * ⇒ 不会跟奥术铁砧上的玩家编辑打架 ✓；铁魔法不在场时整段静默跳过 ✓。
 * 行为在 {@code content/modifier/events/EnderPowerHandler} ✓。
 */
public class EnderPowerModifier extends SingleLevelModifier {

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

    /** 该物品是否带本强化（工具/盔甲通用，损坏时不算 ✓） */
    public static boolean has(net.minecraft.world.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = com.mofengbaizhi.tinkersnewlife.util.ToolHelper.getToolStack(stack);
        return com.mofengbaizhi.tinkersnewlife.util.ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }
}
