package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.module.ModuleHookMap;

/**
 * 铁魔法联动特性·<b>破法</b>（材料「秘银」自带，<b>无等级</b>）：
 * 工具<b>自带 1 级「法术反制」</b>（铁魔法 {@code irons_spellbooks:counterspell}）。
 *
 * <p>实现：在背包 tick 里往物品写入一个法术容器并刻入该法术（与神圣之力注入预设法术同一套机制 ✓）。
 * 刻印后既可用奥术铁砧继续加/升法术 ✓（铁砧只认"已是法术容器"的物品 ✓），
 * 也能配合材料1 的「魔导」特性提升其等级 ✓。
 */
public class SpellBreakModifier extends Modifier {

    public static final ModifierId ID = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "spell_break"));

    /** 自带刻印的法术与等级 */
    public static final String INSCRIBED_SPELL = "irons_spellbooks:counterspell";
    public static final int INSCRIBED_LEVEL = 1;
    /** 容器格数 */
    public static final int CONTAINER_SLOTS = 3;

    @Override
    public Component getDisplayName(int level) {
        return this.getDisplayName();
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
    }

    /** 该物品是否带破法 */
    public static boolean has(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ID) > 0;
    }
}
