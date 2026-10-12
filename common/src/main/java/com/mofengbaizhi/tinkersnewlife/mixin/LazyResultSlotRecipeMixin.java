package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * \u00a7824 \u2605\u2605\u2605\u7ec8\u6781\u65b9\u6848\uff1a\u63d2**\u5320\u9b42\u81ea\u5df1\u7684\u7ed3\u679c\u69fd** \u2713\uff08\u2605\u6a21\u7ec4\u7c7b \u21d2 \u4e0d\u9700\u8981 refmap \u21d2 \u80fd\u6ce8\u5165 \u2713\uff09\u3002
 *
 * <h2>\u4e3a\u4f55\u8d70\u5230\u8fd9\u4e00\u6b65\uff08\u2605\u5168\u90e8\u6709\u65e5\u5fd7\u8bc1\u636e \u2713\uff09</h2>
 * <pre>
 *   \u00b7 \u63d2\u539f\u7248 {@code Level#getBlockEntity} \u21d2 \u2605refmap \u6c38\u8fdc\u751f\u4e0d\u51fa\u6761\u76ee\uff08\u2605\u7ee7\u627f\u65b9\u6cd5 \u2717\uff09\u21d2 \u9759\u9ed8\u5931\u6548 \u2717
 *   \u00b7 \u63d2 Mantle \u4e5f\u4e00\u6837\u4ece\u672a\u88ab\u8c03\u7528 \u2717
 *   \u00b7 \u6302 {@code broadcastChanges} \u21d2 \u65e5\u5fd7\u8bc1\u660e\u5ba2\u6237\u7aef**\u4e0d\u8c03** \u2717
 *   \u00b7 \u6302\u5ba2\u6237\u7aef tick \u4e8b\u4ef6 \u21d2 \u65e5\u5fd7\u8bc1\u660e**\u4e5f\u6ca1\u8dd1** \u2717
 *   \u21d2 \u2605\u5269\u4e0b\u552f\u4e00\u7ed3\u6784\u4e0a\u53ef\u9760\u7684\uff1a\u63d2**\u5320\u9b42\u81ea\u5df1\u7684\u7c7b** \u2713
 *     \uff08\u2605\u672c\u4ed3\u7684 {@code TinkerTabsWidgetMixin} \u5c31\u662f\u540c\u7c7b\u578b \u2713 \u4e00\u76f4\u6709\u6548 \u2713\uff09
 * </pre>
 *
 * <h2>\u505a\u4e86\u4ec0\u4e48</h2>
 * \u5320\u9b42\u753b\u7ed3\u679c\u69fd\u65f6\u4f1a\u8c03 {@code m_7993_()}\uff08\u2605\u6bcf\u5e27 \u2713\uff09\u3002\u6211\u4eec\u5728 HEAD \u62a6\u4e00\u624b \u2713\uff1a
 * \u82e5\u8fd9\u4e2a\u69fd\u5bf9\u5e94\u7684**\u662f\u6211\u4eec\u7684\u5e7d\u7075** \u2713 \u4e14\u5b83 {@code lastRecipe} \u8fd8\u662f null \u2717
 * \u21d2 \u2605\u5ba2\u6237\u7aef\u81ea\u5df1\u7528\u81ea\u5df1\u7684 RecipeManager \u5339\u914d 3x3 \u2713 \u21d2 \u8c03 {@code updateRecipe} \u2713
 * \u21d2 \u540e\u9762\u5320\u9b42\u81ea\u5df1\u90a3\u53e5\u5c31\u80fd\u7b97\u51fa\u7ed3\u679c \u2713\u2713
 */
@Mixin(targets = "slimeknights.tconstruct.tables.menu.slot.PlayerSensitiveLazyResultSlot", remap = false)
public class LazyResultSlotRecipeMixin {

    /** \u2605\u63a2\u9488\uff1a\u8fd9\u4e2a\u6df7\u5165\u5230\u5e95\u6709\u6ca1\u6709\u88ab\u52a0\u8f7d \u2713\uff08\u2605\u65e5\u5fd7\u91cc\u80fd\u770b\u5230\u5c31\u8bf4\u660e\u6df7\u5165\u673a\u5236\u6b63\u5e38 \u2713\uff09 */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void tnl$loaded(CallbackInfo ci) {
        TinkersNewlife.LOGGER.warn("[\u4fbf\u643a\u8bbe\u5907] \u00a7824 \u7ed3\u679c\u69fd\u6df7\u5165\u5df2\u52a0\u8f7d\uff08\u2605\u8bf4\u660e\u6a21\u7ec4\u7c7b\u6df7\u5165\u53ef\u7528 \u2713\uff09");
    }

    @Inject(method = "m_7993_", at = @At("HEAD"), remap = false)
    private void tnl$ensureRecipe(CallbackInfoReturnable<ItemStack> cir) {
        try {
            Object container = tnl$field(this, "inventory");
            if (container == null) {
                return;
            }
            Object crafter = tnl$field(container, "crafter");
            if (!(crafter instanceof BlockEntity be) || !PortableDeviceSupport.VIRTUAL.containsValue(be)) {
                return;   // \u2605\u53ea\u5904\u7406\u6211\u4eec\u81ea\u5df1\u7684\u5e7d\u7075 \u2713
            }
            if (tnl$field(be, "lastRecipe") != null) {
                return;   // \u2605\u5df2\u6709\u914d\u65b9 \u2713
            }
            Level level = be.getLevel();
            if (level == null || !level.isClientSide) {
                return;
            }
            Object grid = Class.forName("slimeknights.tconstruct.tables.block.entity.inventory.CraftingContainerWrapper")
                    .getConstructor(Container.class, int.class, int.class)
                    .newInstance(be, 3, 3);
            java.lang.reflect.Method m = net.minecraft.world.item.crafting.RecipeManager.class.getMethod(
                    "getRecipeFor",
                    RecipeType.class, Container.class, Level.class);
            Object opt = m.invoke(level.getRecipeManager(), RecipeType.CRAFTING, grid, level);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) {
                return;
            }
            be.getClass().getMethod("updateRecipe", CraftingRecipe.class).invoke(be, o.get());
            TinkersNewlife.LOGGER.warn("[\u4fbf\u643a\u8bbe\u5907] \u00a7824 \u5ba2\u6237\u7aef\u5df2\u8865\u4e0a\u914d\u65b9\uff1a{}", o.get().getClass().getSimpleName());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[\u4fbf\u643a\u8bbe\u5907] \u00a7824 \u8865\u914d\u65b9\u5931\u8d25\uff1a{}: {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }

    /** \u2605\u6cbf\u7236\u7c7b\u94fe\u8bfb\u79c1\u6709\u5b57\u6bb5 \u2713 */
    private static Object tnl$field(Object target, String name) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
