package com.mofengbaizhi.tinkersnewlife.util;

import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;

/**
 * 匠魂工具安全操作辅助类
 */
public final class ToolHelper {

    private ToolHelper() {}

    /**
     * 安全地获取 ToolStack
     * <p>
     * 只有在物品是匠魂可修改工具（实现了 IModifiable）时，才会构造 ToolStack，
     * 否则返回 null，从而避免 "non-modifiable tool" 警告。
     *
     * @param stack 物品栈
     * @return ToolStack 实例，或 null（如果 stack 为空、不是匠魂工具、或构造失败）
     */
    @Nullable
    public static ToolStack getToolStack(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof IModifiable)) return null;
        return ToolStack.from(stack);
    }
}