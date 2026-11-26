package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 命灯指轮：戒指槽饰品。
 *
 * <p><b>效果</b>：佩戴后，佩戴者攻击任何目标时，目标**最终都会保留 1 点生命而不会死亡**
 * （伤害被截在"只剩 1 滴"，连致死伤害也一样；见 {@code LifeLampRingHandler}）。
 *
 * <p>慈悲是最大的恶，善念或许也会铸就业果。
 */
public class LifeLampRingItem extends Item implements ICurioItem {

    public LifeLampRingItem() {
        super(new Properties().stacksTo(1));
    }

    // ========== ICurioItem：戒指槽 ==========

    /**
     * 可佩戴的槽位：
     * <ul>
     *   <li>{@code ring} —— 整合包的戒指槽（本模组不注册公共槽位，只用现成的）；</li>
     *   <li>{@code curio} —— Curios 通用槽，兼容没有戒指槽的整合包。</li>
     * </ul>
     */
    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        String id = context.identifier();
        return "ring".equals(id) || "curio".equals(id);
    }

    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        // 手持右键可直接戴到戒指槽
        return canEquip(context, stack);
    }

    // ========== 物品提示 ==========

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tinkersnewlife.life_lamp_ring.effect")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinkersnewlife.life_lamp_ring.flavor")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }

    // ========== 查询 ==========

    /** 该实体是否戴着命灯指轮（遍历 curios 全部槽位，戒指槽/通用槽都算） */
    public static boolean isWorn(LivingEntity entity) {
        if (entity == null) return false;
        var curios = CuriosApi.getCuriosInventory(entity).resolve();
        if (curios.isEmpty()) return false;
        for (ICurioStacksHandler handler : curios.get().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof LifeLampRingItem) return true;
            }
        }
        return false;
    }

    /** 语言键自检用 */
    public static final String LANG_PREFIX = "item." + TinkersNewlife.MOD_ID + ".life_lamp_ring";
}
