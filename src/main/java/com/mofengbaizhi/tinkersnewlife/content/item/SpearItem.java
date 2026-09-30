package com.mofengbaizhi.tinkersnewlife.content.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.SpearCombatHandler;
import com.mofengbaizhi.tinkersnewlife.util.EndingLibraryComponents;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * <b>长矛（Spear）</b>—— 把 MC <b>1.21.11《Mounts of Mayhem》</b>原版长矛移植成匠魂武器（§835／§836）。
 *
 * <h2>§836：这一版是<b>照官方未混淆客户端反编译出来的原版逻辑</b>重写的</h2>
 * 用户口径「**能直接移植原版长矛逻辑吗**」✓ ⇒ 我们下载了 Mojang 官方的
 * <b>未混淆 1.21.11 客户端</b>（{@code piston-data}，35MB ✓）并用仓库自带的 CFR 反编译，
 * 读到了原版真源码 ✓ —— 长矛在 1.21.11 里**不是一个自定义 Item 类** ✗，而是**数据组件**驱动的：
 * <ul>
 *   <li>{@code minecraft:kinetic_weapon}（{@code KineticWeapon} ✓）＝ <b>冲锋</b>；
 *       {@code Item#use} 里 {@code kineticWeapon != null ⇒ player.startUsingItem(hand)} ✓；</li>
 *   <li>{@code minecraft:piercing_weapon}（{@code PiercingWeapon} ✓）＝ <b>戳刺</b>（一次戳到射程内所有目标 ✓）；</li>
 *   <li>{@code minecraft:attack_range}（{@code AttackRange} ✓）＝ <b>最小 2.0 / 最大 4.5</b> 格（创造 2.0/6.5 ✓ 怪物 ×0.5 ✓）；</li>
 *   <li>{@code minecraft:use_effects}（{@code UseEffects(true, false, 1.0f)} ✓）＝
 *       <b>蓄力时不减速、而且能疾跑</b> ✓✓（本条就是 §835 用户实测"速度变慢了"的答案 ✗ 见下文）；</li>
 *   <li>{@code minecraft:swing_animation}＝STAB ✓、{@code minimum_attack_charge}=1.0 ✓、伤害类型 {@code minecraft:spear} ✓。</li>
 * </ul>
 *
 * <h2>§836 修正：为什么 §835 那版会"右键变成蓄力慢走"</h2>
 * 1.21.11 的减速判定改成了<b>数据驱动</b> ✓：{@code LocalPlayer#modifyInput} 里
 * {@code if (isUsingItem() && !isPassenger()) input *= useItem.get(USE_EFFECTS).speedMultiplier()} ✓，
 * 而长矛给的正是 {@code speedMultiplier = 1.0} ＋ {@code canSprint = true} ✓ ⇒ <b>原版长矛蓄力根本不减速</b> ✓。
 * 但 **1.20.1 把这条写死成 ×0.2** ［{@code LocalPlayer#aiStep} L647 ✗］⇒ §835 那版照搬原版
 * {@code startUsingItem} 就吃到了这个 1.20.1 特有的惩罚 ✗。
 * <p>⭐ 用户口径：「**终焉图书馆 mod 有全套数据组件，可以参考模仿**」✓ ⇒ 本版**不再自己写混入** ✗，
 * 改为<b>照他们的数据格式给长矛打 {@code use_effects} 组件</b> ✓（{@link EndingLibraryComponents} ✓）：
 * 他们的 {@code UseEffectsComponent} 就是原版的 {@code use_effects} ✓、并由他们的
 * {@code LocalPlayerMixin} 消费（{@code ×5×speedMultiplier} 抵消 1.20.1 的 ×0.2 ✓ ＋ 放行疾跑 ✓）
 * ⇒ <b>与原版 1.21.11 完全同语义</b> ✓ 而且姿势照旧（{@code UseAnim.SPEAR} ✓ 原版也是这个 ✓）。
 *
 * <h2>数值口径（**照原版铁矛那一档**，见 {@link SpearCombatHandler}）</h2>
 * 原版每档一张参数表（木 0.7×／石铜 0.82×／铁 0.95×／金 0.7×／钻 1.075×／下界合金 1.2× ✓），
 * 匠魂武器的"档"由**材料**决定 ⇒ 我们取<b>铁那一档</b>当统一口径 ✓（原版差异记在 §836 备忘里 ✓）。
 */
public class SpearItem extends ModifiableItem {

    public static final ToolDefinition SPEAR_DEFINITION =
            ToolDefinition.create(new ResourceLocation(TinkersNewlife.MOD_ID, "spear"));

    /** 固定 UUID ⇒ 幂等 ✓ */
    private static final UUID REACH_UUID = UUID.fromString("d8e2f3a4-b5c6-4d7e-9f01-2a3b4c5d6e7f");

    /** 实体交互距离 +1.5 格 ⇒ 合计 <b>4.5</b> 格 ✓ ＝ 原版 {@code AttackRange.maxRange} ✓ */
    public static final double REACH_BONUS = 1.5D;

    /** 长按才能冲锋 ⇒ 基础使用时长给满 ✓；匠魂自己有"使用中"词条时让位 ✓（字节码实读：无词条时 super 返回 0 ✓） */
    private static final int CHARGE_USE_TICKS = 72000;

    public SpearItem(Properties properties) {
        super(properties, SPEAR_DEFINITION);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create(super.getAttributeModifiers(slot, stack));
        if (slot == EquipmentSlot.MAINHAND) {
            // ⚠ 属性名必须 forge:entity_reach（够生物 ✓）——本仓 §806 踩过 forge:reach_distance 不存在的坑 ✗
            map.put(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(REACH_UUID, "Spear Entity Reach",
                            REACH_BONUS, AttributeModifier.Operation.ADDITION));
        }
        return map;
    }

    // ============================================================
    //  右键长按＝冲锋 —— 和原版 1.21.11 一模一样的三件事 ✓
    //    · use()           → startUsingItem（原版 Item#use 见到 KINETIC_WEAPON 就是这句 ✓）
    //    · getUseDuration  → 72000（原版 Item#getUseDuration 对 kinetic weapon 返回 72000 ✓）
    //    · getUseAnimation → SPEAR（原版 Item#getUseAnimation 对 kinetic weapon 返回 SPEAR ✓）
    // ============================================================

    @Override
    public int getUseDuration(ItemStack stack) {
        int ability = super.getUseDuration(stack);      // 匠魂"使用中"词条优先 ✓
        return ability > 0 ? ability : CHARGE_USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        UseAnim ability = super.getUseAnimation(stack);
        // §844：返回 NONE ＝ 关掉 1.20.1 那套"三叉戟端举"（用户：「太丑了」✗），
        //       改由我们自己的客户端动画画（照原版 SpearAnimations 的数学 ✓）。
        return ability != UseAnim.NONE ? ability : UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // ⭐ 先让匠魂自己的能力（格挡/投掷/交互…）有机会消费这次右键 ✗ 不抢它的活 ✓
        InteractionResultHolder<ItemStack> base = super.use(level, player, hand);
        if (base.getResult().consumesAction()) return base;
        if (hand != InteractionHand.MAIN_HAND) return base;

        ItemStack stack = player.getItemInHand(hand);
        ToolStack tool = ToolHelper.getToolStack(stack);
        if (tool == null || tool.isBroken()) return base;   // 损坏即失效 ✓（与全模组同一口径 ✓）

        player.startUsingItem(hand);
        if (player instanceof ServerPlayer serverPlayer) SpearCombatHandler.startCharge(serverPlayer);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        super.onUseTick(level, living, stack, remaining);
        // 原版把冲锋结算放在 ItemStack#onUseTick 里，且**只在服务端**跑 ✓（ItemStack 字节码实读 ✓）⇒ 同口径 ✓
        if (level.isClientSide) return;
        if (!(living instanceof ServerPlayer player)) return;
        SpearCombatHandler.tickCharge(player, stack, remaining, getUseDuration(stack));
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (living instanceof ServerPlayer player) SpearCombatHandler.stopCharge(player);
        super.releaseUsing(stack, level, living, timeLeft);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof ServerPlayer player) SpearCombatHandler.stopCharge(player);
        super.onStopUsing(stack, entity, count);
    }

    // ============================================================
    //  use_effects 组件（**照终焉图书馆的数据格式** ✓ 见 EndingLibraryComponents ✓）
    //    原版 1.21.11 长矛 = UseEffects(canSprint = true, speedMultiplier = 1.0)
    //      ⇒ 蓄力不减速 ✓ 还能疾跑 ✓
    //    1.20.1 把这条写死成 ×0.2 ✗ ⇒ 交给终焉图书馆的组件系统去还原原版行为 ✓
    //    （他们不在场时这两个钩子都是 no-op ✓ 不影响别的东西 ✓）
    // ============================================================

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        EndingLibraryComponents.ensureSpearUseEffects(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (level.isClientSide) return;
        // 幂等且只在缺组件时才写 ✓（别的模组/命令动过 `Component` 标签也能自愈 ✓）
        EndingLibraryComponents.ensureSpearUseEffects(stack);
    }
}
