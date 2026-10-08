package com.mofengbaizhi.tinkersnewlife.compat.jei;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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

import java.util.List;

/**
 * ⭐ §1122 <b>JEI 分类「同 id 变体」</b>（用户口径 ✓「把所有同 id 变体全部折叠在一起，
 * 只在鼠标光标悬停时展开」✓ —— 追问后选定**方案 B** ✓：JEI 原生子类型折叠对"匠魂已注册过"
 * 的工具/部件**无效** ✗（重复注册会被 JEI 抛 IllegalArgumentException ✓ 实测 282 条 ✓）
 * ⇒ 改由本分类来承担 ✓）。
 *
 * <h2>怎么用（⭐ 这就是"悬停时展开"✓）</h2>
 * 在 JEI 里**把鼠标按要求查看配方的键（默认 {@code R}）对着任意一件工具/部件** ✓
 * ⇒ ⭐ 本分类会**把该物品的【全部材料变体一页列出来】** ✓
 * （⚠ JEI 原生那套只能"一格 + 滚轮切换" ✗ 达不到"展开" ✓ 所以才有本分类 ✓）。
 *
 * <h2>页面构成</h2>
 * <ul>
 *   <li>左上 ⭐ <b>被点的那一只</b>（OUTPUT 槽 ✓ 带它自己的名字 ✓）；</li>
 *   <li>右侧与下方 ⭐ <b>同一物品的所有变体</b>（按材料组合去重 ✓ 每行 9 个 ✓）；
 *       ⚠ 每个槽位**光标悬停即可看到那一只的具体材料名** ✓（JEI 自带行为 ✓ 不用我们画 ✗）。</li>
 * </ul>
 * ⚠ 变体由 {@link VariantGroupIndex} 统一枚举（取自创造栏展示物品 ✓ 与 JEI 列表同源 ✓）。
 */
public class VariantGroupJeiCategory implements IRecipeCategory<VariantGroupJeiCategory.VariantGroup> {

    /** 本分类的类型 ✓ */
    public static final RecipeType<VariantGroup> TYPE =
            RecipeType.create(TinkersNewlife.MOD_ID, "variant_group", VariantGroup.class);

    /** 每行放几个变体 ✓ */
    private static final int PER_ROW = 9;
    /** 一个页面最多画几个 ✓（9 × 5 = 45 ✓ 够看 ✓ 超出部分在标题里说明 ✗） */
    private static final int MAX_SLOTS = 45;

    private static final int WIDTH = 166;
    private static final int HEIGHT = 88;

    private final IDrawable background;
    private final IDrawable icon;
    private final Component title;

    public VariantGroupJeiCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        this.icon = helper.createDrawableItemStack(new ItemStack(
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.resources.ResourceLocation("tconstruct", "pickaxe")) == null
                        ? net.minecraft.world.item.Items.IRON_PICKAXE
                        : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                                new net.minecraft.resources.ResourceLocation("tconstruct", "pickaxe"))));
        this.title = Component.translatable("jei.tinkersnewlife.variant_group");
    }

    @Override
    public RecipeType<VariantGroup> getRecipeType() {
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
    public void setRecipe(IRecipeLayoutBuilder builder, VariantGroup group, IFocusGroup focuses) {
        // ⭐ 左上：被点的那一只 ✓（让"我现在看的是哪一只"一目了然 ✓）
        builder.addSlot(RecipeIngredientRole.OUTPUT, 4, 4)
                .addItemStack(group.base())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.tinkersnewlife.variant_group.based")
                                .withStyle(ChatFormatting.GRAY)));

        // ⭐ 其余：同一物品的所有变体 ✓（一页铺开 ✓ 这就是"展开" ✓）
        int i = 0;
        for (ItemStack stack : group.variants()) {
            if (i >= MAX_SLOTS) {
                break;
            }
            int x = 4 + 18 * (i % PER_ROW);
            int y = 26 + 18 * (i / PER_ROW);
            builder.addSlot(RecipeIngredientRole.OUTPUT, x, y).addItemStack(stack);
            i++;
        }
    }

    @Override
    public void draw(VariantGroup group, IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics,
                     double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, group.base().getHoverName(), 24, 8, 0xFFFFFFFF, false);
        int total = group.variants().size();
        Component line = total > MAX_SLOTS
                ? Component.translatable("jei.tinkersnewlife.variant_group.more", total, MAX_SLOTS)
                : Component.translatable("jei.tinkersnewlife.variant_group.count", total);
        graphics.drawString(font, line.copy().withStyle(ChatFormatting.GRAY), 4, 78, 0xFF808080, false);
    }

    /**
     * ⭐ 一个"同 id 变体组" ✓。
     *
     * @param base     被聚焦的那一只（也是页面左上展示的那只 ✓）
     * @param variants ⭐ 该物品的**全部材料变体**（已按材料组合去重 ✓ 已排序 ✓）
     */
    public record VariantGroup(ItemStack base, List<ItemStack> variants) {
    }
}
