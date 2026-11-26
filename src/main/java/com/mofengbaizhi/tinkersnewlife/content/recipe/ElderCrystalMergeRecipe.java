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
 * <b>4 水晶 → 1 古老者水晶方块</b>（<b>自定义合并配方</b>，保 NBT ✓）。
 *
 * <h2>⚠ 为什么不能用原版无序配方（{@code minecraft:crafting_shapeless}）</h2>
 * 原版配方的产物是 <b>JSON 里写死的那个 ItemStack</b> ✗ ——
 * 合成时由 {@code ShapelessRecipe#assemble} 直接 {@code getResultItem().copy()} 返回，
 * <b>根本不会去看材料上的 NBT</b> ✗ ⇒ 4 个水晶里存的 EE 会被静默丢掉 ✗（用户明确点名了这个坑）。
 * 所以必须自己写配方类 + 自己的序列化器 ✓：{@code matches} 认材料、{@code assemble} 把
 * <b>4 个水晶的已存 EE 求和</b>写进产物的 {@code BlockEntityTag.EE} ✓。
 *
 * <h2>为什么不丢一点 EE（可证）</h2>
 * 单个水晶容量 {@link ElderCrystalStorage#CRYSTAL_CAPACITY} = 1000，方块容量
 * {@link ElderCrystalStorage#BLOCK_CAPACITY} = 4000 = <b>正好 4 × 1000</b> ✓ ⇒
 * "4 个水晶的 EE 之和" 恒在 {@code [0, 4000]} 内 ⇒ 写进方块时<b>永远不会被夹</b> ✓
 * （不需要"装不下退回"的补丁 ✗）。
 *
 * <h2>配方类型：为什么 {@code getType()} 是 {@code minecraft:crafting}</h2>
 * 工作台的查表是 {@code RecipeManager#getRecipeFor(RecipeType.CRAFTING, …)}，
 * 而 {@code RecipeManager} 是按 <b>{@code recipe.getType()}</b> 建索引的 ⇒
 * 配方要能用在工作台里，{@code getType()} <b>必须</b>返回 {@code RecipeType.CRAFTING} ✓
 * （这也是原版自己那几条 NBT 配方 {@code ArmorDyeRecipe} / {@code ShulkerBoxColoring} 的做法 ✓
 * —— 它们同样继承 {@link CustomRecipe}）。
 * 我们另注册的 {@code tinkersnewlife:elder_crystal_merge} <b>RecipeType</b>
 * （见 {@link ModRecipeSerializers#ELDER_CRYSTAL_MERGE_TYPE}）留给"按类型识别本配方"的用处 ✓；
 * 而 JSON 里的 {@code "type"} 写的是<b>序列化器</b>名 {@code tinkersnewlife:elder_crystal_merge} ✓
 * —— 两者不要混淆（type 字段选的是序列化器，索引用的是 getType()）。
 *
 * <p>{@code isSpecial() = true}（继承自 {@link CustomRecipe}）⇒ 不进配方书 / 不会被自动合成 ✗。
 */
public class ElderCrystalMergeRecipe extends CustomRecipe {

    /** 需要的水晶个数 */
    public static final int CRYSTAL_COUNT = 4;

    public ElderCrystalMergeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    // ============================================================
    //  匹配：清清楚楚 4 个水晶，别的什么都不要
    // ============================================================

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            // 只认"水晶物品"本体：方块物品不参与（4 水晶 → 1 方块 ✓ 不是 4 方块 → 1 个更大的 ✗）
            if (stack.getItem() != ModItems.ELDER_CRYSTAL.get()) return false;
            total += stack.getCount();
            if (total > CRYSTAL_COUNT) return false;   // 多于 4 个：不匹配 ✓
        }
        return total == CRYSTAL_COUNT;
    }

    // ============================================================
    //  合成：把 4 个水晶的 EE 求和写进方块
    // ============================================================

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        int sum = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty() || stack.getItem() != ModItems.ELDER_CRYSTAL.get()) continue;
            // 一格里可能有多个（同一格里的 NBT 是同一份 ⇒ 每个水晶的 EE 相同 ✓）
            sum += ElderCrystalStorage.getCrystalEe(stack) * stack.getCount();
        }
        ItemStack out = new ItemStack(ModItems.ELDER_CRYSTAL_BLOCK.get());
        // 直接夹一次：4 × 1000 = 4000 = 方块容量 ⇒ 数学上永远不会夹掉东西 ✓（见类注释）
        ElderCrystalStorage.setBlockItemEe(out, Math.min(sum, ElderCrystalStorage.BLOCK_CAPACITY));
        return out;
    }

    /** 2×2 起就能放下 4 个水晶 ✓（1×1、1×2 之类放不下 ⇒ 不做 ✗） */
    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= CRYSTAL_COUNT;
    }

    /**
     * JEI / 配方显示用：4 个"古老者水晶"。
     * <p>⚠ 只影响<b>显示</b>（{@code getIngredients} 不参与 {@link #matches} ✓），
     * 所以"任意摆法都行"这条语义不受影响 ✓（与匠魂工具建造一类的手册式展示同理）。
     */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (int i = 0; i < CRYSTAL_COUNT; i++) {
            list.add(Ingredient.of(ModItems.ELDER_CRYSTAL.get()));
        }
        return list;
    }

    /**
     * 展示用产物：空的水晶方块。
     * <p>真实产物的 EE 取决于材料（见 {@link #assemble} ✓），
     * 这里给"0 EE"的原样方块 —— 不虚报成满仓 ✗（JEI 的图标本来也不显示 EE ✓）。
     */
    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return new ItemStack(ModItems.ELDER_CRYSTAL_BLOCK.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.ELDER_CRYSTAL_MERGE.get();
    }

    // ============================================================
    //  序列化器
    // ============================================================

    public static class Serializer implements RecipeSerializer<ElderCrystalMergeRecipe> {

        @Override
        public ElderCrystalMergeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ElderCrystalMergeRecipe(id, categoryOf(json));
        }

        /** 网络包没有配方 id（服务端下发时会另带）⇒ 用 id=null 的占位实例 ✓ 与其它"全常量"配方一致 */
        @Override
        public ElderCrystalMergeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ElderCrystalMergeRecipe(id, buf.readEnum(CraftingBookCategory.class));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ElderCrystalMergeRecipe recipe) {
            buf.writeEnum(recipe.category());
        }

        /** 配方书分类：JSON 里可选写 {@code "category"}（不写 = misc ✓ 与原版一致） */
        private static CraftingBookCategory categoryOf(JsonObject json) {
            if (!json.has("category")) return CraftingBookCategory.MISC;
            CraftingBookCategory cat = CraftingBookCategory.CODEC.byName(GsonHelper.getAsString(json, "category"));
            return cat == null ? CraftingBookCategory.MISC : cat;
        }
    }

    /** 供外部（JEI 之类）判断"这条配方是不是我们的合并配方" */
    public static boolean isMergeRecipe(@Nullable net.minecraft.world.item.crafting.Recipe<?> recipe) {
        return recipe instanceof ElderCrystalMergeRecipe;
    }
}
