package com.mofengbaizhi.tinkersnewlife.compat.jei;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * JEI 分类：<b>闪电苦力怕 → 液态闪电</b>（{@code ChargedCreeperMeltingRecipe} 的展示项）。
 *
 * <h2>为什么需要单独一个分类（不能靠实体熔炼分类自己显示）</h2>
 * 匠魂的实体熔炼 JEI 分类读的是<b>无参</b> {@code EntityMeltingRecipe#getOutput()} ✓
 * 而"充能"这条分支只在<b>带实体</b>的 {@code getOutput(LivingEntity)} 里 ✓
 * ⇒ 匠魂自己那个分类<b>永远显示不出这条</b> ✗（它只会显示"苦力怕 → 熔融玻璃" ✓）。
 * 所以这里按本模组既有先例（{@link LightningRodConversionJeiCategory}）手工造一条展示 ✓。
 *
 * <h2>判据（三条都取自真实代码，不是另写一份口径）</h2>
 * <ul>
 *   <li><b>触发条件</b>：{@code Creeper#isPowered()} ✓（= 被闪电劈过的苦力怕 ✓）；</li>
 *   <li><b>每次命中产出</b>：{@code charged_amount} ✓（默认 100 mB ✓ 由 {@link Conversion#chargedAmount()} 带入 ✓）；</li>
 *   <li><b>普通苦力怕的对照</b>：直接取父类 <b>public</b> 的 {@code getOutput()} ✓
 *       （不写死"熔融玻璃 50" ✗ —— 匠魂以后改数值我们也不会说谎 ✓）。</li>
 * </ul>
 *
 * <h2>⚠ 联动流体</h2>
 * 输出（{@code liquid_lightning}）属**铁魔法联动组** ⇒ 没装铁魔法时不注册 ✓
 * ⇒ 插件那边查不到就<b>整条不注册</b> ✓（与避雷针转化那条同一套做法 ✓ 不留空头配方 ✗）。
 */
public class ChargedCreeperMeltingJeiCategory
        implements IRecipeCategory<ChargedCreeperMeltingJeiCategory.Conversion> {

    /**
     * JEI 里展示的一条"充能苦力怕熔炼"。
     *
     * @param output         充能时的产出流体（液态闪电）
     * @param chargedAmount  充能时每次命中的产出量（mB）= 配方里的 {@code charged_amount}
     * @param normalOutput   普通苦力怕的产出流体（熔融玻璃）= 父类 {@code getOutput()}
     * @param normalAmount   普通苦力怕每次命中的产出量（mB）
     */
    public record Conversion(Fluid output, int chargedAmount, Fluid normalOutput, int normalAmount) {}

    public static final RecipeType<Conversion> TYPE =
            RecipeType.create(TinkersNewlife.MOD_ID, "charged_creeper_melting", Conversion.class);

    /**
     * ⚠ 必须与 {@code ChargedCreeperMeltingRecipe.CHARGED_FLUID} 保持一致
     * （那个常量是 private ⇒ 这里只能重写一份；那边改了这边要跟着改）。
     */
    private static final ResourceLocation CHARGED_FLUID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "liquid_lightning_still");

    /** 展示用输入：闪电苦力怕（用命令获取，没有刷怪蛋也没有对应物品） */
    private static final String SPAWN_HINT = "/summon minecraft:creeper ~ ~ ~ {powered:1b}";

    private static final int WIDTH = 170;
    private static final int HEIGHT = 70;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private final Component title;

    public ChargedCreeperMeltingJeiCategory(IGuiHelper helper) {
        this.background = helper.createBlankDrawable(WIDTH, HEIGHT);
        // 苦力怕没有刷怪蛋 ⇒ 用苦力怕头当分类图标（一眼能认出是苦力怕 ✓）
        this.icon = helper.createDrawableItemStack(new ItemStack(Items.CREEPER_HEAD));
        this.arrow = helper.getRecipeArrow();
        this.title = Component.translatable("jei.tinkersnewlife.charged_creeper_melting");
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

    /** 输出流体（没装铁魔法时返回 null ⇒ 调用方跳过注册 ✓） */
    public static Fluid chargedFluid() {
        return ForgeRegistries.FLUIDS.getValue(CHARGED_FLUID);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Conversion recipe, IFocusGroup focuses) {
        // 输入：闪电苦力怕（苦力怕头 + tooltip 说明"必须是被闪电劈过的"）
        builder.addSlot(RecipeIngredientRole.INPUT, 8, 22)
                .addItemStack(new ItemStack(Items.CREEPER_HEAD))
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.tinkersnewlife.charged_creeper_melting.charged")
                            .withStyle(ChatFormatting.GRAY));
                    tooltip.add(Component.translatable("jei.tinkersnewlife.charged_creeper_melting.how")
                            .withStyle(ChatFormatting.GRAY));
                });

        // 输出：液态闪电（充能时每次命中的量 ✓）
        // §677 修（用户报告「JEI 里看不到液态闪电的配方」）：
        //   JEI 的"**为这个物品**查配方"只认**物品** ✗ —— 只画流体会导致
        //   "悬停液态闪电的【桶】"时查不到任何东西 ✗（桶确实存在：tinkersnewlife:liquid_lightning_bucket ✓）。
        //   ⇒ 同一个槽里**连桶一起放进去** ✓：桶能查到 ✓、流体也照样能查到 ✓。
        var outSlot = builder.addSlot(RecipeIngredientRole.OUTPUT, 96, 22)
                .addFluidStack(recipe.output(), recipe.chargedAmount())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                        Component.translatable("jei.tinkersnewlife.charged_creeper_melting.each",
                                recipe.chargedAmount()).withStyle(ChatFormatting.GRAY)));
        addBucket(outSlot, recipe.output());
    }

    /**
     * 把流体的**桶**也加进同一个槽（§677）—— 让 JEI 的"按物品查配方"能命中 ✓。
     *
     * <p>桶取 {@code Fluid#getBucket()} ✓ —— 反编译核过：{@code ForgeFlowingFluid#getBucket()}
     * 返回的就是 {@code props.bucket(...)} 绑的那个物品 ✓（仓库的 {@code FluidRegistrar} 正是这么绑的 ✓），
     * 拿不到时返回 {@code Items.AIR} ⇒ 直接跳过 ✓ 不会画出个空桶 ✗。
     * <p>⚠ 别用 {@code FluidType#getBucket(...)} ✗ —— 那个方法**要传 FluidStack**，
     * 而且实现就是转手调 {@code stack.getFluid().getBucket()} ✓（我第一版少传参数、编译直接挂了 ✗）。
     */
    static void addBucket(IRecipeSlotBuilder slot, Fluid fluid) {
        if (fluid == null) return;
        Item bucket = fluid.getBucket();
        if (bucket != null && bucket != Items.AIR) {
            slot.addItemStack(new ItemStack(bucket));
        }
    }

    @Override
    public void draw(Conversion recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                     double mouseX, double mouseY) {
        arrow.draw(graphics, 64, 24);
        var font = Minecraft.getInstance().font;
        // 第一行：普通苦力怕的产出（对照）—— 数值取自配方本身 ✓ 不写死 ✓
        graphics.drawString(font,
                Component.translatable("jei.tinkersnewlife.charged_creeper_melting.normal",
                        recipe.normalAmount(), recipe.normalOutput().getFluidType().getDescription()),
                0, 44, 0xFF808080, false);
        // 第二行：整只的账（20 血 / 每次 2 血 ⇒ 10 次命中）
        int whole = recipe.chargedAmount() * 10;
        graphics.drawString(font,
                Component.translatable("jei.tinkersnewlife.charged_creeper_melting.whole", whole),
                0, 56, 0xFF808080, false);
    }
}
