package com.mofengbaizhi.tinkersnewlife.util;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * <b>绝对防御穿透</b>：把别的模组"只看标签穿不过去"的绝对免疫暂时失效，让本模组的真伤落地。
 *
 * <p>为什么需要这个类：{@link TruePierce} 的伤害源带齐了 {@code #minecraft:bypasses_*} 七个标签，
 * 绝大多数"无敌"（原版无敌帧、灾变利维坦离水无敌、Mowzie 太阳鸟的<b>太阳屏障</b>）都认标签，
 * 光靠标签就够了。但有一类免疫<b>根本不看标签</b>，只看"攻击者是谁/攻击者身上某个属性多高"，
 * 事件层就把打击整个取消掉 —— 标签在这里一点用都没有。
 *
 * <p>目前处理一种（潘多拉之咒 · 现实压制）：
 * <ul>
 *   <li>词条 {@code curseofpandora:reality}「现实压制」：
 *       {@code LivingAttackEvent} 里判断<b>攻击者的"现实指数"是否 ≥ 词条等级</b>，
 *       低于就直接 {@code setCanceled(true)}（伤害事件根本不发生，
 *       {@code hurt()} 与 {@code LivingHurtEvent} 都不会跑）。</li>
 *   <li>官方留的后门正是这个属性：把攻击者的现实指数临时抬到阈值以上，
 *       判定就会放行 —— 于是伤害照常走完整管线（护甲/限伤/死亡结算全部正常），
 *       而不是用 {@code setHealth} 硬扣（那样会绕过别的 Boss 阶段机）。</li>
 * </ul>
 *
 * <p>用法：打击前后各调一次。{@code remove} 必须在 {@code finally} 里调，
 * 否则属性修饰符会永久留在攻击者身上。
 *
 * <p>所有跨模组访问都走反射 + try/catch：模组不在场、属性改名都不影响正常游戏（只是不穿透）。
 */
public final class AbsoluteDefense {

    private AbsoluteDefense() {
    }

    /** 现实指数属性 id（潘多拉之咒） */
    private static final ResourceLocation REALITY_ATTRIBUTE =
            new ResourceLocation("curseofpandora", "reality_index");

    /**
     * 临时现实指数。词条等级上限是 7（配置 {@code max_rank: 7}），判定是
     * "攻击者现实指数 ≥ 词条等级才放行"，所以取一个远高于上限的固定值即可，
     * 不必去读目标身上那条词条到底几级。
     */
    private static final double REALITY_BOOST = 1000.0D;

    /** 临时修饰符的固定 UUID（每名攻击者同一时刻只会有一个，便于精确移除） */
    private static final UUID REALITY_MODIFIER_ID =
            UUID.fromString("7b1c9f2e-4a55-4d0e-9c31-8e6a2f0d5b47");

    /** 属性查表结果（查到即缓存） */
    private static volatile Attribute cachedRealityAttribute;
    /** 是否已经确定"这个属性永远查不到"（属性注册表非空却没有它 → 模组不在场） */
    private static volatile boolean realityAttributeAbsent = false;
    /** 失败日志只打一次（否则每次打击都刷屏） */
    private static volatile boolean realityWarned = false;

    // ============================================================
    //  对外：一次打击期间"超越绝对防御"
    // ============================================================

    /**
     * 打击开始：让 {@code attacker} 这一击能穿过"绝对免疫"。
     * 返回是否需要配对调用 {@link #end(LivingEntity)}（false 表示什么都没做，可以不调）。
     */
    public static boolean begin(@Nullable LivingEntity attacker) {
        return attacker != null && transcendReality(attacker);
    }

    /**
     * 诊断（只记一次）：目标正带着"太阳屏障"类效果。
     *
     * <p>太阳鸟（Mowzie 的 {@code mowziesmobs:umvuthi}）的无敌<b>认标签</b> ——
     * 它自己的 {@code hurt()} 写的是"有 SUNBLOCK 效果且伤害源不带
     * {@code #minecraft:bypasses_invulnerability} 就免伤"。本模组真伤源带着这个标签，
     * 所以<b>本来就能穿</b>，不需要额外处理；这里只在真的遇到时留一行证据，
     * 免得以后再花时间怀疑"是不是没穿透"。
     */
    public static void noteSunblockTarget(LivingEntity target) {
        if (sunblockNoteLogged || target == null) return;
        try {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            if (id == null || !"mowziesmobs".equals(id.getNamespace()) || !"umvuthi".equals(id.getPath())) return;
            // 有没有开屏障：按效果注册名找 mowziesmobs:sunblock（不依赖它的类结构）
            ResourceLocation sunblockId = new ResourceLocation("mowziesmobs", "sunblock");
            var effect = ForgeRegistries.MOB_EFFECTS.getValue(sunblockId);
            if (effect == null || !target.hasEffect(effect)) return;
            sunblockNoteLogged = true;
            TinkersNewlife.LOGGER.info("[穿透] 命中开着太阳屏障的太阳鸟（mowziesmobs:umvuthi）："
                    + "该无敌认 #bypasses_invulnerability 标签，真伤源已带此标签 → 直接穿透");
        } catch (Throwable ignored) {
        }
    }

    private static volatile boolean sunblockNoteLogged = false;

    /** 打击结束：移除临时修饰符。攻击者已死亡/被移除也安全（属性实例仍可访问） */
    public static void end(@Nullable LivingEntity attacker) {
        if (attacker == null) return;
        try {
            AttributeInstance inst = realityIndexOf(attacker);
            if (inst != null) inst.removeModifier(REALITY_MODIFIER_ID);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  现实压制（潘多拉之咒）
    // ============================================================

    /** 把攻击者的现实指数临时抬到阈值之上；成功返回 true */
    private static boolean transcendReality(LivingEntity attacker) {
        try {
            AttributeInstance inst = realityIndexOf(attacker);
            if (inst == null) return false;
            // 已有本次的临时修饰符就不重复加（防止上一击的 finally 没跑到时叠加）
            if (inst.getModifier(REALITY_MODIFIER_ID) != null) return true;
            inst.addTransientModifier(new AttributeModifier(
                    REALITY_MODIFIER_ID, "tinkersnewlife:true_pierce_transcend",
                    REALITY_BOOST, AttributeModifier.Operation.ADDITION));
            return true;
        } catch (Throwable t) {
            // 只在第一次失败时留一行日志：整场游戏每次打击都刷日志没意义
            if (!realityWarned) {
                realityWarned = true;
                TinkersNewlife.LOGGER.warn("[穿透] 现实压制穿透不可用（潘多拉之咒缺失或属性改名）: {}", t.toString());
            }
            return false;
        }
    }

    /** 攻击者的现实指数属性实例；属性不存在（模组不在场）返回 null */
    @Nullable
    private static AttributeInstance realityIndexOf(LivingEntity entity) {
        Attribute attr = realityAttribute();
        return attr == null ? null : entity.getAttribute(attr);
    }

    /** 按注册名查"现实指数"属性（不依赖类名/方法名，模组不在场返回 null） */
    @Nullable
    private static Attribute realityAttribute() {
        Attribute cached = cachedRealityAttribute;
        if (cached != null || realityAttributeAbsent) return cached;
        synchronized (AbsoluteDefense.class) {
            if (cachedRealityAttribute != null || realityAttributeAbsent) return cachedRealityAttribute;
            Attribute found = null;
            try {
                found = ForgeRegistries.ATTRIBUTES.getValue(REALITY_ATTRIBUTE);
                // ⚠ 只有"注册表非空却没有这个属性"才是可信的缺席；注册表整体为空说明还在注册期，
                //   不能缓存否定结果（否则第一次查表太早会让整个功能永久失效）
                if (found == null && !ForgeRegistries.ATTRIBUTES.getKeys().isEmpty()) {
                    realityAttributeAbsent = true;
                }
            } catch (Throwable ignored) {
                // 注册表异常：不缓存，下次打击再试
            }
            if (found != null) cachedRealityAttribute = found;
            return found;
        }
    }
}
