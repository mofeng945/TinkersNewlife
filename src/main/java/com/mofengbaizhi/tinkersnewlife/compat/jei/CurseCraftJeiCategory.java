package com.mofengbaizhi.tinkersnewlife.compat.jei;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.recipe.CurseCraftRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * JEI 分类：咒力合成仪式。
 *
 * <p>展示：核心古神物品 + 材料（≤8）+ 产物 + 咒力需求（含"每 tick 吸收 10 点"的提示）。
 * 催化剂 = 格赫罗斯矿石（右键它即可在 JEI 里搜到这个分类）。
 */
public class CurseCraftJeiCategory implements IRecipeCategory<CurseCraftRecipe> {

    public static final RecipeType<CurseCraftRecipe> TYPE =
            RecipeType.create(TinkersNewlife.MOD_ID, "curse_craft", CurseCraftRecipe.class);

    private static final int WIDTH = 166;
    private static final int HEIGHT = 92;

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;

    public CurseCraftJeiCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(ModItems.GHELOTH_ORE.get()));
        this.title = Component.translatable("jei.tinkersnewlife.curse_craft");
    }

    @Override
    public RecipeType<CurseCraftRecipe> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, CurseCraftRecipe recipe, IFocusGroup focuses) {
        // 核心古神物品（放到格赫罗斯矿石正上方的灯笼）
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 30)
                .addIngredients(recipe.core())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.tinkersnewlife.curse_craft.core_hint")
                                .withStyle(ChatFormatting.GRAY)));

        // 材料（最多 8 个，3 列排布）
        int index = 0;
        for (Ingredient ingredient : recipe.materials()) {
            builder.addSlot(RecipeIngredientRole.INPUT, 32 + (index % 3) * 20, 4 + (index / 3) * 20)
                    .addIngredients(ingredient);
            index++;
        }

        // 产物
        builder.addSlot(RecipeIngredientRole.OUTPUT, 142, 30)
                .addItemStack(recipe.result())
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.tinkersnewlife.curse_craft.result_hint")
                                .withStyle(ChatFormatting.GRAY)));

        // 催化剂：格赫罗斯矿石（结构中心）
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStack(new ItemStack(ModItems.GHELOTH_ORE.get()));
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemStack(new ItemStack(ModItems.GHELOTH_ORE.get().asItem()));
    }

    @Override
    public void draw(CurseCraftRecipe recipe, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font,
                Component.translatable("jei.tinkersnewlife.curse_craft.curse",
                        CursePowerHelper.formatAmount(recipe.curse())).withStyle(ChatFormatting.LIGHT_PURPLE),
                0, 62, 0xFFAA55FF, false);
        graphics.drawString(font,
                Component.translatable("jei.tinkersnewlife.curse_craft.per_tick",
                        CursePowerHelper.formatAmount(CurseCraftRecipe.CURSE_PER_TICK)),
                0, 72, 0xFF808080, false);
        graphics.drawString(font,
                Component.translatable("jei.tinkersnewlife.curse_craft.materials",
                        recipe.materials().size(), CurseCraftRecipe.MAX_MATERIALS),
                0, 82, 0xFF808080, false);
    }

    /** 供插件使用：避免直接引用 VanillaTypes 常量时 IDE 报警 */
    static mezz.jei.api.ingredients.IIngredientType<ItemStack> itemStackType() {
        return VanillaTypes.ITEM_STACK;
    }
}
