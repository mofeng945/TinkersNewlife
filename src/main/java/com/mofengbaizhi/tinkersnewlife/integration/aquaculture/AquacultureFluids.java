package com.mofengbaizhi.tinkersnewlife.integration.aquaculture;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 水产养殖2（aquaculture）联动流体组：<b>只有水产在场时才注册</b>。
 *
 * <p>整组一起注册（FluidType + 静止 + 流动 + 液体方块 + 桶），避免"流体在、桶不在"的半残状态；
 * 水产不在场时这组流体在注册表里根本不存在，对应的材料定义/数值/特性/配方也全部不加载
 * （数据包侧条件是 {@code forge:mod_loaded aquaculture}）。见备忘录 §978。
 *
 * <p>公共代码不引用本类字段：需要时按注册名取
 * （{@code IntegrationLoader.item("molten_neptunium_bucket")} /
 * {@code ForgeRegistries.FLUIDS.getValue(new ResourceLocation(MOD_ID, "molten_neptunium_still"))}）。
 */
public final class AquacultureFluids {

    private static final FluidRegistrar REG = new FluidRegistrar(TinkersNewlife.MOD_ID);

    private AquacultureFluids() {
    }

    /**
     * 熔融海王金属（§965 水产养殖2 联动）：冶炼炉熔炼 {@code aquaculture:neptunium_ingot} 得到 90 mB/锭。
     *
     * <p>颜色 {@code #31A988} 取自水产海王锭贴图实提，与材料渲染信息 / 特性颜色统一。
     */
    public static final FluidRegistrar.FluidEntry MOLTEN_NEPTUNIUM = REG.entry("molten_neptunium",
            2000, 10000, 1500, 0xFF31A988,
            FluidRegistrar.lavaProps(MapColor.COLOR_CYAN));

    /** 把本组四张注册表挂到模组总线（仅水产在场时被调用） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
