package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;

/**
 * 本模组<b>原生</b>流体（原料来自原版 / 本模组内容 / 本模组事件，与任何联动模组无关）。
 *
 * <p>⭐ 联动材料的流体（熔融龙钢、龙血、熔融诅咒金属、不洁之血、熔融破碎之环…）**不在这里**：
 * 它们按"联动注册标准写法"放在 {@code integration/<modid>/} 里，与对应模组同生共死
 * （模组不在场 → 流体/FluidType/液体方块/桶整组不注册）。见
 * {@code integration.iceandfire.IceAndFireFluids} / {@code integration.goety.GoetyFluids} /
 * {@code integration.goety_revelation.GoetyRevelationFluids}。
 */
public class ModFluids {

    // ============================================================
    // 注册表（原生流体共用一套；联动流体各有自己的一套，见 FluidRegistrar）
    // ============================================================
    public static final FluidRegistrar REGISTRAR = new FluidRegistrar(TinkersNewlife.MOD_ID);

    public static final DeferredRegister<Fluid> FLUIDS = REGISTRAR.fluids;
    public static final DeferredRegister<FluidType> FLUID_TYPES = REGISTRAR.types;
    public static final DeferredRegister<Block> FLUID_BLOCKS = REGISTRAR.blocks;
    public static final DeferredRegister<Item> FLUID_BUCKETS = REGISTRAR.buckets;

    /** 声明原生流体（转发到公共注册器） */
    private static FluidRegistrar.FluidEntry entry(String name, int density, int viscosity, int temperature,
                                                   int tintColor,
                                                   net.minecraft.world.level.block.state.BlockBehaviour.Properties props) {
        return REGISTRAR.entry(name, density, viscosity, temperature, tintColor, props);
    }

    // ============================================================
    // 原生流体
    // ============================================================

    /** 格赫罗斯之血（本模组下界矿/内容） */
    public static final FluidRegistrar.FluidEntry GHELOTH_BLOOD = entry("gheloth_blood",
            1500, 2000, 300, 0xFFFF4500,
            FluidRegistrar.waterProps(MapColor.CRIMSON_STEM));

    /** 熔融尼古拉斯的祝福（本模组喂食繁殖掉落） */
    public static final FluidRegistrar.FluidEntry MOLTEN_NICHOLAS_BLESSING = entry("molten_nicholas_blessing",
            2000, 6000, 800, 0xFF9B59B6,
            FluidRegistrar.lavaProps(MapColor.COLOR_PURPLE));

    /** 哈斯塔之恶（本模组 Y>400 坠落事件） */
    public static final FluidRegistrar.FluidEntry HASTUR_MALICE = entry("hastur_malice",
            1500, 2000, 300, 0xFF4B0082,
            FluidRegistrar.waterProps(MapColor.COLOR_PURPLE));

    /** 灰烬之墨（本模组钓鱼产出） */
    public static final FluidRegistrar.FluidEntry ASHEN_INK = entry("ashen_ink",
            1500, 2000, 300, 0xFFC0C0C0,
            FluidRegistrar.waterProps(MapColor.COLOR_LIGHT_GRAY));

    /** 熔融杜兰达尔（本模组凋灵掉落线） */
    public static final FluidRegistrar.FluidEntry MOLTEN_DURANDAL = entry("molten_durandal",
            2000, 10000, 1500, 0xFFFFD700,  // 金黄色
            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));
}
