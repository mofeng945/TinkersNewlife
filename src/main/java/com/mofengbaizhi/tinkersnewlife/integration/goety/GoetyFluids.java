package com.mofengbaizhi.tinkersnewlife.integration.goety;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 诡厄巫法（goety）联动流体组：<b>只有诡厄在场时才注册</b>（整组同生共死）。
 *
 * <p>包含：熔融诅咒金属 / 熔融黑暗金属（诡厄锭熔炼）、不洁之血（熔融 goety:unholy_blood）、
 * 永燃圣火（不洁之血 + 匠魂烈焰血 合金）。
 * 公共代码按注册名取用，不引用本类字段。
 */
public final class GoetyFluids {

    private static final FluidRegistrar REG = new FluidRegistrar(TinkersNewlife.MOD_ID);

    private GoetyFluids() {
    }

    /** 熔融诅咒金属（诡厄巫法 诅咒金属锭 熔炼；tier2 材料「诅咒金属」原料流体） */
    public static final FluidRegistrar.FluidEntry MOLTEN_CURSED_METAL = REG.entry("molten_cursed_metal",
            2000, 8000, 900, 0xFF435C6A,  // 诡厄诅咒金属青
            FluidRegistrar.lavaProps(MapColor.COLOR_LIGHT_BLUE));

    /** 熔融黑暗金属（诡厄巫法 黑暗金属锭 熔炼；tier2 材料「黑暗金属」原料流体） */
    public static final FluidRegistrar.FluidEntry MOLTEN_DARK_METAL = REG.entry("molten_dark_metal",
            2000, 8000, 1000, 0xFF343540,  // goety dark_ingot 暗蓝灰 (52,53,64)
            FluidRegistrar.lavaProps(MapColor.COLOR_BLACK));

    /** 不洁之血（熔融 goety:unholy_blood 得到；永燃圣火的合金成分之一） */
    public static final FluidRegistrar.FluidEntry UNHOLY_BLOOD = REG.entry("unholy_blood",
            1500, 2500, 1200, 0xFF5A0A0A,  // 纯色暗血红 (90,10,10)
            FluidRegistrar.waterProps(MapColor.COLOR_RED));

    /** 永燃圣火（烈焰血 + 不洁之血 合金；顶级燃料，温度 2200） */
    public static final FluidRegistrar.FluidEntry EVERBURNING_HOLY_FIRE = REG.entry("everburning_holy_fire",
            1500, 2000, 2200, 0xFFFF2211,  // 更高亮度烈焰血偏红 (255,34,17)
            FluidRegistrar.lavaProps(MapColor.FIRE));

    /** 把本组四张注册表挂到模组总线（仅诡厄在场时被调用） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
