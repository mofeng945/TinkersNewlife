package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 喜热（§980）· <b>盔甲组 ＋ 工具组</b>的实现。
 *
 * <p>口径（用户 2026-10-04 选定"1:1 照原模组" ✓）：
 * <ul>
 *   <li><b>盔甲</b>（1:1 ✓）：每件 25% 火焰伤害减免 ✓（原模组 {@code ItemPromethiumArmor.Companion#onEntityDamage}
 *     里的 {@code 0.25f}/件 ✓ 实读 ✓）／「幽步」炎热时加速 ✓（原模组挂**原版** {@code MOVEMENT_SPEED} 效果 ✓
 *     —— 这里照抄，不改用属性修饰符 ✓）／「焚身」炎热时缓慢回血 ✓／
 *     「蒸汽」岩浆上行走 ✓（**直接挂原模组自己的 {@code lavafishing:lava_walker} 效果** ✓ 按注册名取 ✓
 *     不引它的类 ⇒ 它不在场时这里整块不生效 ✓ —— 但材料本身也只在它场时才存在 ✓）；</li>
 *   <li><b>工具</b>（我方设计）：着火或熔岩中 ⇒ 近战伤害 +60%（在 {@code HeatLoverModifier} 里 ✓）
 *     ＋ 挖掘速度 +20%（本类 {@link #onBreakSpeed} ✓）；</li>
 *   <li><b>远程</b>（我方设计）：弹射物点燃目标（在 {@code HeatLoverModifier} 里 ✓）。</li>
 * </ul>
 *
 * <p>判定口径与「海王之力」一致 ✓：**工具**看主手 ✓；**盔甲**看四个盔甲槽 ✓；
 * "炎热"＝{@code isInLava() || isOnFire()} ✓。
 *
 * <p>⚠ 客户端那一半（熔岩下视野清晰）在 {@code client/handler/HeatLoverClientHandler} ✓
 * （公共类不许引客户端渲染类 ✓ 见本仓 §801 那条反向同理 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class HeatLoverHandler {

    private HeatLoverHandler() {}

    /** 词条 id（与 {@code Modifiers.HEAT_LOVER} 一致 ✓） */
    private static final ModifierId HEAT_LOVER =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "heat_lover"));

    /** 原模组口径：每件 25% 火焰伤害减免 ✓（4 件 = 100% ⇒ 完全免疫火焰伤害 ✓ 与原模组一致 ✓） */
    private static final float FIRE_REDUCTION_PER_PIECE = 0.25F;
    /** 炎热时的挖掘速度加成（我方设计 ✓） */
    private static final float HEAT_MINING_BONUS = 0.2F;
    /** 炎热时回血间隔与血量（原模组只说"缓慢" ⇒ 取 2 秒 1 点 ✓ ⚠ 原值未确证 ✗） */
    private static final int REGEN_INTERVAL_TICKS = 40;
    private static final float REGEN_AMOUNT = 1.0F;
    /** 原模组自己的岩浆行走效果（按注册名取 ✓ 不引它的类 ✓） */
    private static final ResourceLocation LAVA_WALKER_EFFECT = new ResourceLocation("lavafishing", "lava_walker");
    /** 效果刷新时长（每次 tick 刷 ✓ 取 2 秒足够 ✓） */
    private static final int EFFECT_DURATION_TICKS = 40;

    /** 这一件是不是"带喜热的匠魂工具/盔甲" ✓（{@code ToolHelper} 会挡掉损坏态 ✓） */
    public static boolean hasTrait(ItemStack stack) {
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifiers().getLevel(HEAT_LOVER) > 0;
    }

    /** 主手 ✓ 或四件盔甲任意一件 ✓ */
    public static boolean wearsOrHolds(Player player) {
        if (hasTrait(player.getMainHandItem())) return true;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (hasTrait(player.getItemBySlot(slot))) return true;
        }
        return false;
    }

    /** 四件盔甲里**带喜热的件数** ✓（火伤减免按件叠加 ✓ 与原模组一致 ✓） */
    public static int armorPieces(Player player) {
        int count = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (hasTrait(player.getItemBySlot(slot))) count++;
        }
        return count;
    }

    /** "炎热"＝泡在熔岩里或身上着火 ✓ */
    public static boolean isHot(LivingEntity entity) {
        return entity.isInLava() || entity.isOnFire();
    }

    /** 盔甲四件的被动：岩浆行走 ✓ 炎热时加速 ✓ 炎热时缓慢回血 ✓ */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        // ── 蒸汽（靴子）：可在岩浆上行走 ⇒ 挂原模组自己的 lava_walker 效果（常驻刷新 ✓ 不要求"已在熔岩中" ✓
        //    否则永远迈不出第一步 ✗）──
        if (hasTrait(player.getItemBySlot(EquipmentSlot.FEET))) {
            MobEffect lavaWalker = ForgeRegistries.MOB_EFFECTS.getValue(LAVA_WALKER_EFFECT);
            if (lavaWalker != null) {
                player.addEffect(new MobEffectInstance(lavaWalker, EFFECT_DURATION_TICKS, 0, false, false));
            }
        }

        if (!isHot(player)) return;
        int pieces = armorPieces(player);
        if (pieces <= 0) return;

        // ── 幽步（护腿）：炎热时移动加速 ⇒ 1:1 用**原版**速度效果 ✓（原模组就是这么做的 ✓）──
        if (hasTrait(player.getItemBySlot(EquipmentSlot.LEGS))) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION_TICKS, 0, false, false));
        }

        // ── 焚身（胸甲）：炎热时缓慢回血 ✓ ──
        if (hasTrait(player.getItemBySlot(EquipmentSlot.CHEST))
                && player.getHealth() < player.getMaxHealth()
                && player.tickCount % REGEN_INTERVAL_TICKS == 0) {
            player.heal(REGEN_AMOUNT);
        }
    }

    /**
     * 盔甲四件：**火焰伤害 -25%/件** ✓（原模组口径 ✓）。
     *
     * <p>放在 {@code LivingHurtEvent}（护甲结算之前 ✓）⇒ 与护甲减伤**相乘**叠加 ✓。
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAmount() <= 0.0F) return;
        if (!event.getSource().is(DamageTypeTags.IS_FIRE)) return;
        int pieces = armorPieces(player);
        if (pieces <= 0) return;
        float keep = Math.max(0.0F, 1.0F - FIRE_REDUCTION_PER_PIECE * pieces);
        event.setAmount(event.getAmount() * keep);
    }

    /** 工具（我方设计）：着火或熔岩中 ⇒ 挖掘速度 +20% ✓ */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null) return;
        if (!hasTrait(player.getMainHandItem())) return;
        if (!isHot(player)) return;
        event.setNewSpeed(event.getNewSpeed() * (1.0F + HEAT_MINING_BONUS));
    }
}
