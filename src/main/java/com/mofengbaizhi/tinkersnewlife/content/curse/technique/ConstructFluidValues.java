package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 构筑造价 · 流体价值支撑层（P3 桶/标签代理 + P4 软依赖适配器）。
 *
 * <h3>为什么需要它</h3>
 * 很多模组的"高价值链"其实跑在<b>流体</b>上（例：Iron's Spells 的墨水流体链
 * {@code common_ink → uncommon_ink → … → legendary_ink}），而原版/Forge <b>没有</b>
 * "从配方里读流体投入/产出"的通用接口 —— 这部分价值以前完全丢失。
 *
 * <h3>做法（三层，逐层降级，读不到就不报错）</h3>
 * <ol>
 *   <li><b>桶代理</b>：{@code Fluid#getBucket()} 拿到桶物品 → {@code 桶物品价值 / 1000} = 每 mB 价值。
 *       这是"有价可算"的地板价。</li>
 *   <li><b>流标签表</b>：配置 {@code fluid_tag_values}（如 {@code "#forge:inks=15"}）直接给流体系加成。</li>
 *   <li><b>软依赖适配器</b>：按已知 API 优先（TCon {@code MaterialFluidRecipe#getFluids}、
 *       Create {@code getFluidIngredients()/getFluidResults()}、Mekanism/Thermal/IE 的 FluidStack 字段），
 *       再退化为<b>通用反射扫描</b>：遍历配方的字段与方法，凡是 {@code FluidStack} /
 *       {@code FluidIngredient} / 其集合类型就按名字判断是"投入"还是"产出"。
 *       任何一步失败只降级、不抛异常。</li>
 * </ol>
 */
public final class ConstructFluidValues {

    /** 一份流体的投入/产出量 */
    public record FluidAmount(Fluid fluid, int mb) {}

    private ConstructFluidValues() {}

    // ============================================================
    //  桶代理 / 标签
    // ============================================================

    /** 桶代理：流体每 mB 价值 = 桶物品价值 / 1000（取不到桶 → 0） */
    public static double bucketProxy(Fluid fluid, java.util.function.ToDoubleFunction<Item> valueOf) {
        try {
            Item bucket = fluid.getBucket();
            if (bucket == null || bucket == Items.AIR) return 0.0;
            double v = valueOf.applyAsDouble(bucket);
            return Math.max(0.0, v / 1000.0);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /** 流标签表加成（每 mB）：{@code fluid_tag_values = ["#forge:inks=15"]} → 15/1000 每 mB */
    public static double tagProxy(Fluid fluid) {
        try {
            var list = com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_FLUID_TAG_VALUES.get();
            if (list == null || list.isEmpty()) return 0.0;
            double best = 0;
            for (String raw : list) {
                if (raw == null) continue;
                String e = raw.trim();
                int i = e.indexOf('=');
                if (i <= 0) continue;
                String key = e.substring(0, i).trim();
                if (!key.startsWith("#")) continue;
                double per1000;
                try {
                    per1000 = Double.parseDouble(e.substring(i + 1).trim());
                } catch (Throwable t) {
                    continue;
                }
                ResourceLocation tagId = ResourceLocation.tryParse(key.substring(1));
                if (tagId == null) continue;
                TagKey<Fluid> tag = TagKey.create(net.minecraft.core.registries.Registries.FLUID, tagId);
                if (fluid.builtInRegistryHolder().is(tag) && per1000 > best) best = per1000;
            }
            return best / 1000.0;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    // ============================================================
    //  从配方抽取流体投入 / 产出
    // ============================================================

    /** 每条配方的流体描述缓存（按配方类缓存反射结果，避免重复扫描） */
    private static final Map<Class<?>, java.util.List<Field>> FLUID_FIELDS = new HashMap<>();
    private static final Map<Class<?>, java.util.List<Method>> FLUID_METHODS = new HashMap<>();

    /** 抽取该配方的流体<b>投入</b>（读不到就是空表） */
    public static List<FluidAmount> inputsOf(Recipe<?> recipe) {
        List<FluidAmount> out = new ArrayList<>(2);
        try {
            // ① 已知 API：TCon 的材料流体配方（fluids = 投入流体）
            if (recipe instanceof slimeknights.tconstruct.library.recipe.casting.material.MaterialFluidRecipe mfr) {
                for (FluidStack fs : mfr.getFluids()) add(out, fs, false);
                return out;
            }
            // ② Create：getFluidIngredients()
            Object created = tryInvokeNoArg(recipe, "getFluidIngredients");
            if (created != null) {
                collectFluids(created, out, false);
                if (!out.isEmpty()) return out;
            }
            // ③ 通用反射
            reflectFluids(recipe, out, false);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 抽取该配方的流体<b>产出</b> */
    public static List<FluidAmount> outputsOf(Recipe<?> recipe) {
        List<FluidAmount> out = new ArrayList<>(2);
        try {
            // ① Create：getFluidResults()
            Object created = tryInvokeNoArg(recipe, "getFluidResults");
            if (created != null) {
                collectFluids(created, out, true);
                if (!out.isEmpty()) return out;
            }
            // ② 通用反射
            reflectFluids(recipe, out, true);
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Object tryInvokeNoArg(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            if (!Modifier.isStatic(m.getModifiers())) return m.invoke(target);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 反射扫描字段与方法，按名字判定投入/产出 */
    private static void reflectFluids(Object recipe, List<FluidAmount> out, boolean wantOutput) {
        Class<?> type = recipe.getClass();
        // 字段
        List<Field> fields = FLUID_FIELDS.computeIfAbsent(type, ConstructFluidValues::scanFluidFields);
        for (Field f : fields) {
            if (!classify(f.getName(), wantOutput)) continue;
            try {
                Object v = f.get(recipe);
                collectFluids(v, out, wantOutput);
            } catch (Throwable ignored) {
            }
        }
        // 方法（无参、非静态、名字含 fluid）
        List<Method> methods = FLUID_METHODS.computeIfAbsent(type, ConstructFluidValues::scanFluidMethods);
        for (Method m : methods) {
            if (!classify(m.getName(), wantOutput)) continue;
            try {
                collectFluids(m.invoke(recipe), out, wantOutput);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 名字判定：产出关键词优先；否则含 fluid 却没产出线索 → 视为投入 */
    private static boolean classify(String name, boolean wantOutput) {
        String n = name.toLowerCase(Locale.ROOT);
        boolean outWord = n.contains("result") || n.contains("output") || n.contains("produce");
        boolean inWord = n.contains("input") || n.contains("ingredient") || n.contains("base")
                || n.contains("require") || n.contains("consume") || n.contains("fluid");
        if (wantOutput) return outWord;
        if (outWord) return false;                 // 已判定为产出，就不当投入
        return inWord;
    }

    private static List<Field> scanFluidFields(Class<?> type) {
        List<Field> list = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (isFluidish(f.getType()) || isFluidish(f.getGenericType().getTypeName())) {
                    try {
                        f.setAccessible(true);
                        list.add(f);
                    } catch (Throwable ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        return list;
    }

    private static List<Method> scanFluidMethods(Class<?> type) {
        List<Method> list = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers())) continue;
                if (m.getParameterCount() != 0) continue;
                String n = m.getName().toLowerCase(Locale.ROOT);
                if (!n.contains("fluid")) continue;
                if (!isFluidish(m.getReturnType()) && !isFluidish(m.getGenericReturnType().getTypeName())) continue;
                try {
                    m.setAccessible(true);
                    list.add(m);
                } catch (Throwable ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return list;
    }

    private static boolean isFluidish(Class<?> t) {
        if (t == null) return false;
        String n = t.getName();
        return FluidStack.class.isAssignableFrom(t) || Fluid.class.isAssignableFrom(t)
                || n.contains("FluidIngredient") || n.contains("FluidStack")
                || Collection.class.isAssignableFrom(t) || t.isArray();
    }

    private static boolean isFluidish(String typeName) {
        if (typeName == null) return false;
        return typeName.contains("FluidStack") || typeName.contains("FluidIngredient")
                || typeName.contains("net.minecraft.world.level.material.Fluid");
    }

    /** 把任意对象里"像流体量的东西"收进结果（FluidStack / FluidIngredient / 集合 / 数组） */
    @SuppressWarnings("unchecked")
    private static void collectFluids(Object value, List<FluidAmount> out, boolean output) {
        if (value == null) return;
        try {
            if (value instanceof FluidStack fs) {
                add(out, fs, output);
                return;
            }
            if (value instanceof Fluid fluid) {
                out.add(new FluidAmount(fluid, 1000));
                return;
            }
            if (value instanceof Collection<?> col) {
                for (Object o : col) collectFluids(o, out, output);
                return;
            }
            if (value.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(value);
                for (int i = 0; i < len; i++) collectFluids(java.lang.reflect.Array.get(value, i), out, output);
                return;
            }
            // FluidIngredient（Create）/ 其它包装：试 getFluids() / getMatchingFluidStacks() / 字段
            String cn = value.getClass().getName();
            if (cn.contains("FluidIngredient") || cn.contains("Ingredient")) {
                Object stacks = tryInvokeNoArg(value, "getMatchingFluidStacks");
                if (stacks != null) {
                    collectFluids(stacks, out, output);
                    return;
                }
                Object fluids = tryInvokeNoArg(value, "getFluids");
                if (fluids != null) collectFluids(fluids, out, output);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void add(List<FluidAmount> out, FluidStack fs, boolean output) {
        if (fs == null || fs.isEmpty()) return;
        out.add(new FluidAmount(fs.getFluid(), Math.max(1, fs.getAmount())));
    }

    /** 流体 id 字符串（调试/日志用） */
    public static String idOf(Fluid fluid) {
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        return id == null ? String.valueOf(fluid) : id.toString();
    }

    /** 空物品栈保护（供外部快速判断） */
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty() || stack.getItem() == Items.AIR;
    }

    /** 是否整体启用（配置关闭则流体项一律为 0） */
    public static boolean enabled() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_FLUID_VALUE_ENABLED.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /** 流体每 mB 价值上限（防炸） */
    public static double perMbCap() {
        try {
            double cap = com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_FLUID_VALUE_CAP.get();
            return Math.max(0.0, cap) / 1000.0;
        } catch (Throwable t) {
            return 0.5;   // 默认单桶价值上限 500 分 → 0.5 每 mB
        }
    }

    static {
        TinkersNewlife.LOGGER.debug("[构筑/流体] 流体价值层已就绪（桶代理 + 标签表 + 软依赖反射适配器）");
    }
}
