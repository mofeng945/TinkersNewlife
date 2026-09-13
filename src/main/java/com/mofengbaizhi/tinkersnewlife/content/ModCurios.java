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

        // ⚠ 通用的「饰品」槽（charm / curio）是整合包共享槽，**默认不要抢着注册**：
        //    这类槽位是整合包里其它饰品模组（如神秘遗物把 curios 的 charm 槽中文名覆盖成"饰品"、
        //    奇异饰品、星月遗物…）已经在用的公共槽，本模组再注册一次会覆盖别人的 size/图标。
        //    封呪瓶通过 canEquip 认这些槽位标识 + 写物品标签（data/curios/tags/items/charm.json）来适配。

        // ⭐ 兜底：通用「饰品」槽（charm）
        //  · Curios 自带的槽位定义都没有 size，槽位必须由模组注册；本整合包里 charm 槽是神秘遗物注册的
        //    （它还把 curios.identifier.charm 的中文名覆盖成了"饰品"），诡厄巫法也注册了 charm；
        //    星月遗物/奇异饰品只写物品标签、不注册槽位。
        //  · 为了让"一个 charm 提供者都没装"时封呪瓶依然戴得上，这里补一个 size 1 的 charm 槽；
        //    但只要有任何提供者在场就**不注册** —— Curios 对同名槽位是"后来者覆盖 size"
        //    （SlotType.Builder#apply），无条件注册会把别人设好的槽位大小改掉。
        if (!anyCharmSlotProvider()) {
            InterModComms.sendTo(CuriosApi.MODID, SlotTypeMessage.REGISTER_TYPE,
                    () -> new SlotTypeMessage.Builder("charm")
                            .size(1)
                            .build()
            );
        }
    }

    /** 会注册通用「饰品」(charm) 槽的模组；有它们在时本模组不注册，避免覆盖别人的槽位大小 */
    private static final String[] CHARM_SLOT_PROVIDERS = { "enigmaticlegacy", "goety" };

    private static boolean anyCharmSlotProvider() {
        for (String mod : CHARM_SLOT_PROVIDERS) {
            if (com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.isLoaded(mod)) return true;
        }
        return false;
    }
}