package com.mofengbaizhi.tinkersnewlife.compat.jei;

import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⭐ §1122 <b>JEI 变体折叠器</b>（用户口径 ✓「把所有同 id 变体全部折叠在一起，只在鼠标光标悬停时展开」✓
 * ＋ 追问后选定「**所有模组**」✓「**按材料组合**」✓）。
 *
 * <h2>它做什么</h2>
 * 给**全游戏的每一个物品**注册这一个解释器 ✓ ⇒ JEI 里同一个物品的多个 NBT 变体
 * **折叠成一个格子** ✓（格子角上标 {@code 1/N} ✓，鼠标悬停在该格上滚动滚轮切换 ✓ ——
 * ⚠ 这是 JEI 的原生交互 ✗，插件只能决定"哪些算同一个"，改不了它"一格＋切换"的呈现方式 ✓）。
 *
 * <h2>⭐ 三条判据（顺序即优先级 ✓）</h2>
 * <ol>
 *   <li><b>没有 NBT ⇒ 不折叠</b> ✓：返回 {@link #NO_SUBTYPE_KEY}（空串 ✓）
 *       ⇒ ⭐ JEI 视作"该物品没有子类型" ✓ ⇒ 全游戏绝大多数物品**零影响** ✓
 *       （⚠ 这是给"全模组注册"兜底的关键保险 ✗ 否则会把所有物品都染上"1/1"角标 ✓）；</li>
 *   <li><b>匠魂工具/部件 ⇒ 按【材料组合】折叠</b> ✓：只取 NBT 里的 {@code tic_materials} 那一串
 *       （⭐ 本模组那几千个"每个材料 × 每个工具"的变体就靠它归并 ✓）
 *       —— ⚠ **刻意忽略**耐久（{@code Damage} ✓）/强化（{@code tic_modifiers} ✓）/自定义名 ✓
 *       ⇒ 磨过的工具**不会**另算一只 ✓（用户选的粒度 ✓）；</li>
 *   <li><b>其它模组 ⇒ 按 NBT 折叠</b> ✓：整体 NBT 相等才算同一只 ✓。</li>
 * </ol>
 *
 * <h2>⚠ 性能</h2>
 * JEI 会为**每个展示过的物品栈**调一次本解释器 ✓ ⇒ 这里只做"读一个 NBT 键 + 拼字符串" ✓，
 * 并对**结果串**做缓存 ✓（同一 NBT 只算一次 ✓）；全程 try/catch ✓ ——
 * 解释器抛异常会让 JEI 的整个界面列表炸掉 ✗，绝不能发生 ✓。
 */
public final class JeiVariantFolder implements IIngredientSubtypeInterpreter<ItemStack> {

    /** 单例（全物品共用同一个 ✓ 省内存也省注册时间 ✓） */
    public static final JeiVariantFolder INSTANCE = new JeiVariantFolder();

    /**
     * ⭐ "没有子类型"的返回值 ✓ —— JEI 约定 **空串** 表示该物品不分变体 ✓
     * （⚠ 这就是"全模组注册却不影响普通物品"的关键 ✓：无 NBT 的物品返回它 ⇒ JEI 不给角标 ✓）。
     */
    private static final String NO_SUBTYPE_KEY = "";

    /** 匠魂材料 NBT 键（工具与部件都存在这里 ✓ 是一串 MaterialVariantId 字符串 ✓） */
    private static final String KEY_MATERIALS = "tic_materials";

    /** 结果缓存：NBT 字符串 → 折叠键 ✓（上限保护，防止极端存档把内存吃光 ✗） */
    private static final int CACHE_MAX = 8192;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private JeiVariantFolder() {
    }

    @Override
    public String apply(ItemStack stack, UidContext context) {
        try {
            CompoundTag tag = stack.getTag();
            if (tag == null || tag.isEmpty()) {
                return NO_SUBTYPE_KEY;   // ⭐ 无 NBT ⇒ 不折叠 ✓
            }
            // ⭐ 匠魂工具/部件：按材料组合折叠 ✓（忽略耐久/强化/名字 ✓）
            String materials = materialsKey(tag);
            if (materials != null) {
                return materials;
            }
            // 其它模组：按整体 NBT 折叠 ✓（缓存一下，避免每次重新序列化 ✓）
            String raw = tag.toString();
            String cached = cache.get(raw);
            if (cached != null) {
                return cached;
            }
            String key = "jei_nbt:" + raw;
            if (cache.size() < CACHE_MAX) {
                cache.put(raw, key);
            }
            return key;
        } catch (Throwable ignored) {
            // ⚠ 解释器绝不能抛异常（会连累 JEI 整个物品列表 ✗）⇒ 退化成"不折叠" ✓
            return NO_SUBTYPE_KEY;
        }
    }

    /**
     * 匠魂材料的折叠键 ✓ —— 取 {@code tic_materials} 列表逐项拼起来 ✓；没有该键就返回 {@code null}
     * （交给调用方走"按 NBT"那条 ✓）。
     */
    @Nullable
    private static String materialsKey(CompoundTag tag) {
        if (!tag.contains(KEY_MATERIALS, Tag.TAG_LIST)) {
            return null;
        }
        ListTag list = tag.getList(KEY_MATERIALS, Tag.TAG_STRING);
        if (list.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("jei_mat:");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(list.getString(i));
        }
        return sb.toString();
    }
}
