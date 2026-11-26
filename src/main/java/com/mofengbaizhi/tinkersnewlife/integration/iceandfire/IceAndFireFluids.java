package com.mofengbaizhi.tinkersnewlife.integration.iceandfire;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.FluidRegistrar;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 冰火传说（iceandfire）联动流体组：<b>只有冰火在场时才注册</b>。
 *
 * <p>整组一起注册（FluidType + 静止 + 流动 + 液体方块 + 桶），避免"流体在、桶不在"的半残状态；
 * 冰火不在场时这些流体在注册表里根本不存在，对应的材料定义/配方/标签也全部不加载
 * （数据包侧条件是 {@code forge:mod_loaded iceandfire}）。
 *
 * <p>公共代码不引用本类字段：需要时按注册名取（{@code IntegrationLoader.item("…_bucket")} /
 * {@code ForgeRegistries.FLUIDS.getValue(new ResourceLocation(MOD_ID, name))}）。
 */
public final class IceAndFireFluids {

    private static final FluidRegistrar REG = new FluidRegistrar(TinkersNewlife.MOD_ID);

    private IceAndFireFluids() {
    }

    // ---- 龙血（冰火三种龙血熔炼/浇筑） ----
    public static final FluidRegistrar.FluidEntry FIRE_BLOOD = REG.entry("fire_blood",
            1500, 2000, 1870, 0xFFFF4500,
            FluidRegistrar.waterProps(MapColor.FIRE));

    public static final FluidRegistrar.FluidEntry ICE_BLOOD = REG.entry("ice_blood",
            1500, 2000, 250, 0xFF00BFFF,
            FluidRegistrar.waterProps(MapColor.ICE));

    public static final FluidRegistrar.FluidEntry LIGHTNING_BLOOD = REG.entry("lightning_blood",
            1500, 2000, 350, 0xFF8A2BE2,
            FluidRegistrar.waterProps(MapColor.COLOR_YELLOW));

    // ---- 熔融龙钢（冰火龙钢锭熔炼） ----
    public static final FluidRegistrar.FluidEntry MOLTEN_DRAGONSTEEL_FIRE = REG.entry("molten_dragonsteel_fire",
            2000, 10000, 1300, 0xFFFF4500,
            FluidRegistrar.lavaProps(MapColor.COLOR_ORANGE));

    public static final FluidRegistrar.FluidEntry MOLTEN_DRAGONSTEEL_ICE = REG.entry("molten_dragonsteel_ice",
            2000, 10000, 1300, 0xFF00BFFF,
            FluidRegistrar.lavaProps(MapColor.ICE));

    public static final FluidRegistrar.FluidEntry MOLTEN_DRAGONSTEEL_LIGHTNING = REG.entry("molten_dragonsteel_lightning",
            2000, 10000, 1300, 0xFF8A2BE2,
            FluidRegistrar.lavaProps(MapColor.COLOR_YELLOW));

    public static final FluidRegistrar.FluidEntry MOLTEN_GHOST_INGOT = REG.entry("molten_ghost_ingot",
            2000, 10000, 800, 0xFFE5FFFA,
            FluidRegistrar.lavaProps(MapColor.COLOR_LIGHT_GREEN));

    // Forge fluid temperature is in kelvin; Tinkers subtracts 300 for its Celsius recipes.
    public static final FluidRegistrar.FluidEntry MOLTEN_PIXIE_SILVER = REG.entry("molten_pixie_silver",
            2000, 10000, 1100, 0xFFEFA2D7,
            FluidRegistrar.lavaProps(MapColor.COLOR_PINK));

    public static final FluidRegistrar.FluidEntry MOLTEN_SAPPHIRE = REG.entry("molten_sapphire",
            2000, 10000, 1450, 0xFF1C48DA, FluidRegistrar.lavaProps(MapColor.COLOR_BLUE));
    public static final FluidRegistrar.FluidEntry CRYSTAL_ESSENCE = REG.entry("crystal_essence",
            2000, 10000, 1750, 0xFF5BE1DF, FluidRegistrar.lavaProps(MapColor.COLOR_CYAN));

    // ---- 悚怖（冰火悚怖碎片） ----
    public static final FluidRegistrar.FluidEntry MOLTEN_DREAD = REG.entry("molten_dread",
            1500, 5000, 800, 0xFF6A0DAD,
            FluidRegistrar.lavaProps(MapColor.COLOR_PURPLE));

    public static final FluidRegistrar.FluidEntry MOLTEN_DREAD_SOULSTEEL = REG.entry("molten_dread_soulsteel",
            2000, 10000, 1900, 0xFF184F68, FluidRegistrar.lavaProps(MapColor.COLOR_BLUE));
    public static final FluidRegistrar.FluidEntry MOLTEN_DREADSTEEL = REG.entry("molten_dreadsteel",
            2000, 8000, 1700, 0xFF2F2F2F,
            FluidRegistrar.lavaProps(MapColor.COLOR_BLACK));

    /** 把本组四张注册表挂到模组总线（仅冰火在场时被调用） */
    public static void register(IEventBus bus) {
        REG.register(bus);
    }
}
