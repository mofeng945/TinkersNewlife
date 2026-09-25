package com.mofengbaizhi.tinkersnewlife.content.recipe;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModRecipeSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.primitive.IntLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.helper.FluidOutput;
import slimeknights.mantle.recipe.ingredient.EntityIngredient;
import slimeknights.tconstruct.library.recipe.entitymelting.EntityMeltingRecipe;

/**
 * 实体熔炼「<b>闪电苦力怕（充能苦力怕）→ 液态闪电</b>」：普通苦力怕**照旧**出熔融玻璃 ✓（§286 那条搁置的需求 ✓）。
 *
 * <h2>为什么必须自己写一条（数据包做不到）</h2>
 * 匠魂的 {@code EntityMeltingRecipe.matches(EntityType)} 与
 * {@code EntityMeltingRecipeCache.findRecipe(RecipeManager, EntityType)} <b>只吃 EntityType</b> ✗ ——
 * 而"闪电苦力怕"与普通苦力怕**是同一个 {@code minecraft:creeper}**（区别只在实体数据 {@code powered} 上）✗
 * ⇒ 纯 JSON 表达不出"只让充能的出货" ✗（§286 已查证 ✓）。
 *
 * <h2>⭐ 但匠魂自己留了口子：{@code getOutput(LivingEntity)} 带实体</h2>
 * 反编译 {@code EntityMeltingModule.interactWithEntities}（匠魂 3.11.2.166）：
 * <pre>
 *   EntityMeltingRecipe recipe = findRecipe(entity.getType());
 *   fluid  = recipe.getOutput((LivingEntity) entity);   // ← 带实体 ✓
 *   damage = recipe.getDamage();
 *   if (entity.hurt(source, damage)) tank.fill(fluid, EXECUTE);   // 每次命中填一次 fluid
 * </pre>
 * ⇒ 覆盖 {@code getOutput(LivingEntity)} 就能按"这一只到底充能没有"分支 ✓
 * —— <b>不需要 mixin</b> ✓（§286 当时写 mixin 是绕远路；本类把它取代 ✓）。
 *
 * <h2>数值口径（用户 §286 口径"每滴血 50 mB"✓）</h2>
 * 充能时每次命中给 {@code charged_amount}（默认 <b>100 mB</b> = 匠魂每次伤害 2 × 50 mB/滴血 ✓）；
 * 苦力怕 20 血 ⇒ 10 次命中 ⇒ <b>10 × 100 = 1000 mB = 恰好 1 个雷电瓶</b> ✓
 * （与 §286③ 的"雷电瓶 ⇄ 液态闪电 1:1 回环"天然对齐 ✓）。
 *
 * <h2>⚠ 没装铁魔法时的行为</h2>
 * 本模组的 {@code liquid_lightning} 挂在铁魔法联动组（没装就不注册 ✓）⇒ 这里查表拿不到流体时
 * <b>直接退回配方原本的产出</b>（熔融玻璃 50 mB / 伤害 2 ✓），与匠魂原版**完全一致** ✓
 * ⇒ 所以本配方**不需要** {@code mod_loaded} 条件，也不会在"没有铁魔法"的包里把匠魂那条顶掉之后留个空壳 ✗。
 *
 * <h2>⚠ 为什么要覆盖匠魂的同 ID 配方</h2>
 * 匠魂的缓存是"遍历全部 entity_melting 配方、返回**第一个 matches** 的"，而遍历顺序来自 {@code RecipeManager} 里
 * HashMap 的 key 顺序 ✗ ⇒ 若只是**新增**一条 {@code tinkersnewlife:creeper}，它会和匠魂自带的
 * {@code tconstruct:creeper} **抢**（不可控 ✗）。
 * 所以本类对应的 JSON 写在 <b>{@code data/tconstruct/recipes/smeltery/entity_melting/creeper.json}</b>
 * （**同 ID 覆盖** ✓，包内先例：{@code tinkerscalibration} 就是这么覆盖匠魂烈焰人配方的 ✓）。
 */
public class ChargedCreeperMeltingRecipe extends EntityMeltingRecipe {

    /** 充能时的产出流体（本模组铁魔法联动流体；没装铁魔法 ⇒ 查不到 ⇒ 退回原产出 ✓） */
    private static final ResourceLocation CHARGED_FLUID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "liquid_lightning_still");

    public static final RecordLoadable<ChargedCreeperMeltingRecipe> LOADER = RecordLoadable.create(
            ContextKey.ID.requiredField(),
            EntityIngredient.LOADABLE.requiredField("entity", r -> r.ingredient),
            FluidOutput.Loadable.REQUIRED.requiredField("result", r -> r.output),
            IntLoadable.FROM_ONE.defaultField("damage", 2, true, r -> r.damage),
            IntLoadable.FROM_ONE.defaultField("charged_amount", 100, true, r -> r.chargedAmount),
            ChargedCreeperMeltingRecipe::new);

    // ⚠ 父类那几个字段是 private（子类取不到 ✗）⇒ 自己留一份，LOADER 的访问器读这一份 ✓
    private final EntityIngredient ingredient;
    private final FluidOutput output;
    private final int damage;
    /** 充能状态下每次命中的产出量（mB） */
    private final int chargedAmount;

    public ChargedCreeperMeltingRecipe(ResourceLocation id, EntityIngredient ingredient, FluidOutput output,
                                       int damage, int chargedAmount) {
        super(id, ingredient, output, damage);
        this.ingredient = ingredient;
        this.output = output;
        this.damage = damage;
        this.chargedAmount = chargedAmount;
    }

    /** 充能（{@code Creeper.isPowered()} ✓）且充能流体在场（装了铁魔法 ✓）⇒ 出液态闪电；否则原产出 ✓ */
    @Override
    public FluidStack getOutput(LivingEntity entity) {
        if (entity instanceof Creeper creeper && creeper.isPowered()) {
            Fluid lightning = ForgeRegistries.FLUIDS.getValue(CHARGED_FLUID);
            if (lightning != null) {
                return new FluidStack(lightning, chargedAmount);
            }
        }
        return super.getOutput(entity);
    }

    /**
     * 充能时每次命中的产出量（mB），即 JSON 里的 {@code charged_amount}。
     *
     * <p>⚠ 给 JEI 展示用（{@code ChargedCreeperMeltingJeiCategory}）—— 那边要把这个数画进界面，
     * 而字段是 private ⇒ 必须留一个取值器 ✓（不在 JEI 里重复写死 100 ✗ 免得两处口径漂移 ✓）。
     */
    public int getChargedAmount() {
        return chargedAmount;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.CHARGED_CREEPER_MELTING.get();
    }
}
