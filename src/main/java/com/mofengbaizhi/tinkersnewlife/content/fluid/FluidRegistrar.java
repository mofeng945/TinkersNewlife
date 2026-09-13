package com.mofengbaizhi.tinkersnewlife.content.fluid;

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
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 一组<b>成套</b>的流体注册器：FluidType + 静止流体 + 流动流体 + 液体方块 + 桶。
 *
 * <p>⭐ 为什么要有它：联动材料的流体必须能与对应模组"同生共死"（模组不在场 → 流体/方块/桶整组不注册），
 * 而 {@code DeferredRegister} 是"一个实例挂一次总线"的，所以每个联动模块需要自己的一套注册器，
 * 并且要在<b>同一个条件分支</b>里把四张注册表一起挂上去——绝不出现"流体注册了但桶没注册"的半残状态。
 * 本模组原生流体继续用 {@code ModFluids} 里的公共注册器。
 */
public final class FluidRegistrar {

    public final DeferredRegister<Fluid> fluids;
    public final DeferredRegister<FluidType> types;
    public final DeferredRegister<Block> blocks;
    public final DeferredRegister<Item> buckets;

    public FluidRegistrar(String modId) {
        this.fluids = DeferredRegister.create(ForgeRegistries.FLUIDS, modId);
        this.types = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, modId);
        this.blocks = DeferredRegister.create(ForgeRegistries.BLOCKS, modId);
        this.buckets = DeferredRegister.create(ForgeRegistries.ITEMS, modId);
    }

    /** 把四张注册表一起挂到模组总线（只有本组流体会被注册） */
    public void register(IEventBus bus) {
        types.register(bus);
        blocks.register(bus);
        buckets.register(bus);
        fluids.register(bus);
    }

    /** 注册一个完整流体（type / 静止 / 流动 / 液体方块 / 桶） */
    public FluidEntry entry(String name, int density, int viscosity, int temperature,
                            int tintColor, BlockBehaviour.Properties blockProps) {
        return new FluidEntry(this, name, density, viscosity, temperature, tintColor, blockProps);
    }

    /** 使用 JSON 纹理配置的 MantleFluidType */
    static MantleFluidType createFluidType(String name, int density, int viscosity,
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

    /** 水性质液体方块（可游泳、可乘船） */
    public static BlockBehaviour.Properties waterProps(MapColor color) {
        return BlockBehaviour.Properties.copy(Blocks.WATER).mapColor(color).noLootTable();
    }

    /** 岩浆性质液体方块（发光、灼烧） */
    public static BlockBehaviour.Properties lavaProps(MapColor color) {
        return BlockBehaviour.Properties.copy(Blocks.LAVA).mapColor(color).noLootTable();
    }

    /** 一个完整流体的五个注册对象 */
    public static final class FluidEntry {
        public final RegistryObject<FluidType> type;
        public final RegistryObject<FlowingFluid> still;
        public final RegistryObject<FlowingFluid> flowing;
        public final RegistryObject<LiquidBlock> block;
        public final RegistryObject<BucketItem> bucket;

        private FluidEntry(FluidRegistrar reg, String name, int density, int viscosity, int temperature,
                           int tintColor, BlockBehaviour.Properties blockProps) {
            // 1. FluidType
            this.type = reg.types.register(name,
                    () -> createFluidType(name, density, viscosity, temperature, tintColor));

            // 2. 用数组延迟引用 still / flowing（注册顺序上先建 props，注册对象后补）
            RegistryObject<FlowingFluid>[] stillHolder = new RegistryObject[1];
            RegistryObject<FlowingFluid>[] flowingHolder = new RegistryObject[1];

            // 3. Fluid Properties
            ForgeFlowingFluid.Properties props = new ForgeFlowingFluid.Properties(
                    this.type,
                    () -> stillHolder[0].get(),
                    () -> flowingHolder[0].get()
            );

            // 4. 液体方块
            this.block = reg.blocks.register(name + "_block",
                    () -> new LiquidBlock(() -> stillHolder[0].get(), blockProps));

            // 5. 桶
            this.bucket = reg.buckets.register(name + "_bucket",
                    () -> new BucketItem(() -> stillHolder[0].get(),
                            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

            // 6. 绑定 block / bucket
            props.block(this.block).bucket(this.bucket);

            // 7. 静止 + 流动
            this.still = reg.fluids.register(name + "_still",
                    () -> new ForgeFlowingFluid.Source(props));
            this.flowing = reg.fluids.register(name + "_flowing",
                    () -> new ForgeFlowingFluid.Flowing(props));

            // 8. 完成延迟绑定
            stillHolder[0] = this.still;
            flowingHolder[0] = this.flowing;
        }

        /** 桶物品（未注册时为 null，调用方需判空） */
        public Item bucketOrNull() {
            return this.bucket.isPresent() ? this.bucket.get() : null;
        }
    }
}
