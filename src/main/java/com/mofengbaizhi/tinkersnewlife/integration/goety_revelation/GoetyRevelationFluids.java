package com.mofengbaizhi.tinkersnewlife.integration.goety_revelation;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 诡厄巫法：启示录（goety_revelation）联动流体组：<b>只有启示录在场时才注册</b>（整组同生共死）。
 *
 * <p>熔融破碎之环：启示录 broken_halo / ascension_halo 熔炼得到，浇在黑暗金属上成为「神灵金」材料。
 * 公共代码按注册名取用，不引用本类字段。
 */
public final class GoetyRevelationFluids {

    private static final FluidRegistrar REG = new FluidRegistrar(TinkersNewlife.MOD_ID);

    private GoetyRevelationFluids() {
    }

    /** 熔融破碎之环（启示录 broken_halo 熔炼；配色取 broken_halo 暖金橙） */
    public static final FluidRegistrar.FluidEntry MOLTEN_BROKEN_RING = REG.entry("molten_broken_ring",
            2000, 6000, 1200, 0xFFB87848,  // broken_halo 金橙 (184,120,72)
            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));

    /** 把本组四张注册表挂到模组总线（仅启示录在场时被调用） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
