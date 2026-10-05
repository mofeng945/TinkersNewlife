package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * <b>把"刀的耐久"接到匠魂工具上</b>（§1007 建立 ✓；§1024 按 TiCEX 真源码校正 ✓）。
 *
 * <p>为什么需要它：我们的 {@code KatanaItem} 是从匠魂 {@code ModifiableItem} **整段移植**过来的 ✓
 * （见 §1006），移植时连 {@code initCapabilities(...)} 一起带过来了 ✓ ——
 * 而匠魂那个方法返回的是**匠魂自己的 {@code ToolCapabilityProvider}** ✓
 * ⇒ {@code ItemSlashBlade} 原本提供的刀状态**被顶掉了** ✗
 * ⇒ 所以要把刀状态**作为一项"匠魂工具能力"再注册回去** ✓（见 {@link SBItemCapabilityProvider} ✓）。
 *
 * <p>§1024 校正点（与 TiCEX 的 {@code ToolBladeStateCapability} 逐条对齐 ✓）：
 * <ul>
 *   <li>{@link #isBroken()} ⇒ {@code ToolDamageUtil.isBroken(toolStack)} ✓（原为 {@code tool.isBroken()} ✗）；</li>
 *   <li>{@link #getMaxDamage()} ⇒ {@code ToolDamageUtil.getFakeMaxDamage(toolStack)} ✓
 *       （原为 {@code round(stats.get(DURABILITY))} ✗）；</li>
 *   <li>{@link #getDamage()} ⇒ <b>恒为 0</b> ✓✓ —— 这是关键差异 ✗：
 *       本体的"刀坏了"判定会读它 ✗，只要它 ≥ {@link #getMaxDamage()} 本体就认为刀坏了
 *       ⇒ **不打人、不推连段** ✗（实测症状正是如此 ✓）。耐久本身由匠魂负责 ✓，这里不该再报一份 ✗。</li>
 * </ul>
 * ⚠ TiCEX 构造函数里还有一段"从匠魂持久化数据恢复刀状态"的代码 ✓，但全仓库搜索显示
 * {@code BLADE_STATE_LOCATION} **只有读、没有写** ✗ ⇒ 那段在当前版本里是惰性的 ✓，故未移植 ✓。
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
        return ToolDamageUtil.isBroken(toolStack);
    }

    @Override
    public int getMaxDamage() {
        // 匠魂工具的面板耐久（用匠魂自己的工具方法 ✓，与 TiCEX 一致 ✓）
        return ToolDamageUtil.getFakeMaxDamage(toolStack);
    }

    @Override
    public int getDamage() {
        // ★ 恒为 0 ✓：耐久归匠魂管 ✓，本体这边不能再报一份损耗 ✗（否则会被判成"刀坏了" ✗）
        return 0;
    }
}
