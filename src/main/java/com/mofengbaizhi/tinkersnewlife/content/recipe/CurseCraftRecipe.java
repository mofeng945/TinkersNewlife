package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 咒力合成仪式配方（{@code data/<ns>/recipes/curse_craft/*.json}）。
 *
 * <h2>JSON 格式</h2>
 * <pre>{@code
 * {
 *   "type": "tinkersnewlife:curse_craft",
 *   "core":      { "item": "tinkersnewlife:nyarlathotep_desire" },   // 核心古神物品（放到格赫罗斯矿石上方的灯笼）
 *   "result":    { "item": "tinkersnewlife:curse_bottle" },          // 产物
 *   "materials": [ { "item": "minecraft:glass_bottle" },             // 材料（≤8 个，不要求放满）
 *                  { "item": "tinkersnewlife:boundary_fragment" } ],
 *   "curse": 500                                                      // 咒力需求量
 * }
 * }</pre>
 * Ingredient 支持原版的 {@code {"item": ...}} / {@code {"tag": ...}} / 数组写法 ✓。
 */
public class CurseCraftRecipe implements Recipe<net.minecraft.world.inventory.CraftingContainer> {

    /** 材料展示位数量上限（结构里石砖墙上的灯笼数） */
    public static final int MAX_MATERIALS = 8;

    private final ResourceLocation id;
    private final Ingredient core;
    private final ItemStack result;
    private final NonNullList<Ingredient> materials;
    /** 咒力需求量（仪式每 tick 吸收 10 点，所以耗时 = curse / 10 tick） */
    private final double curse;

    public CurseCraftRecipe(ResourceLocation id, Ingredient core, ItemStack result,
                            List<Ingredient> materials, double curse) {
        this.id = id;
        this.core = core;
        this.result = result;
        this.materials = NonNullList.create();
        this.materials.addAll(materials);
        this.curse = Math.max(0, curse);
    }

    public Ingredient core() {
        return core;
    }

    public List<Ingredient> materials() {
        return materials;
    }

    public double curse() {
        return curse;
    }

    /** 产物（JEI 展示用；无需 RegistryAccess） */
    public ItemStack result() {
        return result;
    }

    /** 每 tick 吸收的咒力 */
    public static final double CURSE_PER_TICK = 10.0;

    /** 需要的 tick 数 */
    public int durationTicks() {
        return (int) Math.ceil(curse / CURSE_PER_TICK);
    }

    // ============================================================
    //  Recipe
    // ============================================================

    @Override
    public boolean matches(net.minecraft.world.inventory.CraftingContainer container, Level level) {
        return false;   // 不走容器匹配：由仪式（CurseCraftRitualHandler）自行判定材料
    }

    @Override
    public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer container, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> all = NonNullList.create();
        all.add(core);
        all.addAll(materials);
        return all;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CURSE_CRAFT.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeSerializers.CURSE_CRAFT_TYPE.get();
    }

    // ============================================================
    //  序列化
    // ============================================================

    public static class Serializer implements RecipeSerializer<CurseCraftRecipe> {

        @Override
        public CurseCraftRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient core = Ingredient.fromJson(GsonHelper.getAsJsonObject(json, "core"));
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            JsonArray array = GsonHelper.getAsJsonArray(json, "materials", new JsonArray());
            List<Ingredient> materials = new ArrayList<>();
            for (JsonElement element : array) {
                if (materials.size() >= MAX_MATERIALS) {
                    throw new JsonSyntaxException("最多 " + MAX_MATERIALS + " 个材料位: " + id);
                }
                materials.add(Ingredient.fromJson(element));
            }
            if (materials.isEmpty()) {
                throw new JsonSyntaxException("curse_craft 配方至少需要一个材料: " + id);
            }
            double curse = GsonHelper.getAsDouble(json, "curse", 0.0);
            return new CurseCraftRecipe(id, core, result, materials, curse);
        }

        @Nullable
        @Override
        public CurseCraftRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient core = Ingredient.fromNetwork(buf);
            ItemStack result = buf.readItem();
            int size = buf.readVarInt();
            List<Ingredient> materials = new ArrayList<>(size);
            for (int i = 0; i < size; i++) materials.add(Ingredient.fromNetwork(buf));
            double curse = buf.readDouble();
            return new CurseCraftRecipe(id, core, result, materials, curse);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, CurseCraftRecipe recipe) {
            recipe.core.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.materials.size());
            for (Ingredient ingredient : recipe.materials) ingredient.toNetwork(buf);
            buf.writeDouble(recipe.curse);
        }
    }

    @Override
    public String toString() {
        return "CurseCraftRecipe[" + TinkersNewlife.MOD_ID + ":" + id.getPath() + " -> " + result + "]";
    }
}
