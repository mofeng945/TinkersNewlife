package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TNL829 \u2605\u63a2\u9488\uff1a\u5320\u9b42\u5408\u6210\u754c\u9762\u5230\u5e95\u6709\u6ca1\u6709\u88ab\u6253\u5f00 \u2713
 *
 * <p>\u786c\u4e8b\u5b9e\uff1a\u7ed3\u679c\u69fd\u7684 {@code getItem}/{@code calcResult} **\u4e24\u4fa7\u90fd\u6ca1\u88ab\u8c03\u7528** \u2717
 * \u21d2 \u2605\u5f88\u53ef\u80fd\u754c\u9762\u6839\u672c**\u4e0d\u662f\u5320\u9b42\u7684\u5408\u6210\u754c\u9762** \u2717\uff08\u2605\u90a3\u5c31\u4e0d\u4f1a\u753b\u7ed3\u679c\u69fd \u2717\uff09
 * \u672c\u63a2\u9488\u5728\u8be5\u754c\u9762\u6784\u9020\u65f6\u6253\u4e00\u884c \u2713 \u21d2 \u6709\u8fd9\u884c\u5c31\u8bf4\u660e\u754c\u9762\u5bf9\u2713 \u6ca1\u6709\u5219\u8bf4\u660e\u754c\u9762\u9519\u4e86 \u2717
 */
@Mixin(targets = "slimeknights.tconstruct.tables.client.inventory.CraftingStationScreen", remap = false)
public class ScreenProbeMixin {

    @Inject(method = "<init>", at = @At("HEAD"), remap = false)
    private void tnl$screenOpened(CallbackInfo ci) {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            TinkersNewlife.LOGGER.warn("TNL829 Tinkers crafting screen opened, menu={} slots={} player={}",
                    mc.player == null ? "?" : mc.player.containerMenu.getClass().getName(),
                    mc.player == null ? -1 : mc.player.containerMenu.slots.size(),
                    mc.player == null ? "?" : mc.player.getName().getString());
        } catch (Throwable ignored) {
        }
    }
}
