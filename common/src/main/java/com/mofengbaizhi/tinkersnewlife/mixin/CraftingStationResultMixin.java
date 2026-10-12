package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.PortableDeviceSupport;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * TNL-825 \u2605\u2605\u2605 \u7ec8\u6781\u4fee\u6cd5\uff1a\u63d2\u5320\u9b42 {@code CraftingStationBlockEntity#calcResult} \u2713
 *
 * <h2>\u4e3a\u4f55\u63d2\u8fd9\u91cc\uff08\u2605\u65e5\u5fd7\u8bc1\u636e \u2713\uff09</h2>
 * <pre>
 *   \u65e5\u5fd7\u91cc "\u7ed3\u679c\u69fd\u6df7\u5165\u5df2\u52a0\u8f7d" \u51fa\u73b0\u4e86 2 \u6b21
 *   \u21d2 \u2605\u2605\u6a21\u7ec4\u7c7b\u7684\u6df7\u5165**\u5b8c\u5168\u53ef\u7528** \u2713\uff08\u2605\u539f\u7248\u7c7b\u7684\u7ee7\u627f\u65b9\u6cd5\u624d\u751f\u4e0d\u51fa refmap \u2717\uff09
 *   \u4f46 `m_7993_` \u91cc\u7684\u63a2\u9488\u6ca1\u6253 \u2717 \u21d2 \u6362\u4e00\u4e2a**\u5fc5\u5b9a\u4f1a\u88ab\u8c03**\u7684\u94a9\u5b50 \u2713
 *   {@code calcResult(Player)} \u662f\u5320\u9b42\u81ea\u5df1\u7684\u65b9\u6cd5\uff08\u2605\u975e\u539f\u7248\u2717 \u2605\u540d\u5b57\u4e0d\u88ab\u6df7\u6dc6 \u2713\uff09
 *   \u800c\u4e14**\u6bcf\u6b21\u7b97\u7ed3\u679c\u90fd\u8d70\u5b83** \u2713 \u21d2 \u5728 HEAD \u628a\u7f3a\u7684\u914d\u65b9\u8865\u4e0a\u5373\u53ef \u2713\u2713
 * </pre>
 *
 * <h2>\u505a\u6cd5</h2>
 * \u82e5\uff1a\u2605\u8fd9\u4e2a\u65b9\u5757\u5b9e\u4f53\u662f**\u6211\u4eec\u7684\u5e7d\u7075** \u2713 \u2605\u5ba2\u6237\u7aef \u2713 \u2605{@code lastRecipe == null} \u2717
 * \u21d2 \u7528\u5ba2\u6237\u7aef\u81ea\u5df1\u7684 RecipeManager \u5339\u914d 3x3 \u2713 \u21d2 \u53cd\u5c04\u8c03 {@code updateRecipe} \u2713
 * \u21d2 \u5320\u9b42\u63a5\u4e0b\u6765\u90a3\u53e5\u5c31\u80fd\u7b97\u51fa\u7ed3\u679c \u2713\u2713
 *
 * <p>\u2605\u65e5\u5fd7\u6807\u7b7e\u6539\u7528**\u7eaf ASCII**\uff08\u2605`\u00a7` \u4f1a\u88ab\u65e5\u5fd7\u7f16\u7801\u5403\u6389 \u2717 \u4e0a\u4e00\u8f6e\u5c31\u88ab\u5751\u4e86 \u2713\uff09
 */
@Mixin(targets = "slimeknights.tconstruct.tables.block.entity.table.CraftingStationBlockEntity", remap = false)
public class CraftingStationResultMixin {

    @Inject(method = "calcResult", at = @At("HEAD"), remap = false)
    private void tnl$supplyRecipe(Player player, CallbackInfoReturnable<ItemStack> cir) {
        try {
            BlockEntity self = (BlockEntity) (Object) this;
            if (!PortableDeviceSupport.VIRTUAL.containsValue(self)) {
                return;   // \u2605\u53ea\u5904\u7406\u6211\u4eec\u7684\u5e7d\u7075 \u2713
            }
            Level level = self.getLevel();
            if (level == null || !level.isClientSide) {
                return;   // \u2605\u670d\u52a1\u7aef\u81ea\u5df1\u4f1a\u7b97 \u2713
            }
            if (tnl$field(self, "lastRecipe") != null) {
                return;
            }
            Object grid = Class.forName("slimeknights.tconstruct.tables.block.entity.inventory.CraftingContainerWrapper")
                    .getConstructor(Container.class, int.class, int.class)
                    .newInstance(self, 3, 3);
            java.lang.reflect.Method m = net.minecraft.world.item.crafting.RecipeManager.class.getMethod(
                    "getRecipeFor", RecipeType.class, Container.class, Level.class);
            Object opt = m.invoke(level.getRecipeManager(), RecipeType.CRAFTING, grid, level);
            if (!(opt instanceof java.util.Optional<?> o) || o.isEmpty()) {
                TinkersNewlife.LOGGER.warn("TNL825 virtual crafting station: no recipe matched on client");
                return;
            }
            Object recipe = o.get();
            self.getClass().getMethod("updateRecipe", CraftingRecipe.class).invoke(self, recipe);
            TinkersNewlife.LOGGER.warn("TNL825 supplied recipe on client: {}", recipe.getClass().getSimpleName());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL825 failed to supply recipe: {}: {}", t.getClass().getSimpleName(), t.getMessage());
        }
    }

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
