package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.WizardArmorItem;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.content.modifier.MagicConductionModifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * 巫师套装特性 · <b>魔力涌动</b>（无等级 ✓ 按件叠加 ✓）。
 *
 * <p>每穿 1 件巫师套（头/胸/腿/靴 ✓）：
 * <ul>
 *   <li><b>法力上限 +125</b>、<b>法术强度 +5%</b> —— 铁魔法属性 ✓ 走反射 ✓ 软依赖 ✓；</li>
 *   <li><b>法术伤害 +7%</b> —— 我们自己的出伤结算（{@link #onSpellHurt} ✓ 对铁魔法<b>与</b>诡厄巫法
 *       的法术一并生效 ✓ 判定复用「导魔」的{@link MagicConductionModifier#isMagicDamage} ✓）。</li>
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
    public static final int MANA_PER_PIECE = com.mofengbaizhi.tinkersnewlife.content.modifier.ManaSurgeTrait.MANA_PER_PIECE;
    public static final double SPELL_POWER_PER_PIECE = com.mofengbaizhi.tinkersnewlife.content.modifier.ManaSurgeTrait.SPELL_POWER_PER_PIECE;
    public static final double SPELL_DAMAGE_PER_PIECE = com.mofengbaizhi.tinkersnewlife.content.modifier.ManaSurgeTrait.SPELL_DAMAGE_PER_PIECE;

    /** 修饰符身份（固定 UUID ✓ 保证"刷新=替换"而不是叠加 ✗） */
    private static final UUID MANA_UUID = UUID.fromString("7a13c0de-0001-4a11-9d10-1c5e0f5a0001");
    private static final UUID POWER_UUID = UUID.fromString("7a13c0de-0002-4a11-9d10-1c5e0f5a0002");
    private static final UUID POTENCY_UUID = UUID.fromString("7a13c0de-0003-4a11-9d10-1c5e0f5a0003");

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
        // ⭐ 以**特性**为准 ✓（不是"物品类型" ✗）：把魔力涌动从甲上洗掉，效果就随之消失 ✓
        return com.mofengbaizhi.tinkersnewlife.content.modifier.ManaSurgeTrait.countWorn(entity);
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
                int shield = com.mofengbaizhi.tinkersnewlife.content.modifier.ManaShieldTrait.countWorn(living);
                if (pieces == 0 && shield == 0 && !hasOurModifier(living)
                        && !ManaShieldHandler.hasOurModifier(living)) continue;   // 无关实体直接跳过 ✓
                applySurge(living, pieces);
                // ⭐ 魔力护盾的生命上限顺带在这里结算 ✓ —— 并入同一次实体遍历（不为每个特性各扫全世界 ✗）
                ManaShieldHandler.applyShield(living, shield);
            }
        }
    }

    private static boolean hasOurModifier(LivingEntity living) {
        AttributeInstance mana = attributeOf(living, ironMaxMana());
        if (mana != null && mana.getModifier(MANA_UUID) != null) return true;
        AttributeInstance potency = attributeOf(living, goetyPotency());
        return potency != null && potency.getModifier(POTENCY_UUID) != null;
    }

    private static void applySurge(LivingEntity living, int pieces) {
        setModifier(living, ironMaxMana(), MANA_UUID, "tn_wizard_mana",
                pieces * (double) MANA_PER_PIECE, AttributeModifier.Operation.ADDITION);
        setModifier(living, ironSpellPower(), POWER_UUID, "tn_wizard_power",
                pieces * SPELL_POWER_PER_PIECE, AttributeModifier.Operation.MULTIPLY_TOTAL);
        // ⭐ 诡厄巫法：全学派法术强度（= Goety 的 SPELL_POTENCY 属性 ✓）
        //   用 MULTIPLY_TOTAL 而不是 ADDITION ✓ —— 与它的基准值无关：
        //   无论 potency 基准是 1.0（Goety 自己的袍子用 ADDITION +0.05 口径 ✓）还是别的，
        //   x*(1+0.05n) 都是"每件 +5%" ✓（软依赖：没装 Goety 时这一段静默跳过 ✓）
        setModifier(living, goetyPotency(), POTENCY_UUID, "tn_wizard_potency",
                pieces * SPELL_POWER_PER_PIECE, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    /**
     * 出伤侧：穿着整套/部分巫师甲的实体<b>施放法术</b>时，伤害 ×(1 + 7% × 件数) ✓。
     *
     * <p>判定复用「导魔」的{@link MagicConductionModifier#isMagicDamage}（原版女巫抗性标签、
     * {@code forge:is_magic}、名字含 magic、以及 {@code irons_spellbooks.*} / {@code goety.*}
     * 命名空间兜底 ✓）⇒ <b>铁魔法与诡厄巫法的法术一并生效</b> ✓。
     *
     * <p>放在 {@link EventPriority#LOWEST}（别人都改完之后 ✓）再乘，避免被后续结算抹掉；
     * 只乘一次、不改伤害类型 ⇒ 不会触发递归 ✗（与「混沌之流」那种嵌套 hurt 不同 ✓）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpellHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        // ⭐ 混沌之流的每段都是法术类型 ⇒ 巫师套装的"法术增伤 ×(1+0.1×件数)"不逐段乘 ✗
        //    否则段数 × 增幅 = 指数级膨胀 ✗（见 util/DamagePipeline）
        if (com.mofengbaizhi.tinkersnewlife.util.DamagePipeline.skipNested()) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity caster)) return;
        int pieces = wornPieces(caster);
        if (pieces <= 0) return;
        if (!MagicConductionModifier.isMagicDamage(event.getSource())) return;
        event.setAmount(event.getAmount() * (1.0F + (float) (pieces * SPELL_DAMAGE_PER_PIECE)));
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

    // ---------- 铁魔法 / 诡厄属性（⭐ 懒解析 + 逐次重试 ✓ 软依赖 ✓）----------

    private static Attribute IRON_MAX_MANA;
    private static Attribute IRON_SPELL_POWER;
    private static Attribute GOETY_SPELL_POTENCY;
    private static boolean loggedResolution;

    /**
     * ⚠ 踩过的坑（用户实测"穿甲法力上限没提升"）：以前是在<b>静态初始化块</b>里解析一次、
     * 并且先把 {@code ironResolved = true} 置上 ✗ —— 而本类是 {@code @Mod.EventBusSubscriber}
     * ⇒ <b>模组构造期就会被加载</b> ✗，那时铁魔法 / 诡厄的属性注册表<b>还没填好</b> ✗ ⇒
     * {@code RegistryObject.get()} 抛异常被吞掉 ✗ ⇒ 属性永久 null 且<b>永不重试</b> ✗✗
     * （特性提示照常显示 ⇒ 看起来就像"特性没效果" ✗）。
     *
     * <p>现在改成"<b>取到就缓存、取不到下次再来</b>" ✓ —— 走「刻印」已在用的
     * {@link IronSpellsSpellAccess#attribute(String)} ✓（它同样"没就绪就不缓存" ✓）。
     */
    private static Attribute ironMaxMana() {
        if (IRON_MAX_MANA != null) return IRON_MAX_MANA;
        IRON_MAX_MANA = IronSpellsSpellAccess.attribute("MAX_MANA");
        logResolved();
        return IRON_MAX_MANA;
    }

    private static Attribute ironSpellPower() {
        if (IRON_SPELL_POWER != null) return IRON_SPELL_POWER;
        IRON_SPELL_POWER = IronSpellsSpellAccess.attribute("SPELL_POWER");
        return IRON_SPELL_POWER;
    }

    private static Attribute goetyPotency() {
        if (GOETY_SPELL_POTENCY != null) return GOETY_SPELL_POTENCY;
        GOETY_SPELL_POTENCY = goetyAttribute("SPELL_POTENCY");
        return GOETY_SPELL_POTENCY;
    }

    /** 第一次成功解析时打一条日志 ✓（便于在 logs 里确认属性到底挂上没有 ✓） */
    private static void logResolved() {
        if (loggedResolution || IRON_MAX_MANA == null) return;
        loggedResolution = true;
        TinkersNewlife.LOGGER.info("[TinkersNewlife] 魔力涌动：铁魔法 MAX_MANA 属性已解析 ✓");
    }

    /** 诡厄巫法的全学派法术强度属性（软依赖 ✓ 没装就返回 null ✓ 下次再试 ✓） */
    private static Attribute goetyAttribute(String field) {
        try {
            Class<?> reg = Class.forName("com.Polarice3.Goety.init.ModAttributes");
            Object holder = reg.getField(field).get(null);
            if (holder == null) return null;
            Object attr = holder.getClass().getMethod("get").invoke(holder);
            return attr instanceof Attribute a ? a : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
