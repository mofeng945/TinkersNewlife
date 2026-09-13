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

        // ⚠ 这里**不要**注册通用的「饰品」槽（curio / charm 之类的共享槽位）：
        //    这类槽位是整合包里其它饰品模组（如神秘遗物把 curios 的 charm 槽中文名覆盖成"饰品"、
        //    奇异饰品、星月遗物…）已经在用的公共槽，本模组再注册一次会覆盖别人的 size/图标。
        //    封呪瓶通过 canEquip 认这些槽位标识 + 写物品标签（data/curios/tags/items/charm.json）来适配。
    }
}