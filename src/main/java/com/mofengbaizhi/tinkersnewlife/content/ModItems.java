package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.*;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

public class ModItems {

    // ============================================================
    //  注册表
    // ============================================================

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TinkersNewlife.MOD_ID);

    // ============================================================
    //  基础材料
    // ============================================================

    public static final RegistryObject<Item> GHELOTH_REMAINS =
            ITEMS.register("gheloth_remains", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> NICHOLAS_BLESSING =
            ITEMS.register("nicholas_blessing", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> YELLOW_KING_REMNANT =
            ITEMS.register("yellow_king_remnant", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> RLYEH_CALL =
            ITEMS.register("rlyeh_call", () -> new Item(new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(10)
                            .saturationMod(0.5f)
                            .alwaysEat()
                            .effect(() -> new MobEffectInstance(MobEffects.ABSORPTION, 1200, 9), 1.0f)
                            .build()
                    )
            ));

    public static final RegistryObject<Item> ECHO_OF_THE_VOID =
            ITEMS.register("echo_of_the_void", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> ASTRAL_ANCHOR =
            ITEMS.register("astral_anchor", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> NEXUS_OF_SPACETIME =
            ITEMS.register("nexus_of_spacetime", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> YOG_SOTHOTH_GATE_KEY =
            ITEMS.register("yog_sothoth_gate_key", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> NYARLATHOTEP_DESIRE =
            ITEMS.register("nyarlathotep_desire", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> DURANDAL_SHARD =
        ITEMS.register("durandal_shard", () -> new Item(new Item.Properties().stacksTo(64)));

    // ============================================================
    //  矿石方块物品
    // ============================================================

    public static final RegistryObject<Item> GHELOTH_ORE =
            ITEMS.register("gheloth_ore", () -> new BlockItem(ModBlocks.GHELOTH_ORE.get(), new Item.Properties()));

    // ============================================================
    //  匠魂部件
    // ============================================================

    /** 龙魂核心（驯龙杖部件） */
    public static final RegistryObject<DragonCoreItem> DRAGON_CORE =
            ITEMS.register("dragon_core", () -> new DragonCoreItem(new Item.Properties()));

    /** 龙魂核心铸模（金铸模） */
    public static final RegistryObject<Item> DRAGON_CORE_CAST =
            ITEMS.register("dragon_core_cast", () -> new Item(new Item.Properties().stacksTo(1)));

    /** 龙魂核心铸模（沙铸模） */
    public static final RegistryObject<Item> DRAGON_CORE_SAND_CAST =
            ITEMS.register("dragon_core_sand_cast", () -> new Item(new Item.Properties().stacksTo(1)));

    /** 龙魂核心铸模（红沙铸模） */
    public static final RegistryObject<Item> DRAGON_CORE_RED_SAND_CAST =
            ITEMS.register("dragon_core_red_sand_cast", () -> new Item(new Item.Properties().stacksTo(1)));

    /** 噤默手套·核心部件（头部统计） */
    public static final RegistryObject<ToolPartItem> SILENT_GLOVE_CORE =
            ITEMS.register("silent_glove_core",
                    () -> new ToolPartItem(new Item.Properties(),
                            new MaterialStatsId(new ResourceLocation("tconstruct", "head"))
                    )
            );

    /** 噤默手套·皮面部件（手柄统计） */
    public static final RegistryObject<ToolPartItem> SILENT_GLOVE_BINDING =
            ITEMS.register("silent_glove_binding",
                    () -> new ToolPartItem(new Item.Properties(),
                            new MaterialStatsId(new ResourceLocation("tconstruct", "handle"))
                    )
            );

    public static final RegistryObject<ToolPartItem> DURANDAL_PART =
        ITEMS.register("durandal",
                () -> new ToolPartItem(new Item.Properties(),
                        new MaterialStatsId(new ResourceLocation("tconstruct", "head"))
                )
        );

    public static final RegistryObject<ToolPartItem> SPELL_CORE =
        ITEMS.register("spell_core",
                () -> new ToolPartItem(
                        new Item.Properties(),
                        new MaterialStatsId(new ResourceLocation("tconstruct", "head"))
                )
        );

    public static final RegistryObject<Item> SPELL_CORE_CAST =
            ITEMS.register("spell_core_cast", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SPELL_CORE_SAND_CAST =
            ITEMS.register("spell_core_sand_cast", () -> new Item(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> SPELL_CORE_RED_SAND_CAST =
            ITEMS.register("spell_core_red_sand_cast", () -> new Item(new Item.Properties().stacksTo(1)));

    // ============================================================
    //  工具
    // ============================================================

    /** 驯龙杖 */
    public static final RegistryObject<DragonStaffItem> DRAGON_STAFF =
            ITEMS.register("dragon_staff",
                    () -> new DragonStaffItem(new Item.Properties().stacksTo(1))
            );

    /** 噤默手套 */
    public static final RegistryObject<SilentGloveItem> SILENT_GLOVE =
            ITEMS.register("silent_glove",
                    () -> new SilentGloveItem(new Item.Properties().stacksTo(1))
            );

    public static final RegistryObject<WarScytheItem> WAR_SCYTHE =
        ITEMS.register("war_scythe",
                () -> new WarScytheItem(
                        new Item.Properties().stacksTo(1),
                        2.0f,   // 攻击距离 +2 格
                        0.006f  // 每点 Fever 增加 0.006 攻击速度（100 Fever 时 +0.6）
                ));

    /** 模块化魔杖 */
        public static final RegistryObject<ModularStaffItem> MODULAR_STAFF =
                ITEMS.register("modular_staff",
                        () -> new ModularStaffItem(new Item.Properties().stacksTo(1))
                );

        public static final RegistryObject<FlyingSwordItem> FLYING_SWORD =
        ITEMS.register("flying_sword",
                () -> new FlyingSwordItem(new Item.Properties().stacksTo(1))); // 耐久可自行调整

        public static final RegistryObject<DurandalSwordItem> DURANDAL_SWORD =
        ITEMS.register("durandal_sword",
                () -> new DurandalSwordItem(new Item.Properties()
                        .stacksTo(1)
                        .fireResistant()
                        .rarity(Rarity.EPIC)
                ));
}