package com.mofengbaizhi.tinkersnewlife.integration.goety_ladder;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 「诡厄巫法：阶梯」({@code goety_ladder}) 联动流体组：<b>熔融虚空金属</b>。
 *
 * <p>按本模组的联动隔离铁律（见 {@code integration/Integration} 的文档 ✓）：
 * <b>整组同生共死、只有阶梯在场时才注册</b> ✓ —— FluidType / 静止 / 流动 / 流体方块 / 桶 一起注册或一起不注册 ✓，
 * 公共代码只按注册名取用 ✓。
 *
 * <h2>为什么 §725 要新增这个流体（用户口径）</h2>
 * 用户要求材料「虚空金属」应当<b>熔融成流体后浇筑使用</b>，而不是部件台直造 ✗ ⇒
 * 需要一条 {@code 虚空金属锭 --熔炼--> 熔融虚空金属 --浇筑--> 部件} 的链路 ✓：
 * <ul>
 *   <li>熔炼配方：{@code data/tinkersnewlife/recipes/smeltery/melting/void_metal_ingot.json} ✓（1 锭 = 90 mB ✓）；</li>
 *   <li>材质-流体绑定：{@code data/tinkersnewlife/recipes/materials/void_metal_material_fluid.json} ✓
 *       （只给 fluid + output ⇒ "这个流体就是这个材质" ✓，浇筑台/铸造台就能用它浇出部件 ✓）；</li>
 *   <li>部件浇筑本身由<b>匠魂自带</b>的 {@code data/tconstruct/recipes/tools/parts/casting/*} 泛化处理 ✓
 *       （按材质流体解析，不需要我们为每个部件写配方 ✓）；</li>
 *   <li>顺带倒回锭：{@code data/tinkersnewlife/recipes/casting/void_metal_ingot/void_metal_ingot_cast.json} ✓。</li>
 * </ul>
 */
public final class GoetyLadderFluids {

    private static final FluidRegistrar REG = new FluidRegistrar(TinkersNewlife.MOD_ID);

    private GoetyLadderFluids() {
    }

    /**
     * 熔融虚空金属（阶梯 {@code goety_ladder:void_metal} 熔炼得到；T4 材料「虚空金属」的浇筑原料 ✓）。
     * <p>密度/粘度对齐我们其它熔融金属（2000 / 8000 ✓）；温度取 1200（比黑暗金属 1000 高、比神灵金 1200 同级 ✓）；
     * 色调取材料色 {@code #6A4BB5} 偏亮一档 ⇒ {@code 0xFF8A6BE0} ✓（Mantle 那边还会按 fluid_texture 的 color 再统一 ✓）。
     */
    public static final FluidRegistrar.FluidEntry MOLTEN_VOID_METAL = REG.entry("molten_void_metal",
            2000, 8000, 1200, 0xFF8A6BE0,
            FluidRegistrar.lavaProps(MapColor.COLOR_PURPLE));

    /** 把本组注册表挂到模组总线（仅阶梯在场时被 {@code IntegrationLoader} 调用 ✓） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
