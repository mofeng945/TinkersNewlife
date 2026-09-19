package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.WizardArmorItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 巫师套装特性 · <b>魔力涌动</b>（无等级 ✓ 按件叠加 ✓）。
 *
 * <p>每穿 1 件巫师套（头/胸/腿/靴 ✓）：
 * <ul>
 *   <li>铁魔法：<b>法力上限 +125</b>、<b>法术强度 +5%</b>（走反射 ✓ 软依赖 ✓）；</li>
 *   <li>诡厄巫法：全学派法术强度 +5%（后续接 ✓）；</li>
 *   <li>全类型法术：法术伤害 <b>+7%</b>（后续接 ✓ 挂我们自己的伤害结算 ✓）。</li>
 * </ul>
 *
 * <p>⭐ 实现方式：**属性修饰符**（瞬时修饰符 ✓ 幂等刷新 ✓），不是往玩家身上塞状态 ✗
 * —— 与本模组"不给玩家挂隐身类状态"的一贯做法一致 ✓（见 §358 那段教训 ✓）。
 *
 * <p>⚠ 软依赖铁律：所有铁魔法类一律 {@code Class.forName} + 反射 ✓，拿不到就静默跳过 ✓
 * （没装铁魔法时本特性只剩"无效果"而不会崩 ✗→✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WizardArmorSetHandler {

    private WizardArmorSetHandler() {}

    /** 每件的数值（用户定案 ✓） */
    public static final int MANA_PER_PIECE = 125;
    public static final double SPELL_POWER_PER_PIECE = 0.05D;
    public static final double SPELL_DAMAGE_PER_PIECE = 0.07D;

    /** 修饰符身份（固定 UUID ✓ 保证"刷新=替换"而不是叠加 ✗） */
    private static final UUID MANA_UUID = UUID.fromString("7a13c0de-0001-4a11-9d10-1c5e0f5a0001");
    private static final UUID POWER_UUID = UUID.fromString("7a13c0de-0002-4a11-9d10-1c5e0f5a0002");

    /** 穿着一件巫师套吗（任意一件即启用 ✓ 用户确认 ✓） */
    public static boolean wearsAny(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (entity.getItemBySlot(slot).getItem() instanceof WizardArmorItem) return true;
        }
        return false;
    }

    /** 穿了几件（0~4 ✓ 魔力涌动按这个叠加 ✓） */
    public static int wornPieces(LivingEntity entity) {
        int n = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            if (entity.getItemBySlot(slot).getItem() instanceof WizardArmorItem) n++;
        }
        return n;
    }

    /** 动态提示行（工具提示用 ✓ 数字随件数变 ✓ 用户要求"别一大串静态描述" ✓） */
    public static net.minecraft.network.chat.MutableComponent surgeLine(int pieces) {
        return net.minecraft.network.chat.Component.translatable("trait.tinkersnewlife.mana_surge.line",
                pieces, pieces * MANA_PER_PIECE, (int) Math.round(pieces * SPELL_POWER_PER_PIECE * 100),
                (int) Math.round(pieces * SPELL_DAMAGE_PER_PIECE * 100));
    }

    /** 每秒把属性修饰符刷成当前件数对应值 ✓（幂等 ✓ 只为穿套装或刚脱下的实体做事 ✓） */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof LivingEntity living)) continue;
                int pieces = wornPieces(living);
                if (pieces == 0 && !hasOurModifier(living)) continue;   // 无关实体直接跳过 ✓
                applySurge(living, pieces);
            }
        }
    }

    private static boolean hasOurModifier(LivingEntity living) {
        AttributeInstance mana = attributeOf(living, IRON_MAX_MANA);
        return mana != null && mana.getModifier(MANA_UUID) != null;
    }

    private static void applySurge(LivingEntity living, int pieces) {
        setModifier(living, IRON_MAX_MANA, MANA_UUID, "tn_wizard_mana",
                pieces * (double) MANA_PER_PIECE, AttributeModifier.Operation.ADDITION);
        setModifier(living, IRON_SPELL_POWER, POWER_UUID, "tn_wizard_power",
                pieces * SPELL_POWER_PER_PIECE, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /** 增删一个瞬时修饰符（值为 0 就移除 ✓ 幂等 ✓） */
    private static void setModifier(LivingEntity living, Attribute attr, UUID id, String name,
                                    double amount, AttributeModifier.Operation op) {
        if (attr == null) return;
        AttributeInstance inst = living.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier old = inst.getModifier(id);
        if (amount <= 0) {
            if (old != null) inst.removeModifier(id);
            return;
        }
        if (old != null && old.getAmount() == amount) return;   // 没变就别动 ✗（避免每帧同步）
        if (old != null) inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    private static AttributeInstance attributeOf(LivingEntity living, Attribute attr) {
        return attr == null ? null : living.getAttribute(attr);
    }

    // ---------- 铁魔法属性（反射解析一次后缓存 ✓ 软依赖 ✓）----------
    private static Attribute IRON_MAX_MANA;
    private static Attribute IRON_SPELL_POWER;
    private static boolean ironResolved;

    private static void resolveIron() {
        if (ironResolved) return;
        ironResolved = true;
        IRON_MAX_MANA = ironAttribute("MAX_MANA");
        IRON_SPELL_POWER = ironAttribute("SPELL_POWER");
    }

    private static Attribute ironAttribute(String field) {
        try {
            Class<?> reg = Class.forName("io.redspace.ironsspellbooks.api.registry.AttributeRegistry");
            Object holder = reg.getField(field).get(null);
            Object attr = holder.getClass().getMethod("get").invoke(holder);
            return attr instanceof Attribute a ? a : null;
        } catch (Throwable ignored) {
            return null;   // 没装铁魔法（或版本不同）⇒ 无效果但不崩 ✓
        }
    }

    static {
        resolveIron();
    }
}