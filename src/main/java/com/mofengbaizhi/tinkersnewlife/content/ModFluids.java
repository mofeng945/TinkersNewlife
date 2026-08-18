package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.fluid.type.MantleFluidType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModFluids {
    // ============================================================
    // 注册表
    // ============================================================
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(ForgeRegistries.FLUIDS, TinkersNewlife.MOD_ID);
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, TinkersNewlife.MOD_ID);
    public static final DeferredRegister<Block> FLUID_BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TinkersNewlife.MOD_ID);
    public static final DeferredRegister<Item> FLUID_BUCKETS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TinkersNewlife.MOD_ID);

    // ============================================================
    // 辅助方法：创建 MantleFluidType（使用 JSON 纹理配置）
    // ============================================================
    private static MantleFluidType createFluidType(String name, int density, int viscosity,
                                                   int temperature, int tintColor) {
        return new MantleFluidType(
                FluidType.Properties.create()
                        .density(density)
                        .viscosity(viscosity)
                        .temperature(temperature)
                        .canPushEntity(false)
                        .canDrown(true)
                        .canExtinguish(false)
                        .supportsBoating(true)
                        .descriptionId("fluid_type." + TinkersNewlife.MOD_ID + "." + name), // 匹配语言文件
                new ResourceLocation(TinkersNewlife.MOD_ID, "block/" + name + "_still"),   // 默认纹理（后备）
                tintColor
        );
    }

    private static BlockBehaviour.Properties waterProps(MapColor color) {
        return BlockBehaviour.Properties.copy(Blocks.WATER).mapColor(color).noLootTable();
    }

    private static BlockBehaviour.Properties lavaProps(MapColor color) {
        return BlockBehaviour.Properties.copy(Blocks.LAVA).mapColor(color).noLootTable();
    }

    // ============================================================
    // 内部类：封装一个完整流体的注册（使用 Supplier 避免循环依赖）
    // ============================================================
    public static class FluidEntry {
        public final RegistryObject<FluidType> type;
        public final RegistryObject<FlowingFluid> still;
        public final RegistryObject<FlowingFluid> flowing;
        public final RegistryObject<LiquidBlock> block;
        public final RegistryObject<BucketItem> bucket;

        public FluidEntry(String name, int density, int viscosity, int temperature,
                          int tintColor, BlockBehaviour.Properties blockProps) {
            // 1. 创建 FluidType
            this.type = FLUID_TYPES.register(name,
                    () -> createFluidType(name, density, viscosity, temperature, tintColor));

            // 2. 使用数组延迟引用 still 和 flowing
            RegistryObject<FlowingFluid>[] stillHolder = new RegistryObject[1];
            RegistryObject<FlowingFluid>[] flowingHolder = new RegistryObject[1];

            // 3. 创建 Fluid Properties
            ForgeFlowingFluid.Properties props = new ForgeFlowingFluid.Properties(
                    this.type,
                    () -> stillHolder[0].get(),
                    () -> flowingHolder[0].get()
            );

            // 4. 注册 Block
            this.block = FLUID_BLOCKS.register(name + "_block",
                    () -> new LiquidBlock(() -> stillHolder[0].get(), blockProps));

            // 5. 注册 Bucket
            this.bucket = FLUID_BUCKETS.register(name + "_bucket",
                    () -> new BucketItem(() -> stillHolder[0].get(),
                            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

            // 6. 绑定 block 和 bucket
            props.block(this.block).bucket(this.bucket);

            // 7. 注册 still 和 flowing
            this.still = FLUIDS.register(name + "_still",
                    () -> new ForgeFlowingFluid.Source(props));
            this.flowing = FLUIDS.register(name + "_flowing",
                    () -> new ForgeFlowingFluid.Flowing(props));

            // 8. 完成延迟绑定
            stillHolder[0] = this.still;
            flowingHolder[0] = this.flowing;
        }
    }

    // ============================================================
    // 注册所有流体
    // ============================================================

    public static final FluidEntry GHELOTH_BLOOD = new FluidEntry("gheloth_blood",
            1500, 2000, 300, 0xFFFF4500,
            waterProps(MapColor.CRIMSON_STEM));

    public static final FluidEntry MOLTEN_DRAGONSTEEL_FIRE = new FluidEntry("molten_dragonsteel_fire",
            2000, 10000, 1300, 0xFFFF4500,
            lavaProps(MapColor.COLOR_ORANGE));

    public static final FluidEntry MOLTEN_DRAGONSTEEL_ICE = new FluidEntry("molten_dragonsteel_ice",
            2000, 10000, 1300, 0xFF00BFFF,
            lavaProps(MapColor.ICE));

    public static final FluidEntry MOLTEN_DRAGONSTEEL_LIGHTNING = new FluidEntry("molten_dragonsteel_lightning",
            2000, 10000, 1300, 0xFF8A2BE2,
            lavaProps(MapColor.COLOR_YELLOW));

    public static final FluidEntry FIRE_BLOOD = new FluidEntry("fire_blood",
            1500, 2000, 400, 0xFFFF4500,
            waterProps(MapColor.FIRE));

    public static final FluidEntry ICE_BLOOD = new FluidEntry("ice_blood",
            1500, 2000, 250, 0xFF00BFFF,
            waterProps(MapColor.ICE));

    public static final FluidEntry LIGHTNING_BLOOD = new FluidEntry("lightning_blood",
            1500, 2000, 350, 0xFF8A2BE2,
            waterProps(MapColor.COLOR_YELLOW));

    public static final FluidEntry MOLTEN_DREAD = new FluidEntry("molten_dread",
            1500, 5000, 800, 0xFF6A0DAD,
            lavaProps(MapColor.COLOR_PURPLE));

    public static final FluidEntry MOLTEN_DREADSTEEL = new FluidEntry("molten_dreadsteel",
            2000, 8000, 1100, 0xFF2F2F2F,
            lavaProps(MapColor.COLOR_BLACK));
            
    public static final FluidEntry MOLTEN_NICHOLAS_BLESSING = new FluidEntry("molten_nicholas_blessing",
            2000, 6000, 800, 0xFF9B59B6,
            lavaProps(MapColor.COLOR_PURPLE));

    public static final FluidEntry HASTUR_MALICE = new FluidEntry("hastur_malice",
            1500, 2000, 300, 0xFF4B0082,
            waterProps(MapColor.COLOR_PURPLE));

    public static final FluidEntry ASHEN_INK = new FluidEntry("ashen_ink",
            1500, 2000, 300, 0xFFC0C0C0,
            waterProps(MapColor.COLOR_LIGHT_GRAY));

    public static final FluidEntry MOLTEN_DURANDAL = new FluidEntry("molten_durandal",
        2000, 10000, 1500, 0xFFFFD700,  // 金黄色
        lavaProps(MapColor.COLOR_ORANGE));
}