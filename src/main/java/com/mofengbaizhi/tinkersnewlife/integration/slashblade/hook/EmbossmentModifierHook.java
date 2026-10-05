package com.mofengbaizhi.tinkersnewlife.integration.slashblade.hook;

// 移植自 TiCEX (MIT): moffy.ticex.lib.hook.EmbossmentModifierHook

import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;

/**
 * TiCEX 的"装裱（embossment）"修饰符钩子 —— 逐字移植（MIT）。
 *
 * <p>原版（TiCEX）用它把"另一个物品的数据/附魔/刀状态"贴到工具上；
 * 这里的 {@link EmbossmentContext} 就是那套流程的上下文（工具栈 ＋ 修补台容器 ＋ 错误信息）。
 */
public interface EmbossmentModifierHook {

    boolean applyItem(EmbossmentContext context, int inputIndex, boolean secondary);

    class DefaultClass implements EmbossmentModifierHook {

        @Override
        public boolean applyItem(EmbossmentContext context, int inputIndex, boolean secondary) {
            return false;
        }
    }

    record AllMerger(Collection<EmbossmentModifierHook> modules) implements EmbossmentModifierHook {

        @Override
        public boolean applyItem(EmbossmentContext context, int inputIndex, boolean secondary) {
            for (EmbossmentModifierHook module : modules) {
                boolean result = module.applyItem(context, inputIndex, secondary);
                if (result) {
                    return true;
                }
            }
            return false;
        }
    }

    class EmbossmentContext {

        private ItemStack toolStack;
        private ITinkerStationContainer inv;
        private Component errorMsg;

        public EmbossmentContext(ItemStack toolStack, ITinkerStationContainer inv) {
            this.toolStack = toolStack;
            this.inv = inv;
            // ⚠ 原版 key 是 recipe.ticex.embossment_not_allowed ⇒ 换成我们自己的命名空间 ✓
            this.errorMsg = Component.translatable("recipe.tinkersnewlife.embossment_not_allowed");
        }

        public ItemStack getToolStack() {
            return toolStack;
        }

        public ITinkerStationContainer getInv() {
            return inv;
        }

        public ItemStack getInputStack(int index) {
            return inv.getInput(index);
        }

        public Component getErrorMsg() {
            return errorMsg;
        }

        public void setToolStack(ItemStack toolStack) {
            this.toolStack = toolStack;
        }

        public void setInv(ITinkerStationContainer inv) {
            this.inv = inv;
        }

        public void setErrorMsg(Component errorMsg) {
            this.errorMsg = errorMsg;
        }
    }
}
