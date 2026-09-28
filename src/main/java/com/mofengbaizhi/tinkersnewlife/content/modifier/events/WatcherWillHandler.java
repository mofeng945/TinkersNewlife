package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.WatcherWillModifier;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import com.mofengbaizhi.tinkersnewlife.integration.irons_spellbooks.IronSpellsSpellAccess;
import com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * <b>守望意志</b>（虚空金属盔甲自带，有等级、多件叠加）：每级 <b>10% 闪避</b> ＋ 吟唱速度 <b>+15%/级</b>。
 *
 * <h2>①闪避</h2>
 * 用 {@link LivingAttackEvent}（最低优先级 ⇒ 别人改完伤害数再判 ✓）取消<b>整次攻击</b> ✓：
 * 概率 = <b>身上所有护甲上该特性等级之和 × 10%</b>，<b>100% 封顶</b> ✓（用户口径「每级 10%」「多件可叠加」✓）。
 * 触发时给一点末影粒子当反馈 ✓（无声、不打断 ✓）。
 *
 * <h2>②铁魔法吟唱速度 +15%/级</h2>
 * 与材料「秘银」的 {@code MithrilHandler} 完全同一套写法 ✓：每 10 tick 刷新一次
 * 铁魔法 {@code CAST_TIME_REDUCTION} 属性上的**瞬态**修饰符 ✓（用固定 UUID ⇒ 不会越叠越多 ✓）。
 * 护甲上的特性等级之和就是加成等级 ✓。
 *
 * <h2>③诡厄吟唱速度 +15%/级</h2>
 * 不在本类 —— 诡厄没有公开的吟唱速度属性，只能挂它自己的
 * {@code ModAttributes#getCastingSpeed}（见 {@code mixin/GoetyCastSpeedMixin} ✓）；
 * 本类只提供 {@link #levels} 给它调用 ✓（mixin 里不写业务逻辑 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WatcherWillHandler {

    /** 特性 id（{@link ArmorModifierHelper} 按字符串查 ✓） */
    public static final String MODIFIER = "watcher_will";
    /** 每级闪避概率：10% ✓ */
    public static final double DODGE_PER_LEVEL = 0.10D;
    /** 每级吟唱加速：15% ✓（诡厄与铁魔法同一条口径 ✓） */
    public static final double CAST_SPEED_PER_LEVEL = 0.15D;

    /** 铁魔法吟唱缩减属性的瞬态修饰符 UUID（固定值 ⇒ 反复刷新也只留一条 ✓） */
    private static final UUID CAST_SPEED_UUID = UUID.fromString("b71f0c62-5a44-4d8e-9a1c-2f0d3c7e5b91");

    private WatcherWillHandler() {}

    /** 身上所有护甲上「守望意志」的等级之和（护甲损坏时不算 ✓）——诡厄 mixin 也用这个 ✓ */
    public static int levels(LivingEntity wearer) {
        if (wearer == null) return 0;
        return ArmorModifierHelper.getTotalModifierLevelOnArmor(wearer, MODIFIER);
    }

    // ============================================================
    //  ① 闪避
    // ============================================================

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttack(LivingAttackEvent event) {
        LivingEntity wearer = event.getEntity();
        if (wearer.level().isClientSide) return;
        int level = levels(wearer);
        if (level <= 0) return;
        double chance = Math.min(1.0D, DODGE_PER_LEVEL * level);
        if (wearer.getRandom().nextDouble() >= chance) return;

        event.setCanceled(true);
        // §750 诊断：守望意志闪避成功 ✓（只是确认这条没在疯狂触发 ✓）
        com.mofengbaizhi.tinkersnewlife.util.VoidArmorDiag.log("dodge",
                "守望意志：闪避成功 ✓ 玩家={} 等级={}",
                wearer.getName().getString(), level);
        // 只是反馈：一团末影粒子（服务端发，附近玩家都能看到 ✓）
        if (wearer.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    wearer.getX(), wearer.getY() + wearer.getBbHeight() * 0.5D, wearer.getZ(),
                    12, 0.35D, 0.45D, 0.35D, 0.02D);
        }
    }

    // ============================================================
    //  ② 铁魔法吟唱速度（属性刷新）
    // ============================================================

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        if (player.tickCount % 10 != 0) return;

        /*
         * ⭐「无铁魔法不报错」的关键一步（§724）：**先做纯 ModList 判断**，确认铁魔法在场再碰它的访问层。
         * 为什么不能只靠 IronSpellsSpellAccess 自己的保护 ✗：那个类的 init() 在失败时虽然只是
         * `failed = true` + 返回 null（不会崩 ✓），但它**会 LOGGER.warn 一行**（还带堆栈）✗ ——
         * 每 10 tick 调一次 ⇒ 没装铁魔法时每次进游戏都会刷一条"铁魔法法术访问层初始化失败" ✗。
         * 换成 IronSpellsReflector.isIronSpellsAvailable()（内部就是 ModList 判断 ✓，无反射、无日志 ✓）
         * ⇒ 没铁魔法时本方法**一行日志都不打**、直接返回 ✓。
         */
        if (!IronSpellsReflector.isIronSpellsAvailable()) return;

        Attribute castReduction = IronSpellsSpellAccess.attribute("CAST_TIME_REDUCTION");
        if (castReduction == null) return;                       // 没装铁魔法 ⇒ 跳过 ✓
        AttributeInstance instance = player.getAttribute(castReduction);
        if (instance == null) return;

        instance.removeModifier(CAST_SPEED_UUID);
        int level = levels(player);        // 多件叠加：四件 × 5 级 = 20 级 ⇒ +300%（用户口径"每级 15%"，不额外设限 ✓）
        if (level > 0) {
            instance.addTransientModifier(new AttributeModifier(CAST_SPEED_UUID,
                    "watcher_will_cast_speed",
                    CAST_SPEED_PER_LEVEL * level,
                    AttributeModifier.Operation.ADDITION));
        }
    }
}
