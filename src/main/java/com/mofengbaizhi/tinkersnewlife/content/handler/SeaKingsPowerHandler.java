package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 海王之力（§960 规格）· <b>工具组 ＋ 盔甲组</b>的实现（§967）。
 *
 * <p>用户口径（逐条）：
 * <ul>
 *   <li><b>工具</b>：水中或雨中 ⇒ 无视挖掘速度惩罚 ✓（<b>未做</b> ✗）攻速 +50% ✓（<b>本轮</b>）伤害 +60% ✓（§966 已做 ✓）；</li>
 *   <li><b>盔甲</b>：水下呼吸 ✓（<b>本轮</b>）水中视野更清晰 ✓（<b>未做</b> ✗ 客户端渲染）速度 +20% ✓（<b>本轮</b>）伤害减免 +30% ✓（<b>本轮</b>）；</li>
 *   <li>钓竿组与远程组都还没做 ✗（见 §960 ✓）。</li>
 * </ul>
 *
 * <p>为什么用"玩家 tick ＋ 属性修饰符"而不是 TC 的属性钩子：本仓**没有**现成的
 * {@code AttributesModifierHook} 用例 ✗ 且它在 {@code ModifierHooks} 里的**常量名没查到** ✗
 * ⇒ 改走**全部已验证**的路子 ✓（原版属性 API ＋ {@code ToolHelper.getToolStack(...).getModifiers().getLevel(...)}
 * 判词条 ✓ —— 后者是 {@code SoldiersSaberHandler} 的现成写法 ✓）。
 *
 * <p>⚠ 判定口径：**工具**看主手 ✓；**盔甲**看四个盔甲槽任意一件 ✓；条件都是原版
 * {@code isInWaterOrRain()} ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class SeaKingsPowerHandler {

    private SeaKingsPowerHandler() {}

    /** 词条 id（与 {@code Modifiers.SEA_KINGS_POWER} 一致 ✓） */
    private static final ModifierId SEA_KINGS_POWER =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "sea_kings_power"));

    /** 攻速 +50%（用户口径 ✓） */
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("7f3a51c2-9b4e-4d18-8a2f-3c6d9e0f1a2b");
    /** 速度 +20%（用户口径 ✓） */
    private static final UUID MOVE_SPEED_UUID = UUID.fromString("2c8d6b40-1f9a-4e73-9d55-8b1e7a4c3f60");

    /** 这一件是不是"带海王之力的匠魂工具" ✓（{@code ToolHelper} 会挡掉损坏态 ✓） */
    private static boolean hasTrait(ItemStack stack) {
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifiers().getLevel(SEA_KINGS_POWER) > 0;
    }

    /** 主手 ✓ 或四件盔甲任意一件 ✓ */
    private static boolean wearsOrHolds(Player player) {
        if (hasTrait(player.getMainHandItem())) return true;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (hasTrait(player.getItemBySlot(slot))) return true;
        }
        return false;
    }

    /** 工具攻速 ✓ ＋ 盔甲速度 ✓（条件：水/雨中 ✓）＋ 盔甲水下呼吸 ✓ */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        boolean inWater = player.isInWaterOrRain();

        // ── 工具：攻速 +50%（主手带词条 ＋ 水/雨中 ✓）──
        boolean toolActive = inWater && hasTrait(player.getMainHandItem());
        applyModifier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_UUID,
                "sea_kings_power_attack_speed", 0.5D, toolActive);

        // ── 盔甲：速度 +20%（穿任意一件带词条 ＋ 水/雨中 ✓）──
        boolean armorActive = inWater && hasTrait(player.getItemBySlot(EquipmentSlot.CHEST))
                || inWater && hasTrait(player.getItemBySlot(EquipmentSlot.HEAD))
                || inWater && hasTrait(player.getItemBySlot(EquipmentSlot.LEGS))
                || inWater && hasTrait(player.getItemBySlot(EquipmentSlot.FEET));
        applyModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVE_SPEED_UUID,
                "sea_kings_power_move_speed", 0.2D, armorActive);

        // ── 盔甲：水下呼吸（穿任意一件带词条 ⇒ 水下不掉氧 ✓）──
        if (player.isUnderWater() && armorActive) {
            player.setAirSupply(player.getMaxAirSupply());
            if (player.getRemainingFireTicks() > 0) player.clearFire();
        }
    }

    /** 幂等地挂/摘一个临时修饰符 ✓（不改玩家永久属性 ✓ UUID 固定 ✓） */
    private static void applyModifier(AttributeInstance instance, UUID id, String name,
                                      double amount, boolean active) {
        if (instance == null) return;
        boolean present = instance.getModifier(id) != null;
        if (active && !present) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (!active && present) {
            instance.removeModifier(id);
        }
    }

    /**
     * 盔甲：**水中或雨中受伤 -30%** ✓（用户口径 ✓）。
     *
     * <p>⚠ 放在 {@code LivingHurtEvent}（护甲结算**之前** ✓）⇒ 与护甲的减伤**相乘**叠加 ✓
     * （"30% 伤害减免"按乘法理解 ✓ 与 §960 的口径一致 ✓）。
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getAmount() <= 0.0F) return;
        if (!player.isInWaterOrRain()) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (hasTrait(player.getItemBySlot(slot))) {
                event.setAmount(event.getAmount() * 0.7F);
                return;
            }
        }
    }
}