package com.mofengbaizhi.tinkersnewlife.content.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 处刑人之剑（伏诛赐死领域发放的行刑武器）
 * <ul>
 *   <li>仅 1 点耐久：命中一次即毁（无论是否处决目标）</li>
 *   <li>只能存在 120 秒：过期自动消散；背包已有另一把时不再发放新剑</li>
 *   <li>命中处刑目标 → 目标直接死亡；命中其它生物 → 造成 200% 伤害</li>
 *   <li>NBT：{@code execution_target} = 处刑目标 UUID（无则仅作普通弱剑）</li>
 * </ul>
 */
public class ExecutionSwordItem extends SwordItem {

    /** NBT：处刑目标 UUID */
    public static final String TAG_TARGET = "execution_target";

    public ExecutionSwordItem(Properties properties) {
        super(Tiers.WOOD, 3, -2.4F, properties);
    }

    public static void setTarget(ItemStack stack, UUID targetId) {
        stack.getOrCreateTag().putUUID(TAG_TARGET, targetId);
    }

    @Nullable
    public static UUID getTarget(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().hasUUID(TAG_TARGET)) return null;
        return stack.getTag().getUUID(TAG_TARGET);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.tinkersnewlife.execution_sword.desc"));
    }
}
