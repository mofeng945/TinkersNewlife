package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import javax.annotation.Nonnull;

/**
 * 噤默手套相关辅助：统一"查找玩家佩戴的手套"逻辑。
 * <p>
 * 原实现散落在 GloveWeaponStorage / DarkSilentManager / SilentGloveSoundHandler 等多处，
 * 各自遍历 Curios "hands" 槽。统一收拢到此处，后续修改槽位规则只改这一个类。
 */
public final class GloveHelper {

    private GloveHelper() {}

    /**
     * 在玩家 Curios "hands" 槽中查找佩戴的噤默手套。
     *
     * @param player 玩家
     * @return 手套 ItemStack；未佩戴时返回 {@link ItemStack#EMPTY}
     */
    @Nonnull
    public static ItemStack findWornGlove(Player player) {
        var curios = CuriosApi.getCuriosHelper().getCuriosHandler(player).resolve();
        if (curios.isEmpty()) return ItemStack.EMPTY;

        ICurioStacksHandler gloveHandler = curios.get().getStacksHandler("hands").orElse(null);
        if (gloveHandler == null) return ItemStack.EMPTY;

        for (int i = 0; i < gloveHandler.getSlots(); i++) {
            ItemStack stack = gloveHandler.getStacks().getStackInSlot(i);
            if (stack.getItem() instanceof SilentGloveItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * 玩家是否佩戴了噤默手套（Curios "hands" 槽）。
     */
    public static boolean isWearingGlove(Player player) {
        return !findWornGlove(player).isEmpty();
    }
}
