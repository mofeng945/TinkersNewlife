package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotTypeMessage;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModCurios {

    @SubscribeEvent
    public static void enqueueIMC(InterModEnqueueEvent event) {
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder("hands")
                        .size(2)
                        .build()
        );

        // ✅ 将 ring 槽初始大小设为 1，使修改器能正常叠加
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder("ring")
                        .size(2)
                        .build()
        );

        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder("feet")
                        .size(1)
                        .build()
        );

        // 咒力核心饰品槽位（图标复用匠魂 pattern 图标，与玩家画的咒力核心纹理一致）
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder("curse_core")
                        .size(1)
                        .icon(new net.minecraft.resources.ResourceLocation(TinkersNewlife.MOD_ID, "gui/tinker_pattern/curse_core"))
                        .build()
        );

        // ⭐ 通用「饰品」槽（curio）：Curios 自带的槽位定义**没有 size（默认 0 = 不显示）**，
        //    必须由模组用 IMC 显式给一个 size，槽位才会真的出现在饰品栏里
        //    （本模组的 hands / ring / feet / curse_core 都是这么登记的）。
        //    封呪瓶佩戴在这里，所以这一条不可省 —— 少了它就会出现"物品放不进饰品槽"。
        //    图标沿用 Curios 自带的空饰品槽图标。
        InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                () -> new SlotTypeMessage.Builder("curio")
                        .size(1)
                        .icon(new net.minecraft.resources.ResourceLocation("curios", "slot/empty_curio_slot"))
                        .build()
        );
    }
}