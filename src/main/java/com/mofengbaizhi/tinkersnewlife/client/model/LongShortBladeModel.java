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
 * ⭐ §1146 <b>长短刃：按「场景 × 形态」切模型</b>（用户口径 ✓ 2026-10-09：
 * 「我接下来要画几个贴图，分别用于：物品栏未手持时展示模型，物品栏手持时展示模型（2个），
 * 手持模型（两个），能先搭建好环境吗」✓）。
 *
 * <h2>⭐⭐ 两个轴怎么分工（这是本类唯一的关键设计 ✓）</h2>
 * ⚠ 原版/Forge 给模型的两个钩子拿到的信息**不一样** ✗ ⇒ ⭐ 必须分开用 ✓：
 * <table border="1">
 *   <tr><th>轴</th><th>用哪个钩子</th><th>为什么</th></tr>
 *   <tr><td>⭐ <b>场景</b>（物品栏／手持）</td>
 *       <td>{@link #applyTransform}</td>
 *       <td>⭐ 它拿得到 {@code ItemDisplayContext} ✓（GUI／FIRST_PERSON_*／THIRD_PERSON_* ✓）</td></tr>
 *   <tr><td>⭐ <b>形态</b>（长刀／短刀）</td>
 *       <td>{@link #getOverrides}</td>
 *       <td>⚠ {@code applyTransform} **拿不到物品栈** ✗ ⇒ ⭐ 形态只能在这一层判 ✓
 *           （{@code resolve(model, stack, …)} 有 `stack` ✓ 读 NBT {@code lnb_form} ✓）</td></tr>
 * </table>
 *
 * <h2>⭐ 所以是两级包装</h2>
 * <ol>
 *   <li>⭐ 第 1 级（本类 ✓）：持有 **{@code gui} 一对 ＋ {@code held} 一对** ✓
 *       ⇒ {@code applyTransform} 按场景挑出"一对"✓ 并返回第 2 级 ✓；</li>
 *   <li>⭐ 第 2 级（{@link FormPickModel} ✓）：持有那一对里的**长／短**两张 ✓
 *       ⇒ {@code getOverrides().resolve(stack…)} 读 {@code lnb_form} 决定用哪张 ✓。</li>
 * </ol>
 *
 * <h2>⚠ 为什么要额外包 {@code getOverrides}（照 {@code ContextModel} 的教训 ✓）</h2>
 * ⭐ 匠魂工具是**按物品栈用 overrides 解析**出模型的 ✗ ⇒ 只在最外层包一层会被
 * **解析出来的新模型绕过去** ✗ ⇒ ⭐ 必须把解析结果**重新包回来** ✓。
 */
public class LongShortBladeModel extends BakedModelWrapper<BakedModel> {

    /** ⭐ 物品栏那一对（⭐ 未手持 ✓ 与 已装配 ✓） */
    private final BakedModel guiIdle;
    private final BakedModel guiLong;
    private final BakedModel guiShort;
    /** ⭐ 手持那一对（⭐ 第一人称／第三人称 ✓） */
    private final BakedModel heldLong;
    private final BakedModel heldShort;

    public LongShortBladeModel(BakedModel fallback,
                               BakedModel guiIdle, BakedModel guiLong, BakedModel guiShort,
                               BakedModel heldLong, BakedModel heldShort) {
        super(fallback);
        // ⚠ 缺哪个就回退到哪一个 ✗ —— ⭐ 用户**可以只画一部分** ✓（⭐ 没画的场景沿用默认 ✓ 不崩 ✓）
        BakedModel def = fallback;
        this.guiIdle = guiIdle != null ? guiIdle : def;
        this.guiLong = guiLong != null ? guiLong : this.guiIdle;
        this.guiShort = guiShort != null ? guiShort : this.guiIdle;
        this.heldLong = heldLong != null ? heldLong : this.guiLong;
        this.heldShort = heldShort != null ? heldShort : this.guiShort;
    }

    /** ⭐ 这四个"拿在手里"的场景才用 held ✓ 其它（GUI／展示框／掉落／头顶…）一律用 gui ✓ */
    private static boolean heldContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand) {
        BakedModel longSide = heldContext(ctx) ? this.heldLong : this.guiLong;
        BakedModel shortSide = heldContext(ctx) ? this.heldShort : this.guiShort;
        // ⭐ 交给第 2 级去按 `lnb_form` 选 ✓（⚠ 这一层拿不到 stack ✗ 见类注释 ✓）
        return new FormPickModel(longSide, shortSide, this.guiIdle);
    }

    @Override
    public ItemOverrides getOverrides() {
        return new WrappedOverrides(this);
    }

    /**
     * ⭐ 第 2 级：⭐ 只知道"长／短"两张 ✓ 靠 {@code getOverrides().resolve(stack…)} 里的 stack 判形态 ✓。
     */
    public static class FormPickModel extends BakedModelWrapper<BakedModel> {

        private final BakedModel longSide;
        private final BakedModel shortSide;
        /** ⭐ 没有形态信息时（⚠ 理论上不会 ✗）用的默认 ✓ */
        private final BakedModel idle;

        FormPickModel(BakedModel longSide, BakedModel shortSide, BakedModel idle) {
            super(longSide);
            this.longSide = longSide;
            this.shortSide = shortSide;
            this.idle = idle;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand) {
            // ⭐ 已经不缺信息了 ⇒ 直接用长刀那侧的变换 ✓（形态在 overrides 层已经定死 ✓）
            return this.longSide.applyTransform(ctx, pose, leftHand);
        }

        @Override
        public ItemOverrides getOverrides() {
            return new ItemOverrides() {
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                          @Nullable LivingEntity entity, int seed) {
                    BakedModel pick = isLongForm(stack) ? FormPickModel.this.longSide : FormPickModel.this.shortSide;
                    if (pick == null) {
                        pick = FormPickModel.this.idle;
                    }
                    return pick;
                }
            };
        }
    }

    /** ⭐ 读 {@code lnb_form} ✓（⚠ 常量复用物品类里的 ✓ 免得两边写两个字符串 ✗） */
    private static boolean isLongForm(ItemStack stack) {
        try {
            return com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem.isLong(stack);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** ⭐ 把匠魂按栈解析出来的结果**重新包回来** ✓（⚠ 不包会被绕过 ✗ 见类注释 ✓） */
    private static final class WrappedOverrides extends ItemOverrides {

        private final LongShortBladeModel parent;
        private final ItemOverrides wrapped;

        WrappedOverrides(LongShortBladeModel parent) {
            this.parent = parent;
            this.wrapped = parent.originalModel.getOverrides();
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            BakedModel inner = this.wrapped == null
                    ? this.parent.originalModel
                    : this.wrapped.resolve(this.parent.originalModel, stack, level, entity, seed);
            // ⭐ 用"解析后的基模型"重建一份包装 ✓（⭐ 材质替换过的模型仍按场景／形态切 ✓）
            return new LongShortBladeModel(inner,
                    this.parent.guiIdle, this.parent.guiLong, this.parent.guiShort,
                    this.parent.heldLong, this.parent.heldShort);
        }
    }
}
