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
 *
 * <p>⚠⚠ <b>本类当前临时挂了"充能熔炼诊断"事件（{@code onLivingAttackDiag} / {@code onLivingTickDiag}）</b>
 * —— 为定位用户报告"普通苦力怕能熔成玻璃、充能后就不掉血"而加 ✓ **定位完要整段删掉** ✗
 * （连类上的 {@code @EventBusSubscriber} 注解一起删 ✓）。
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(
        modid = TinkersNewlife.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
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

    /**
     * 充能（{@code Creeper.isPowered()} ✓）且充能流体在场（装了铁魔法 ✓）⇒ 出液态闪电；否则原产出 ✓
     */
    @Override
    public FluidStack getOutput(LivingEntity entity) {
        // ⚠⚠ 临时诊断（**这是唯一能回答"匠魂到底有没有把这只交给我们的配方"的位置** ✓）：
        //   匠魂 `interactWithEntities` 里 `fluid = recipe.getOutput((LivingEntity) entity)` ——
        //   只有在 `canMeltEntity` 通过、且**已经成功 hurt 之前**才会走到这里 ✓
        //   ⇒ 打出来就能一刀切开两种病因（见备忘录 §649）：
        //     ① **有这条日志** ⇒ 匠魂确实在处理它、配方也选中了我们 ⇒ 病在"伤害/产出"环节；
        //     ② **没有这条日志** ⇒ `canMeltEntity` 就把它挡了 ⇒ 病在"进不去熔炼流程"。
        if (!entity.level().isClientSide) {
            TinkersNewlife.LOGGER.info(
                    "[充能熔炼诊断] getOutput 被调用! powered={} hp={}/{} uuid={} pos={},{},{}",
                    (entity instanceof Creeper c && c.isPowered()),
                    entity.getHealth(), entity.getMaxHealth(), entity.getUUID(),
                    entity.getBlockX(), entity.getBlockY(), entity.getBlockZ());
        }
        if (entity instanceof Creeper creeper && creeper.isPowered()) {
            Fluid lightning = ForgeRegistries.FLUIDS.getValue(CHARGED_FLUID);
            if (lightning != null) {
                return new FluidStack(lightning, chargedAmount);
            }
        }
        return super.getOutput(entity);
    }

    // ============================================================
    //  ⚠⚠ 临时诊断（定位用户报告："普通苦力怕能熔成玻璃、充能后就不掉血" ⇒ 定位完**整段删除**）
    // ============================================================

    /**
     * 苦力怕吃伤害前打一行：能区分两种完全不同的病因 ——
     * <ul>
     *   <li><b>日志一条都没有</b> ⇒ 匠魂的 {@code canMeltEntity} 就返回了 false（压根没发起攻击）
     *       ⇒ 病在"进不去熔炼流程"（燃料/热/火免/防火效果/`invulnerable` 标记）；</li>
     *   <li><b>日志有、且 powered=true</b> ⇒ 攻击确实发生了 ⇒ 病在"伤害被免疫掉"
     *       ⇒ 看那几个布尔值（`fireImmune` / `invuln` / `fireRes`）是哪个为真。</li>
     * </ul>
     */
    // ⚠⚠ 关键修正（§650 的诊断失败根因）：`@SubscribeEvent` **默认 `receiveCanceled = false`** ✗
    //   ⇒ 一旦有别的模组把 `LivingAttackEvent` 取消掉，**本方法根本不会被调用** ✗
    //   ⇒ 表现就是"一条日志都没有"，而我会把它误读成"**没发起攻击**" ✗ —— 前几轮就是这么被带偏的 ✗。
    //   ⭐ 现在显式 `receiveCanceled = true` + 最低优先级 ⇒ **取消与否都能看到** ✓
    //   （已查明：莱特兰 `LHAttackListener.onAttack` 会 `event.setCanceled(true)` ⇒
    //     匠魂 `hurt` 返回 false ⇒ `tank.fill` 不执行 ⇒ **不出流体** ✓ 这正是用户看到的"不熔炼" ✓）
    @net.minecraftforge.eventbus.api.SubscribeEvent(
            priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST,
            receiveCanceled = true)
    public static void onLivingAttackDiag(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (event.getEntity().level().isClientSide) return;
        // ⚠ 只在**匠魂的伤害源**上打 —— 否则闪电/法术会把它刷爆 ✗（上一版就是被刷爆了）
        String src = event.getSource().getMsgId();
        if (!src.startsWith("tconstruct.")) return;
        TinkersNewlife.LOGGER.info(
                "[充能熔炼诊断] 受击 entity={} powered={} hp={}/{} pos={},{},{} uuid={} "
                        + "canceled={} src={} amount={} fireImmune={} invuln={} invulnTime={} fireRes={}",
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(creeper.getType()),
                creeper.isPowered(),
                creeper.getHealth(),
                creeper.getMaxHealth(),
                creeper.getBlockX(), creeper.getBlockY(), creeper.getBlockZ(),
                creeper.getUUID(),
                event.isCanceled(),
                src,
                event.getAmount(),
                creeper.fireImmune(),
                creeper.isInvulnerable(),
                creeper.invulnerableTime,
                creeper.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE));
    }

    /** 苦力怕每 tick：**只打"附近有冶炼炉方块实体"的那些** ✓（含普通与充能 ⇒ 天然有对比组 ✓ 也不刷屏 ✓） */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLivingTickDiag(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (creeper.level().isClientSide) return;
        if (creeper.tickCount % 20 != 0) return;

        // ⭐ 这一版的关键：**不再是只看充能的**（上一版漏了对比组 ✗），而是
        //   **把炉子方块实体与实体坐标一起打出来** —— 直接回答"它到底在不在炉子扫描范围里" ✓
        String smeltery = "（拿不到服务端世界）";
        if (creeper.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            var found = new StringBuilder();
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        var pos = creeper.blockPosition().offset(dx, dy, dz);
                        var be = sl.getBlockEntity(pos);
                        if (be == null) continue;
                        String n = net.minecraftforge.registries.ForgeRegistries.BLOCK_ENTITY_TYPES
                                .getKey(be.getType()).toString();
                        if (n.contains("smeltery") || n.contains("foundry") || n.contains("melter")) {
                            found.append(n).append('@').append(pos.getX()).append(',')
                                 .append(pos.getY()).append(',').append(pos.getZ()).append(' ');
                        }
                    }
                }
            }
            // ⚠ 只记录"附近真有冶炼炉"的苦力怕 ⇒ 既能拿到对比组（普通 vs 充能 ✓），又不会全图刷屏 ✓
            if (found.length() == 0) return;
            smeltery = found.toString();
        }

        StringBuilder eff = new StringBuilder();
        for (var inst : creeper.getActiveEffects()) {
            var key = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(inst.getEffect());
            eff.append(key).append('x').append(inst.getAmplifier()).append(';');
        }
        TinkersNewlife.LOGGER.info(
                "[充能熔炼诊断] tick entity={} powered={} hp={}/{} invulnTime={} pos={},{},{} "
                        + "| 判据: isRemoved={} invulnerable={} fireImmune={} fireRes={} "
                        + "| effects=[{}] | 附近冶炼炉: {}",
                net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(creeper.getType()),
                creeper.isPowered(),
                creeper.getHealth(),
                creeper.getMaxHealth(),
                creeper.invulnerableTime,
                creeper.getBlockX(), creeper.getBlockY(), creeper.getBlockZ(),
                creeper.isRemoved(),
                creeper.isInvulnerable(),
                creeper.fireImmune(),
                creeper.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE),
                eff.length() == 0 ? "（无）" : eff.toString(),
                smeltery);
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
