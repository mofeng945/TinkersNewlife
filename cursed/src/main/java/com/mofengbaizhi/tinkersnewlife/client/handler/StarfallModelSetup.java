package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * TNL891 \u2605\u2605 \u6ce8\u518c\u81ea\u5b9a\u4e49\u6a21\u578b\u8c13\u8bcd ``tinkersnewlife:charge`` \u2713
 *
 * <p>\u2605\u4e3a\u4f55\u8981\u81ea\u5df1\u7684\u8c13\u8bcd \u2717\uff1a\u2605\u539f\u7248 ``pulling``/``pull`` \u4f9d\u8d56
 * {@code useItemRemaining} \u2713\uff0c\u2605\u800c\u5b9e\u673a\u65e5\u5fd7\u8bc1\u660e\u5b83\u88ab\u53cd\u590d\u91cd\u7f6e \u2717
 * \uff08\u2605TNL889\uff1a\u8fde\u7eed 24 \u79d2 ``useTime=0 remaining=72000`` \u2713\uff09\u3002
 * \u2605\u73b0\u5728\u8bfb\u7684\u662f\u6211\u4eec\u81ea\u5df1\u6570\u7684 NBT ``TnlCharge`` \u2713
 * \uff08\u2605\u7531 StarfallChargeHandler \u6bcf tick \u8bfb\u53f3\u952e\u5199\u5165 \u2713\uff09\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StarfallModelSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(
                ModItems.STARFALL_LAMENT.get(),
                new ResourceLocation(TinkersNewlife.MOD_ID, "charge"),
                (stack, level, entity, seed) -> {
                    int c = stack.hasTag() ? stack.getTag().getInt("TnlCharge") : 0;
                    return Math.min(1.0F, c / (float) StarfallChargeHandler.MAX_CHARGE);
                }));
    }
}
