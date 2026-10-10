package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.google.gson.JsonObject;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import com.mofengbaizhi.tinkersnewlife.content.energy.ElderCrystalStorage;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * <b>1 古老者水晶方块 → 4 古老者水晶</b>（**自定义拆分配方**，EE 平分且一点都不丢 ✓ §532）。
 *
 * <h2>为什么不能用原版配方</h2>
 * 同 {@link ElderCrystalMergeRecipe}：原版配方的产物是 JSON 里写死的 ItemStack ✗
 * ⇒ 方块里的 EE 会被静默丢掉 ✗。所以自己写配方类 + 序列化器 ✓。
 *
 * <h2>怎么做到"1 个配方给 4 个产物、且各自 EE 不同"</h2>
 * 原版 {@code assemble} 只能返回**一个** ItemStack ✗ ⇒ 这里分成两半：
 * <ul>
 *   <li>{@link #assemble} 返回**第 0 颗**水晶 ✓；</li>
 *   <li>{@link #getRemainingItems} 返回**第 1~3 颗**水晶 ✓ —— 这正是原版"合成剩余物"（桶、瓶）走的那条路，
 *       工作台会自动把它们塞回空的合成格 / 给玩家 ✓ ⇒ 玩家拿到 4 颗 ✓。</li>
 * </ul>
 * EE 分配：{@code base = ee / 4}、{@code rem = ee % 4} ⇒ 前 {@code rem} 颗拿 {@code base + 1}、其余拿 {@code base} ✓
 * ⇒ <b>4 颗之和恒等于原方块的 EE</b> ✓（余数不会丢 ✓ 这正是"不丢"的关键 ✗ 别改成整除后丢弃 ✗）。
 *
 * <p>与合并配方**互逆**：4 水晶 → 1 方块（求和）✓ / 1 方块 → 4 水晶（平分）✓ ⇒ 来回搬运 EE 守恒 ✓。
 * <p>{@code isSpecial() = true}（继承自 {@link CustomRecipe}）⇒ 不进配方书 / 不被自动合成 ✗。
 */
public class ElderCrystalSplitRecipe extends CustomRecipe {

    /** 拆出来的水晶个数（= 合并配方需要的个数 ✓ 两者必须一致才互逆 ✓） */
    public static final int CRYSTAL_COUNT = ElderCrystalMergeRecipe.CRYSTAL_COUNT;

    public ElderCrystalSplitRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    // ============================================================
    //  匹配：清清楚楚 1 个水晶方块，别的什么都不要
    // ============================================================

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            // 只认方块物品本体：水晶物品不参与（4 水晶 → 1 方块 是另一条配方 ✓）
            if (stack.getItem() != ModItems.ELDER_CRYSTAL_BLOCK.get()) return false;
            total += stack.getCount();
            if (total > 1) return false;          // 多于 1 个方块：不匹配 ✓
        }
        return total == 1;
    }

    // ============================================================
    //  合成：第 0 颗（assemble）+ 第 1~3 颗（getRemainingItems）
    // ============================================================

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        return crystal(eeOfInput(container), 0);
    }

    /**
     * 合成剩余物 = 第 1~3 颗水晶 ✓
     * <p>⚠ 每一颗都按自己的序号 {@code k} 分配 EE（见类注释的分配规则 ✓），这样 4 颗之和 = 原 EE ✓。
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> out = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        int ee = eeOfInput(container);
        for (int k = 1; k < CRYSTAL_COUNT; k++) {
            if (k < out.size()) {
                out.set(k, crystal(ee, k));
            }
        }
        return out;
    }

    /** 2×2 起（4 格）—— 因为要放得下 4 颗剩余物 ✓（1×2 之类放不下 ⇒ 不做 ✗） */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= CRYSTAL_COUNT;
    }

    /** JEI / 配方显示：材料 = 1 个方块 ✓ */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(Ingredient.of(ModItems.ELDER_CRYSTAL_BLOCK.get()));
        return list;
    }

    /** 展示用产物：第 0 颗（EE 取"平分值"，不虚报 ✓） */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return new ItemStack(ModItems.ELDER_CRYSTAL.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ELDER_CRYSTAL_SPLIT.get();
    }

    // ============================================================
    //  工具
    // ============================================================

    /** 读输入那个方块物品里的 EE（找不到就是 0 ✓） */
    private static int eeOfInput(CraftingContainer container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty() && stack.getItem() == ModItems.ELDER_CRYSTAL_BLOCK.get()) {
                return ElderCrystalStorage.getBlockItemEe(stack);
            }
        }
        return 0;
    }

    /**
     * 第 {@code index} 颗水晶（0~3）—— 按 {@code base/rem} 规则分配 EE ✓（见类注释 ✓）。
     */
    private static ItemStack crystal(int totalEe, int index) {
        int base = totalEe / CRYSTAL_COUNT;
        int rem = totalEe % CRYSTAL_COUNT;
        int ee = base + (index < rem ? 1 : 0);
        ItemStack stack = new ItemStack(ModItems.ELDER_CRYSTAL.get());
        ElderCrystalStorage.setCrystalEe(stack, Math.min(ee, ElderCrystalStorage.CRYSTAL_CAPACITY));
        return stack;
    }

    // ============================================================
    //  序列化器
    // ============================================================

    public static class Serializer implements RecipeSerializer<ElderCrystalSplitRecipe> {

        @Override
        public ElderCrystalSplitRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ElderCrystalSplitRecipe(id, categoryOf(json));
        }

        @Override
        public ElderCrystalSplitRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ElderCrystalSplitRecipe(id, buf.readEnum(CraftingBookCategory.class));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ElderCrystalSplitRecipe recipe) {
            buf.writeEnum(recipe.category());
        }

        private static CraftingBookCategory categoryOf(JsonObject json) {
            if (!json.has("category")) return CraftingBookCategory.MISC;
            CraftingBookCategory cat = CraftingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category"));
            return cat == null ? CraftingBookCategory.MISC : cat;
        }
    }

    /** 供外部（JEI 之类）判断"这条配方是不是我们的拆分配方" */
    public static boolean isSplitRecipe(@Nullable net.minecraft.world.item.crafting.Recipe<?> recipe) {
        return recipe instanceof ElderCrystalSplitRecipe;
    }
}
