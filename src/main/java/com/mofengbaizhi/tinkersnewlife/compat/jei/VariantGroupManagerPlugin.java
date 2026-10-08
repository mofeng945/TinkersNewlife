package com.mofengbaizhi.tinkersnewlife.compat.jei;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ⭐ §1122 <b>同 id 变体索引 + JEI 管理器插件</b>（用户选的方案 B ✓）。
 *
 * <h2>为什么需要它</h2>
 * ⚠ JEI 原生的"子类型折叠"对**匠魂工具/部件无效** ✗ —— 匠魂自己的 JEI 插件早已给全部
 * {@code IModifiable} 物品注册过子类型 ✓，而 **JEI 15.48 对重复注册直接抛
 * {@code IllegalArgumentException}** ✗（实测 282 条 ERROR ✓ 名字正是
 * {@code sword / rapier / spear / whip / small_blade / tool_handle / bow_limb …}）⇒
 * ⭐ 于是改用本分类来"展开" ✓。
 *
 * <h2>索引怎么建（与 JEI 列表同源 ✓）</h2>
 * 遍历**创造栏的展示物品**（{@code CreativeModeTab#getDisplayItems()} ✓）✓
 * ⇒ 对"匠魂可改造物品"以及**本模组**的物品 ✓ 按
 * ⭐ <b>物品 id + {@code tic_materials} 材料组合</b>（{@link JeiVariantFolder#materialsKeyOf}）分组 ✓
 * ⇒ ⭐ 只保留**变体数 ≥ 2** 的组 ✓（单只的不值得单开一页 ✗）。
 * <p>⚠ 与 {@code ModCreativeTabs} 的注册内容**自动一致** ✓（都走创造栏 ✓ 不用维护第二份清单 ✓）。
 *
 * <h2>管理器插件的三个方法（JEI 15.48 实证 ✓）</h2>
 * <pre>
 *   &lt;V&gt; List&lt;RecipeType&lt;?&gt;&gt; getRecipeTypes(IFocus&lt;V&gt; focus);              // 这件物品归本分类 ⇒ 返回 TYPE ✓
 *   &lt;T,V&gt; List&lt;T&gt; getRecipes(IRecipeCategory&lt;T&gt; category, IFocus&lt;V&gt; focus);  // 它所在的那一组 ✓
 *   &lt;T&gt; List&lt;T&gt; getRecipes(IRecipeCategory&lt;T&gt; category);                      // 全部组（JEI 列页面用 ✓）
 * </pre>
 * ⚠ 全程 try/catch ✓：⚠ 这类插件抛异常会让 JEI **整个界面**炸掉 ✗，绝不能发生 ✓。
 */
public class VariantGroupManagerPlugin implements IRecipeManagerPlugin {

    /** 索引（懒建一次 ✓ 只在客户端 ✓） */
    private static volatile List<VariantGroupJeiCategory.VariantGroup> groups = null;

    /** 组键 → 组 ✓（查"某只属于哪一组"用 ✓） */
    private static volatile Map<String, VariantGroupJeiCategory.VariantGroup> byKey = null;

    private VariantGroupManagerPlugin() {
    }

    /** 由插件在 {@code registerAdvanced} 里创建 ✓ */
    public static VariantGroupManagerPlugin create() {
        return new VariantGroupManagerPlugin();
    }

    // ============================================================
    //  IRecipeManagerPlugin
    // ============================================================

    @Override
    public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) {
        try {
            ItemStack stack = focusedStack(focus);
            if (stack == null || !stack.hasTag()) {
                return List.of();
            }
            ensureBuilt();
            return byKey.containsKey(keyOf(stack)) ? List.of(VariantGroupJeiCategory.TYPE) : List.of();
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    @Override
    public <T, V> List<T> getRecipes(IRecipeCategory<T> category, IFocus<V> focus) {
        try {
            if (category == null || category.getRecipeType() != VariantGroupJeiCategory.TYPE) {
                return List.of();
            }
            ItemStack stack = focusedStack(focus);
            if (stack == null) {
                return List.of();
            }
            ensureBuilt();
            VariantGroupJeiCategory.VariantGroup group = byKey.get(keyOf(stack));
            if (group == null) {
                return List.of();
            }
            // ⭐ 用"被点的这一只"当页面左上那只 ✓（组内其它只仍然全列 ✓）
            List<T> out = new ArrayList<>(1);
            out.add((T) new VariantGroupJeiCategory.VariantGroup(stack, group.variants()));
            return out;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    @Override
    public <T> List<T> getRecipes(IRecipeCategory<T> category) {
        try {
            if (category == null || category.getRecipeType() != VariantGroupJeiCategory.TYPE) {
                return List.of();
            }
            ensureBuilt();
            List<T> out = new ArrayList<>(groups.size());
            for (VariantGroupJeiCategory.VariantGroup g : groups) {
                out.add((T) g);
            }
            return out;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    // ============================================================
    //  索引
    // ============================================================

    /** {@code IFocus} → 被聚焦的 {@link ItemStack}（不是物品栈就返回 null ✓） */
    private static ItemStack focusedStack(IFocus<?> focus) {
        if (focus == null) {
            return null;
        }
        // ⚠ 反编译实证（JEI 15.21 的 IFocus ✓）：它是 getTypedValue()（→ ITypedIngredient）✗
        //   没有 getIngredient() 那个方法 ✓ —— 上一版就是照直觉写错才编译不过的 ✓
        try {
            var typed = focus.getTypedValue();
            if (typed == null) {
                return null;
            }
            Object ingredient = typed.getIngredient();
            return ingredient instanceof ItemStack stack ? stack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 组键：物品 id + 材料组合 ✓（正是"同 id 变体"的"同一只"定义 ✓） */
    private static String keyOf(ItemStack stack) {
        String id = stack.getItem().builtInRegistryHolder().key().location().toString();
        String mat = JeiVariantFolder.materialsKeyOf(stack);
        return id + "#" + (mat == null ? "-" : mat);
    }

    /**
     * ⭐ <b>重建索引</b> ✓ —— ⚠ 必须由**插件在 {@code onRuntimeAvailable} 里调用** ✗：
     * 实测（用户日志 ✓）在 JEI 调 {@code getRecipes} 时（14:16:40 ✓）**创造栏还没 build 完** ✗
     * ⇒ 那时枚举 {@code CreativeModeTab#getDisplayItems()} 几乎拿不到东西 ✗
     * ⇒ ⭐ 索引只有 5 组 ✗（本该几百组 ✓）。⚠ 所以要在**一切就绪之后**重建一次 ✓。
     */
    public static synchronized void rebuild() {
        try {
            groups = null;
            byKey = null;
            ensureBuilt();
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[JEI] 变体索引重建失败（分类可能不显示内容）：{}", t.toString());
        }
    }

    /**
     * ⭐ <b>用 JEI 自己的完整物品表重建索引</b> ✓（推荐入口 ✓）——
     * ⚠ 这比"枚举创造栏"硬得多 ✗：{@code IIngredientManager#getAllItemStacks()} 就是
     * **JEI 列表里那些东西本身** ✓ ⇒ 与眼睛看到的**同源** ✓（创造栏那条只作兜底 ✓）。
     *
     * @param all JEI 的全部物品栈（⚠ 可能是极大一张表 ✓ 建一次就好 ✗）
     */
    public static synchronized void rebuildFrom(java.util.Collection<ItemStack> all) {
        try {
            List<VariantGroupJeiCategory.VariantGroup> built = buildFrom(all);
            Map<String, VariantGroupJeiCategory.VariantGroup> map = new LinkedHashMap<>();
            for (VariantGroupJeiCategory.VariantGroup g : built) {
                map.put(keyOf(g.base()), g);
            }
            byKey = map;
            groups = built;
            TinkersNewlife.LOGGER.info("[JEI] 同 id 变体：已按 JEI 物品表整理出 {} 组（每组 ≥2 只 ✓ 共看了 {} 个物品栈 ✓）",
                    built.size(), all == null ? 0 : all.size());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[JEI] 按 JEI 物品表建索引失败 ⇒ 退回创造栏那条 ✓：{}", t.toString());
            rebuild();
        }
    }

    /** ⭐ 从一张物品栈表建组 ✓（{@link #build()} 与 {@link #rebuildFrom} 共用 ✓） */
    private static List<VariantGroupJeiCategory.VariantGroup> buildFrom(java.util.Collection<ItemStack> source) {
        Map<String, List<ItemStack>> buckets = new LinkedHashMap<>();
        if (source != null) {
            for (ItemStack stack : source) {
                try {
                    if (stack == null || stack.isEmpty() || !stack.hasTag()) {
                        continue;
                    }
                    if (!interesting(stack)) {
                        continue;
                    }
                    buckets.computeIfAbsent(keyOf(stack), k -> new ArrayList<>()).add(stack.copy());
                } catch (Throwable ignored) {
                }
            }
        }
        return toGroups(buckets);
    }

    private static void ensureBuilt() {
        if (groups != null) {
            return;
        }
        synchronized (VariantGroupManagerPlugin.class) {
            if (groups != null) {
                return;
            }
            List<VariantGroupJeiCategory.VariantGroup> built = build();
            Map<String, VariantGroupJeiCategory.VariantGroup> map = new LinkedHashMap<>();
            for (VariantGroupJeiCategory.VariantGroup g : built) {
                map.put(keyOf(g.base()), g);
            }
            byKey = map;
            groups = built;
            TinkersNewlife.LOGGER.info("[JEI] 同 id 变体：已整理出 {} 组（每组 ≥2 只 ✓）", built.size());
        }
    }

    /** ⭐ 从创造栏展示物品建组 ✓（⚠ 兜底路径 ✗ —— 实测它在 JEI 早期调用时**几乎拿不到东西** ✗，优先用 {@link #rebuildFrom} ✓） */
    private static List<VariantGroupJeiCategory.VariantGroup> build() {
        // 组键 → 变体表（保持创造栏顺序 ✓）
        Map<String, List<ItemStack>> buckets = new LinkedHashMap<>();
        try {
            BuiltInRegistries.CREATIVE_MODE_TAB.forEach(tab -> {
                try {
                    for (ItemStack stack : tab.getDisplayItems()) {
                        if (stack == null || stack.isEmpty() || !stack.hasTag()) {
                            continue;
                        }
                        if (!interesting(stack)) {
                            continue;
                        }
                        buckets.computeIfAbsent(keyOf(stack), k -> new ArrayList<>()).add(stack.copy());
                    }
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[JEI] 变体索引建立失败（JEI 里就没有「同 id 变体」分类了，但不崩）：{}", t.toString());
        }
        return toGroups(buckets);
    }

    /** ⭐ 组桶 → 组表 ✓：只留 ≥2 只的 ✓，组内按 NBT 排序保持稳定 ✓，{@code base} 取第一只 ✓ */
    private static List<VariantGroupJeiCategory.VariantGroup> toGroups(Map<String, List<ItemStack>> buckets) {
        List<VariantGroupJeiCategory.VariantGroup> out = new ArrayList<>();
        for (Map.Entry<String, List<ItemStack>> e : buckets.entrySet()) {
            List<ItemStack> variants = e.getValue();
            if (variants.size() < 2) {
                continue;   // ⭐ 单只不值得单开一页 ✗
            }
            variants.sort(Comparator.comparing(s -> String.valueOf(s.getTag()), Comparator.naturalOrder()));
            out.add(new VariantGroupJeiCategory.VariantGroup(variants.get(0), List.copyOf(variants)));
        }
        return out;
    }

    /**
     * 这个物品值得进"同 id 变体"分类吗 ✓ —— ⭐ **匠魂可改造物品**（⚠ 正是 JEI 子类型折叠
     * 覆盖不到的那批 ✓）或 ⭐ **本模组自己的物品** ✓。
     * <p>⚠ 刻意**不**把"全游戏所有带 NBT 的物品"都收进来 ✗ —— 那些已经由
     * {@link JeiVariantFolder} 的子类型折叠处理了 ✓（两套互补 ✓ 不重复 ✓ 也不至于让分类膨胀到几千页 ✗）。
     */
    private static boolean interesting(ItemStack stack) {
        try {
            if (stack.getItem() instanceof slimeknights.tconstruct.library.tools.item.IModifiable) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return stack.getItem().builtInRegistryHolder().key().location().getNamespace()
                    .equals(TinkersNewlife.MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ============================================================
    //  给别的类用的材料键（与 JeiVariantFolder 同一口径 ✓）
    // ============================================================

    /** 匠魂材料列表的长度（供排序/调试 ✓） */
    static int materialsCount(ItemStack stack) {
        try {
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("tic_materials", Tag.TAG_LIST)) {
                return 0;
            }
            ListTag list = tag.getList("tic_materials", Tag.TAG_STRING);
            return list.size();
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
