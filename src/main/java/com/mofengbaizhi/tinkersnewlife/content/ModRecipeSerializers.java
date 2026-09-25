package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.recipe.AutoMaterialMeltingRecipe;
import com.mofengbaizhi.tinkersnewlife.content.recipe.ChargedCreeperMeltingRecipe;
import com.mofengbaizhi.tinkersnewlife.content.recipe.CrystalModifierRecipe;
import com.mofengbaizhi.tinkersnewlife.content.recipe.GenericToolMeltingRecipe;
import com.mofengbaizhi.tinkersnewlife.content.recipe.TagModifierSalvage;
import com.mofengbaizhi.tinkersnewlife.content.recipe.CurseCraftRecipe;
import com.mofengbaizhi.tinkersnewlife.content.recipe.ElderCrystalMergeRecipe;
import com.mofengbaizhi.tinkersnewlife.content.recipe.ElderCrystalSplitRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;

/**
 * 自定义配方序列化器注册
 */
public class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TinkersNewlife.MOD_ID);

    /** 自定义配方类型注册表（咒力合成仪式） */
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, TinkersNewlife.MOD_ID);

    /** 咒力合成仪式：data/<ns>/recipes/curse_craft/*.json */
    public static final RegistryObject<RecipeType<CurseCraftRecipe>> CURSE_CRAFT_TYPE =
            RECIPE_TYPES.register("curse_craft", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return TinkersNewlife.MOD_ID + ":curse_craft";
                }
            });

    public static final RegistryObject<RecipeSerializer<CurseCraftRecipe>> CURSE_CRAFT =
            RECIPE_SERIALIZERS.register("curse_craft", CurseCraftRecipe.Serializer::new);

    /** 仅水晶添加的修饰符配方（术式/领域专用，见 CrystalModifierRecipe） */
    public static final RegistryObject<RecipeSerializer<CrystalModifierRecipe>> CRYSTAL_MODIFIER =
            RECIPE_SERIALIZERS.register("crystal_modifier",
                    () -> LoadableRecipeSerializer.of(CrystalModifierRecipe.LOADER));

    /** 万能材料熔化配方（一条配方自动覆盖所有流体材料，见 AutoMaterialMeltingRecipe） */
    public static final RegistryObject<RecipeSerializer<AutoMaterialMeltingRecipe>> AUTO_MATERIAL_MELTING =
            RECIPE_SERIALIZERS.register("auto_material_melting",
                    () -> LoadableRecipeSerializer.of(AutoMaterialMeltingRecipe.LOADER));

    /** 通用匠魂工具熔化配方（读取工具所有部件材料，按最高融化温度熔化，见 GenericToolMeltingRecipe） */
    public static final RegistryObject<RecipeSerializer<GenericToolMeltingRecipe>> TOOL_MELTING =
            RECIPE_SERIALIZERS.register("tool_melting",
                    () -> LoadableRecipeSerializer.of(GenericToolMeltingRecipe.LOADER));

    /**
     * 实体熔炼「闪电苦力怕 → 液态闪电」（见 {@link ChargedCreeperMeltingRecipe}）。
     *
     * <p>对应的数据文件是 <b>{@code data/tconstruct/recipes/smeltery/entity_melting/creeper.json}</b>
     * —— **同 ID 覆盖**匠魂自带那条（否则两条会抢，见类注释 ✓）；普通苦力怕仍是熔融玻璃 50 mB/damage 2 ✓。
     */
    public static final RegistryObject<RecipeSerializer<ChargedCreeperMeltingRecipe>> CHARGED_CREEPER_MELTING =
            RECIPE_SERIALIZERS.register("charged_creeper_melting",
                    () -> LoadableRecipeSerializer.of(ChargedCreeperMeltingRecipe.LOADER));

    /** 修饰符槽位返还（术式/领域通用，按 modifier tag 一步到位，见 TagModifierSalvage） */
    public static final RegistryObject<RecipeSerializer<TagModifierSalvage>> TAG_MODIFIER_SALVAGE =
            RECIPE_SERIALIZERS.register("modifier_salvage",
                    () -> LoadableRecipeSerializer.of(TagModifierSalvage.LOADER));

    // ============================================================
    //  古老者水晶合并（§519 P1）：4 水晶 → 1 水晶方块，EE 求和保 NBT ✓
    // ============================================================

    /**
     * 我们的配方类型标识 {@code tinkersnewlife:elder_crystal_merge}。
     *
     * <p>⚠ <b>真正的合成走的是 {@code minecraft:crafting}</b> ——
     * {@link com.mofengbaizhi.tinkersnewlife.content.recipe.ElderCrystalMergeRecipe}
     * 继承 {@code CustomRecipe}，{@code getType()} 返回 {@code RecipeType.CRAFTING}
     * （否则工作台按 {@code RecipeType.CRAFTING} 查表时根本找不到它 ✗）。
     * 这个类型留给"按类型认出我们的合并配方"的用途 ✓（注册本身无副作用 ✓）。
     */
    public static final RegistryObject<RecipeType<ElderCrystalMergeRecipe>> ELDER_CRYSTAL_MERGE_TYPE =
            RECIPE_TYPES.register("elder_crystal_merge", () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return TinkersNewlife.MOD_ID + ":elder_crystal_merge";
                }
            });

    /** 合并配方的序列化器（data/<ns>/recipes/*.json 里的 {@code "type"} 就是它 ✓） */
    public static final RegistryObject<RecipeSerializer<ElderCrystalMergeRecipe>> ELDER_CRYSTAL_MERGE =
            RECIPE_SERIALIZERS.register("elder_crystal_merge",
                    ElderCrystalMergeRecipe.Serializer::new);

    // ============================================================
    //  古老者水晶拆分（§532）：1 方块 → 4 水晶，EE 平分不丢 ✓（与合并配方互逆 ✓）
    // ============================================================

    /** 拆分配方的序列化器（data/<ns>/recipes/*.json 里的 {@code "type"} 就是它 ✓） */
    public static final RegistryObject<RecipeSerializer<ElderCrystalSplitRecipe>> ELDER_CRYSTAL_SPLIT =
            RECIPE_SERIALIZERS.register("elder_crystal_split",
                    ElderCrystalSplitRecipe.Serializer::new);
}
