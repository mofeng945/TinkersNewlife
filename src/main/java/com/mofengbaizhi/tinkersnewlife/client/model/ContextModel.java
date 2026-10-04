package com.mofengbaizhi.tinkersnewlife.client.model;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.BakedModelWrapper;
import com.mojang.blaze3d.vertex.PoseStack;

import javax.annotation.Nullable;

/**
 * <b>按显示场景切换模型</b>（§938 建壳 ✓ §939 改成"默认＝物品栏、手持可选"）——
 * 照匠魂 `UniqueGuiModel` / `ToolModel$BakedToolModel` 的做法 ✓（机制见备忘录 §937 ✓）。
 *
 * <h2>口径（§939，用户口径）</h2>
 * 「**我想让默认是物品栏贴图，只有某些情况下需要额外写手持贴图**」✓
 * <ul>
 *   <li><b>默认（{@code item/spear.json}）＝ 物品栏那一套</b> ✓ ——
 *       {@code GUI} / {@code FIXED}（展示框）/ {@code GROUND}（掉落）/ {@code HEAD} / 其它一切场景都用它 ✓；</li>
 *   <li><b>手持</b>（{@code FIRST_PERSON_RIGHT/LEFT_HAND} ✓ {@code THIRD_PERSON_RIGHT/LEFT_HAND}）⇒
 *       **如果有** {@code item/spear_held.json} 就用它 ✓，**没有**就沿用默认 ✓
 *       ⇒ 换句话说：**只有想要"手持另一套贴图"时才需要写那个文件** ✓。</li>
 * </ul>
 *
 * <h2>原理</h2>
 * Forge 在 {@code BakedModel} 上加了钩子
 * {@code applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand)} ✓
 * —— **模型可以按场景返回不同的 `BakedModel`** ✓ 这就是匠魂"物品栏和手持不一样"的支点 ✓。
 *
 * <h2>为什么要额外包 `getOverrides`</h2>
 * ⚠ 匠魂工具是**按物品栈用 overrides 解析**出模型的（{@code ToolModel$MaterialOverrideHandler} ✓）
 * ⇒ 只在最外层包一层会被"解析出来的新模型"绕过去 ✗
 * ⇒ 这里把 `ItemOverrides` 也包一层 ✓，**解析结果重新包回来** ✓。
 */
public class ContextModel extends BakedModelWrapper<BakedModel> {

    /** 手持那一套 ✓；没提供时构造方会传默认模型进来（= 等于不切换 ✓） */
    private final BakedModel heldModel;

    public ContextModel(BakedModel base, BakedModel heldModel) {
        super(base);
        this.heldModel = heldModel != null ? heldModel : base;
    }

    /** 只有这四个"拿在手里"的场景才可能换 ✓ 其它（物品栏/展示框/掉落/头顶…）一律用默认 ✓ */
    private static boolean heldContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand) {
        BakedModel chosen = heldContext(ctx) ? this.heldModel : this.originalModel;
        // 交给被选中的模型自己走它的显示变换 ✓（匠魂自己的 BakedToolModel 也会在这儿再切 small/大 ✓）
        return chosen.applyTransform(ctx, pose, leftHand);
    }

    @Override
    public ItemOverrides getOverrides() {
        return new WrappedOverrides(this.originalModel, this.heldModel);
    }

    /** 把 overrides 的**解析结果**重新包回 {@link ContextModel} ✓（否则匠魂按栈解析出来的模型会绕过我们 ✗） */
    private static final class WrappedOverrides extends ItemOverrides {

        private final BakedModel base;
        private final BakedModel held;
        private final ItemOverrides baseOverrides;
        private final ItemOverrides heldOverrides;

        WrappedOverrides(BakedModel base, BakedModel held) {
            this.base = base;
            this.held = held;
            this.baseOverrides = base.getOverrides();
            this.heldOverrides = held.getOverrides();
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            BakedModel resolvedBase = this.baseOverrides == null
                    ? this.base : this.baseOverrides.resolve(this.base, stack, level, entity, seed);
            BakedModel resolvedHeld = this.heldOverrides == null
                    ? this.held : this.heldOverrides.resolve(this.held, stack, level, entity, seed);
            return new ContextModel(resolvedBase, resolvedHeld);
        }
    }
}
