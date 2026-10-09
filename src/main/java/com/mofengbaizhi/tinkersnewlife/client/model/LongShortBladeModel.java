package com.mofengbaizhi.tinkersnewlife.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.BakedModelWrapper;

import javax.annotation.Nullable;

/**
 * ⭐ §1146／§1154 <b>长短刃：按「形态 × 场景」切模型</b>（用户口径 ✓）。
 *
 * <h2>⭐⭐⭐ 两个钩子的**调用顺序**决定了分工（⚠ 我第一版搞反了 ✗）</h2>
 * ⭐ 原版 {@code ItemRenderer} 的顺序是：
 * <pre>
 *   model = shaper.getItemModel(stack);                                  // ① 顶层模型
 *   model = model.getOverrides().resolve(model, stack, level, entity, s); // ② ⭐ **先** resolve（有 stack ✓）
 *   model = model.applyTransform(ctx, pose, leftHand);                    // ③ ⭐ **后** transform（有场景 ✓）
 *   … 用 ③ 返回的模型渲染 …
 * </pre>
 * ⇒ ⚠⚠ **`applyTransform` 拿不到物品栈** ✗（⭐ ③ 已经离 ② 很远了 ✓）
 * ⇒ ⭐ **形态（长／短）必须在 ② `resolve` 里定** ✓
 * ⇒ ⭐ **场景（物品栏／手持）必须在 ③ `applyTransform` 里定** ✓
 * <p>⚠ 我第一版**反了** ✗：⭐ 在 `applyTransform` 里挑形态 ✗ ⇒ ⭐ 它拿不到 stack ✗
 * ⇒ ⭐ 只能固定用长刀那一侧 ⇒ ⭐ **长短刀都会显示长刀那套** ✗ ✓（⭐ 已重写 ✓）。
 *
 * <h2>⭐ 于是分两级（级别含义与第一版相反 ✓）</h2>
 * <ol>
 *   <li>⭐ 第 1 级（本类 ✓）：⭐ 唯一入口 ✓ ⇒ {@link #getOverrides()} 的 `resolve` **读 stack 定形态** ✗
 *       ⇒ ⭐ 返回第 2 级 ✓；</li>
 *   <li>⭐ 第 2 级（{@link ContextPickModel} ✓）：⭐ **已经知道形态** ✓ ⇒ 只剩"物品栏／手持"两选一 ✓
 *       ⇒ 它的 `overrides` 返回**空的**（⭐ 别让原版再解析一轮 ✗ 否则会循环 ✓）。</li>
 * </ol>
 *
 * <h2>⚠ 为什么第 2 级的 overrides 要给一个空实现</h2>
 * ⭐ 原版拿 ③ 的返回值后**还会再调一次** `getOverrides().resolve(...)` ✓（⭐ `ItemRenderer` 内部 ✓）
 * ⇒ ⚠ 若第 2 级的 `resolve` 又返回"需要形态判断"的模型 ✗ ⇒ ⭐ **形态信息已经丢了** ✗
 * ⇒ ⭐ 会给错 ✓（⭐ 甚至无限套娃 ✓）⇒ ⭐ 返回**默认实现**（⭐ 原样返回传入的模型 ✓）✓。
 */
public class LongShortBladeModel extends BakedModelWrapper<BakedModel> {

    /** ⭐ 物品栏·长刀 ✓ */
    private final BakedModel guiLong;
    /** ⭐ 物品栏·短刀 ✓ */
    private final BakedModel guiShort;
    /** ⭐ 手持·长刀 ✓ */
    private final BakedModel heldLong;
    /** ⭐ 手持·短刀 ✓ */
    private final BakedModel heldShort;

    public LongShortBladeModel(BakedModel fallback,
                               BakedModel guiLong, BakedModel guiShort,
                               BakedModel heldLong, BakedModel heldShort) {
        super(fallback);
        // ⚠ 缺哪个就回退到哪一个 ✗ —— ⭐ 用户可以只画一部分 ✓（⭐ 没画的沿用默认 ✓ 不崩 ✓）
        BakedModel def = fallback;
        this.guiLong = guiLong != null ? guiLong : def;
        this.guiShort = guiShort != null ? guiShort : this.guiLong;
        this.heldLong = heldLong != null ? heldLong : this.guiLong;
        this.heldShort = heldShort != null ? heldShort : this.guiShort;
    }

    /** ⭐ 这四个"拿在手里"的场景才用 held ✓ 其它（GUI／展示框／掉落／头顶…）一律用 gui ✓ */
    static boolean heldContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Override
    public ItemOverrides getOverrides() {
        return new PickerOverrides(this);
    }

    /** ⭐ 读 {@code lnb_form} ✓（⚠ 复用物品类里的判断 ✓ 免得两边各写一个字符串 ✗） */
    private static boolean isLongForm(ItemStack stack) {
        try {
            return com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem.isLong(stack);
        } catch (Throwable ignored) {
            // ⚠ 读不到就当长刀 ✓（⭐ 与 `LongShortBladeItem` 里的默认值一致 ✓）
            return true;
        }
    }

    /** ⭐ 第 1 级：⭐ 这里**有 stack** ✓ ⇒ ⭐ **定形态** ✗ ⇒ 交给第 2 级去定场景 ✓ */
    private static final class PickerOverrides extends ItemOverrides {

        private final LongShortBladeModel parent;

        PickerOverrides(LongShortBladeModel parent) {
            this.parent = parent;
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            boolean longForm = isLongForm(stack);
            // ⭐ 把"该形态的 物品栏／手持 两张"一起交给第 2 级 ✓
            return new ContextPickModel(
                    longForm ? this.parent.guiLong : this.parent.guiShort,
                    longForm ? this.parent.heldLong : this.parent.heldShort);
        }
    }

    /**
     * ⭐ 第 2 级：⭐ **形态已经定死** ✓ ⇒ 只剩"物品栏／手持"两选一 ✓。
     * <p>⚠ 它的 {@code overrides} 必须是**空实现** ✗ —— ⭐ 否则原版拿到它之后又解析一轮 ✗
     * 会把刚定好的形态丢掉 ✓（⭐ 见类注释 ✓）。
     */
    public static final class ContextPickModel extends BakedModelWrapper<BakedModel> {

        private final BakedModel guiSide;
        private final BakedModel heldSide;

        ContextPickModel(BakedModel guiSide, BakedModel heldSide) {
            super(guiSide != null ? guiSide : heldSide);
            this.guiSide = guiSide;
            this.heldSide = heldSide;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand) {
            BakedModel picked = heldContext(ctx) ? this.heldSide : this.guiSide;
            if (picked == null) {
                picked = this.guiSide != null ? this.guiSide : this.heldSide;
            }
            // ⭐ 交给被选中的那张自己走它的显示变换 ✓（⭐ 匠魂的 `tconstruct:tool` 模型也在这里做事 ✓）
            return picked.applyTransform(ctx, pose, leftHand);
        }

        @Override
        public ItemOverrides getOverrides() {
            // ⚠ 空实现（⭐ 默认就是"原样返回传入的模型" ✓）—— ⚠ 绝不能返回会再判形态的东西 ✗
            return new ItemOverrides() {
            };
        }
    }
}
