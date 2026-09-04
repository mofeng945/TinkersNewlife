package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 咒力核心（模块化饰品）
 * <p>
 * 可穿戴于「咒力核心」饰品槽位（curios: curse_core）。
 * 作为匠魂模块化工具，可通过工匠站用部件组装、强化，
 * 工具定义自带槽位：1 领域槽 + 2 防御槽 + 3 升级槽。
 */
public class CurseCoreItem extends ModifiableItem implements ICurioItem {

    public static final ToolDefinition CURSE_CORE_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "curse_core"));

    public CurseCoreItem(Properties properties) {
        super(properties, CURSE_CORE_DEFINITION);
    }

    // ========== ICurioItem ==========

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        if (!"curse_core".equals(context.identifier())) return false;
        // ⭐ 天与咒缚：失去咒力，无法佩戴咒力核心（服务端持久数据校验）
        if (context.entity() instanceof net.minecraft.world.entity.player.Player player
                && com.mofengbaizhi.tinkersnewlife.content.handler.HeavenlyRestrictionHandler.isRestricted(player)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return false;
    }
}
