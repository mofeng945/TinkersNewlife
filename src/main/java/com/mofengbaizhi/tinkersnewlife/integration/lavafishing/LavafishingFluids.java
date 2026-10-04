package com.mofengbaizhi.tinkersnewlife.integration.lavafishing;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 熔岩钓鱼（lavafishing）联动流体组：<b>只有熔岩钓鱼在场时才注册</b>。
 *
 * <p>整组一起注册（FluidType + 静止 + 流动 + 液体方块 + 桶），避免"流体在、桶不在"的半残状态；
 * 熔岩钓鱼不在场时这组流体在注册表里根本不存在，对应的材料定义/数值/特性/配方也全部不加载
 * （数据包侧条件是 {@code forge:mod_loaded lavafishing}）。写法与 §978 的水产模块一致 ✓。
 *
 * <p>公共代码不引用本类字段：需要时按注册名取
 * （{@code IntegrationLoader.item("molten_promethium_bucket")}）。
 */
public final class LavafishingFluids {

    private static final FluidRegistrar REG = new FluidRegistrar(TinkersNewlife.MOD_ID);

    private LavafishingFluids() {
    }

    /**
     * 熔融钷（§980 熔岩钓鱼联动）：冶炼炉熔炼 {@code lavafishing:promethium_ingot} 得到 90 mB/锭。
     *
     * <p>颜色 {@code #CF7654} 取自熔岩钓鱼「钷锭」贴图实提（最亮 25% 均值 ✓，与海王锭同一口径），
     * 与材料渲染信息 / 特性颜色统一。
     */
    public static final FluidRegistrar.FluidEntry MOLTEN_PROMETHIUM = REG.entry("molten_promethium",
            2000, 10000, 1500, 0xFFCF7654,
            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));

    /** 把本组四张注册表挂到模组总线（仅熔岩钓鱼在场时被调用） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
