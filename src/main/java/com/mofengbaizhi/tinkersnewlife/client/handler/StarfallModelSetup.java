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
 * TNL891 ★★ 注册自定义模型谓词 ``tinkersnewlife:charge`` ✓
 *
 * <p>★为何要自己的谓词 ✗：★原版 ``pulling``/``pull`` 依赖
 * {@code useItemRemaining} ✓，★而实机日志证明它被反复重置 ✗
 * （★TNL889：连续 24 秒 ``useTime=0 remaining=72000`` ✓）。
 * ★现在读的是我们自己数的 NBT ``TnlCharge`` ✓
 * （★由 StarfallChargeHandler 每 tick 读右键写入 ✓）。
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
