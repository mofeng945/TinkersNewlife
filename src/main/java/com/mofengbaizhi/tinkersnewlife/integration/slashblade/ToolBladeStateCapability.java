package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * <b>把"刀的耐久"接到匠魂工具上</b>（§1007 · 照 TiCEX 的做法 ✓）。
 *
 * <p>为什么需要它：我们的 {@code KatanaItem} 是从匠魂 {@code ModifiableItem} **整段移植**过来的 ✓
 * （见 §1006），移植时连 {@code initCapabilities(...)} 一起带过来了 ✓ —— 而匠魂那个方法返回的是
 * **匠魂自己的 {@code ToolCapabilityProvider}** ✓ ⇒ {@code ItemSlashBlade} 原本提供的刀状态
 * **被顶掉了** ✗。所以要把刀状态**作为一项"匠魂工具能力"再注册回去** ✓（见 {@link SBItemCapabilityProvider} ✓）。
 *
 * <p>只覆盖三处 ✓（与 TiCEX 完全一致 ✓）：
 * <ul>
 *   <li>{@link #isBroken()} ⇒ 匠魂工具的损坏态 ✓</li>
 *   <li>{@link #getMaxDamage()} ⇒ 匠魂工具的面板耐久（{@code ToolStats.DURABILITY} ✓）</li>
 *   <li>{@link #getDamage()} ⇒ 匠魂工具当前已损耗的耐久 ✓</li>
 * </ul>
 * ⇒ 于是"刀的耐久条/破损/无法使用"与匠魂完全同步 ✓（而不是各记一套 ✗）。
 */
public class ToolBladeStateCapability extends SlashBladeState {

    /** 对应的匠魂工具栈（拔刀剑物品 ✓） */
    protected final ItemStack toolStack;
    /** 匠魂工具视图（取面板与耐久 ✓） */
    protected final IToolStackView tool;

    public ToolBladeStateCapability(ItemStack toolStack, IToolStackView tool) {
        super(toolStack);
        this.toolStack = toolStack;
        this.tool = tool;
    }

    @Override
    public boolean isBroken() {
        return tool.isBroken();
    }

    @Override
    public int getMaxDamage() {
        // 匠魂工具的面板耐久（取不到就退化为 1，避免除零 ✓）
        return Math.max(1, Math.round(tool.getStats().get(ToolStats.DURABILITY)));
    }

    @Override
    public int getDamage() {
        return tool.getDamage();
    }
}
