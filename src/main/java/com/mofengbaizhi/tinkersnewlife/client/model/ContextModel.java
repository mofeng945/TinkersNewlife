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
 * <b>按显示场景切换模型</b>（§938）—— "物品栏用一套、手持用另一套"的通用壳子 ✓
 * （照匠魂 `UniqueGuiModel` / `ToolModel$BakedToolModel` 的做法 ✓ 见备忘录 §937 ✓）。
 *
 * <h2>原理</h2>
 * Forge 在 {@code BakedModel} 上加了钩子
 * {@code applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand)} ✓
 * —— **模型可以按场景返回不同的 `BakedModel`** ✓ 这就是匠魂"物品栏和手持不一样"的支点 ✓。
 * 本类把原模型包一层：物品栏（{@code GUI} / {@code FIXED} / {@code GROUND}）返回 {@code guiModel} ✓
 * 其余场景（第一/第三人称、掉落、头顶…）原样返回 ✓。
 *
 * <h2>为什么要额外包 `getOverrides`</h2>
 * ⚠ 匠魂工具是**按物品栈用 overrides 解析**出模型的（{@code ToolModel$MaterialOverrideHandler} ✓）
 * ⇒ 只在最外层包一层会被"解析出来的新模型"绕过去 ✗
 * ⇒ 这里把 `ItemOverrides` 也包一层 ✓，**解析结果重新包回来** ✓ 保证任何栈、任何材质都还走本类 ✓。
 *
 * <h2>怎么换贴图</h2>
 * {@code assets/tinkersnewlife/models/item/spear_gui.json} 就是"物品栏那一套" ✓
 * —— 它现在引用**同一批**贴图 ✓（所以外观暂时不变 ✓）；以后把它的三行 texture 改到新图即可 ✓
 * （新增的 PNG 建议放 {@code textures/item/tool/spear/gui/} ✓）。
 */
public class ContextModel extends BakedModelWrapper<BakedModel> {

    /** 物品栏那一套 ✓（GUI / 展示框 FIXED / 掉落物 GROUND ✓ 都算"图标类"场景 ✓） */
    private final BakedModel guiModel;

    public ContextModel(BakedModel base, BakedModel guiModel) {
        super(base);
        this.guiModel = guiModel != null ? guiModel : base;
    }

    private static boolean iconContext(ItemDisplayContext ctx) {
        return ctx == ItemDisplayContext.GUI
                || ctx == ItemDisplayContext.FIXED
                || ctx == ItemDisplayContext.GROUND;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext ctx, PoseStack pose, boolean leftHand) {
        BakedModel chosen = iconContext(ctx) ? this.guiModel : this.originalModel;
        // 交给被选中的模型自己走它的显示变换 ✓（匠魂自己的 BakedToolModel 也会在这儿再切 small/大 ✓）
        return chosen.applyTransform(ctx, pose, leftHand);
    }

    @Override
    public ItemOverrides getOverrides() {
        return new WrappedOverrides(this.originalModel, this.guiModel);
    }

    /** 把 overrides 的**解析结果**重新包回 {@link ContextModel} ✓（否则匠魂按栈解析出来的模型会绕过我们 ✗） */
    private static final class WrappedOverrides extends ItemOverrides {

        private final BakedModel base;
        private final BakedModel gui;
        private final ItemOverrides baseOverrides;
        private final ItemOverrides guiOverrides;

        WrappedOverrides(BakedModel base, BakedModel gui) {
            this.base = base;
            this.gui = gui;
            this.baseOverrides = base.getOverrides();
            this.guiOverrides = gui.getOverrides();
        }

        @Override
        public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                  @Nullable LivingEntity entity, int seed) {
            BakedModel resolvedBase = this.baseOverrides == null
                    ? this.base : this.baseOverrides.resolve(this.base, stack, level, entity, seed);
            BakedModel resolvedGui = this.guiOverrides == null
                    ? this.gui : this.guiOverrides.resolve(this.gui, stack, level, entity, seed);
            return new ContextModel(resolvedBase, resolvedGui);
        }
    }
}
