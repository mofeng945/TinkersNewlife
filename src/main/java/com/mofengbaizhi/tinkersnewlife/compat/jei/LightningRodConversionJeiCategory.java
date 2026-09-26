package com.mofengbaizhi.tinkersnewlife.compat.jei;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.LightningRodConversionHandler;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/**
 * JEI 分类：<b>避雷针雷击转化（烈焰血 → 液态闪电，5:4）</b>。
 *
 * <p>这条机制**不是**真正的配方（它由 {@link LightningRodConversionHandler} 在雷击事件里直接做），
 * 所以在这里手工造一条"展示用配方"：输入 = 烈焰血（500 mB）+ 避雷针，输出 = 液态闪电（400 mB），
 * 画面上再写清"5:4、余下 1/5 消失、只认单流体容器" ✓。
 *
 * <p>催化剂 = 避雷针（右键避雷针即可在 JEI 里翻到这个分类 ✓，见
 * {@code TinkersNewlifeJeiPlugin#registerRecipeCatalysts} ✓）。
 *
 * <p>⚠ 输出流体（{@code liquid_lightning}）属**铁魔法联动组** ⇒ 没装铁魔法时它不注册，
 * 插件那边会查不到 ⇒ 本分类连同这条展示**一起不注册** ✓（不会留一个"空头配方" ✗）。
 */
public class LightningRodConversionJeiCategory
        implements IRecipeCategory<LightningRodConversionJeiCategory.Conversion> {

    /** JEI 里展示的"一次转化"：一份输入流体 + 一份输出流体（数量按 5:4 展示 ✓） */
    public record Conversion(Fluid input, Fluid output, int inputMb, int outputMb) {}

    public static final RecipeType<Conversion> TYPE =
            RecipeType.create(TinkersNewlife.MOD_ID, "lightning_rod_conversion", Conversion.class);

    /** JEI 展示用的一次量：500 mB 烈焰血 → 400 mB 液态闪电（与真实的 4/5 完全一致 ✓） */
    public static final int SHOW_INPUT_MB = 500;
    public static final int SHOW_OUTPUT_MB = SHOW_INPUT_MB
            * LightningRodConversionHandler.OUTPUT_PER_CYCLE / LightningRodConversionHandler.INPUT_PER_CYCLE;

    private static final int WIDTH = 170;
    private static final int HEIGHT = 70;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private final Component title;

    public LightningRodConversionJeiCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(Items.LIGHTNING_ROD));
        this.arrow = helper.getRecipeArrow();
        this.title = Component.translatable("jei.tinkersnewlife.lightning_rod_conversion");
    }

    @Override
    public RecipeType<Conversion> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Conversion recipe, IFocusGroup focuses) {
        // §677 修（用户报告「JEI 里看不到液态闪电的配方」）：
        //   JEI 的"**为这个物品**查配方"只认**物品** ✗ ⇒ 只画流体会让"悬停液态闪电的【桶】"
        //   或"悬停烈焰血的【桶】"都查不到这条 ✗（两个桶都存在：tinkersnewlife:liquid_lightning_bucket ✓、
        //   tconstruct:blazing_blood_bucket ✓）。
        //   ⇒ 每个槽里**连桶一起放** ✓（桶能查到 ✓、流体也照样能查到 ✓）。

        // 输入：烈焰血（匠魂本体流体 ✓）+ 它的桶
        var inSlot = builder.addSlot(RecipeIngredientRole.INPUT, 8, 22)
                .addFluidStack(recipe.input(), recipe.inputMb());
        ChargedCreeperMeltingJeiCategory.addBucket(inSlot, recipe.input());

        // "原料"：避雷针（放在容器正上方）—— 详细说明挂在它的 tooltip 上 ✓
        builder.addSlot(RecipeIngredientRole.INPUT, 36, 22)
                .addItemStack(new ItemStack(Items.LIGHTNING_ROD))
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.tinkersnewlife.lightning_rod_conversion.how")
                            .withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.tinkersnewlife.lightning_rod_conversion.container")
                            .withStyle(ChatFormatting.GRAY));
                });

        // 输出：液态闪电（本模组铁魔法联动流体 ✓）+ 它的桶
        var outSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 22)
                .addFluidStack(recipe.output(), recipe.outputMb());
        ChargedCreeperMeltingJeiCategory.addBucket(outSlot, recipe.output());
    }

    @Override
    public void draw(Conversion recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 64, 24);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
                Component.translatable("jei.tinkersnewlife.lightning_rod_conversion.ratio",
                        LightningRodConversionHandler.INPUT_PER_CYCLE,
                        LightningRodConversionHandler.OUTPUT_PER_CYCLE,
                        recipe.inputMb(), recipe.outputMb()),
                0, 46, 0xFF808080, false);
        graphics.drawString(font,
                Component.translatable("jei.tinkersnewlife.lightning_rod_conversion.single"),
                0, 58, 0xFF808080, false);
    }
}
