package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * 词条·抗魔（黑暗金属护甲部件自带）：
 * <ol>
 *   <li>穿戴者受到魔法伤害（{@link DamageSource#isMagic()}）时，
 *       把 {@code 5%×等级 + 1.5} 点魔法伤害转化为物理伤害——护甲在伤害结算前已按原版流程减免物理部分，
 *       此处等效为直接减免等量魔法伤害（护甲/魔抗词条材料等级一般 1 级）；</li>
 *   <li>§795 <b>免疫失明与黑暗</b>（用户口径 ✓）：
 *       <b>不会获得</b>这两个效果 ✓ ＋ 身上已经有的会被<b>清掉</b> ✓。</li>
 * </ol>
 *
 * <h2>§795 免疫怎么做的（两条一起上 ✓ 缺一不可）</h2>
 * <ol>
 *   <li>{@link MobEffectEvent.Applicable} —— 它对应原版 {@code LivingEntity#canBeAffected} 判定 ✓
 *       （Forge 源码实核 ✓ {@code @HasResult} ✓ ALLOW/DENY/DEFAULT ✓）
 *       ⇒ 置 {@code DENY} ⇒ 这次施加<b>直接不生效</b> ✓（连时长刷新也刷不上 ✓）；</li>
 *   <li>{@link LivingEvent.LivingTickEvent} 每秒扫一次，把<b>已经挂在身上</b>的失明/黑暗移除 ✓ ——
 *       因为第 1 条只挡"新施加的" ✗：先被致盲再穿上盔甲、或者效果随存档加载进来，
 *       都绕过了 canBeAffected ✗。
 *       <p>⚠ <b>必须先 {@code hasEffect} 再 {@code removeEffect}</b> ✗ ——
 *       无条件 {@code removeEffect} 会在效果并不存在时也先发 {@code MobEffectEvent.Remove}
 *       （§758 的实测教训：诡厄借此凭空播了一次"取消"音效 ✗）。</li>
 * </ol>
 * 两条都只在<b>服务端</b>跑 ✓，并且都先查"这个人是不是真穿着抗魔部件"（
 * {@link ArmorModifierHelper#getTotalModifierLevelOnArmor} ✓）⇒ 没穿就一点都不参与 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DarkMetalMagicResistHandler {

    /** 词条 id（ArmorModifierHelper 按字符串查找） */
    private static final String MODIFIER_ID = "dark_metal_magic_resist";

    /** §795 免疫的效果：失明 ＋ 黑暗 ✓（用户口径 ✓） */
    private static final Set<MobEffect> IMMUNE_EFFECTS = Set.of(MobEffects.BLINDNESS, MobEffects.DARKNESS);

    /** 原版魔法伤害 tag（DamageTypeTags 无 IS_MAGIC 常量，显式建 TagKey） */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.damagesource.DamageType> MAGIC =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    new net.minecraft.resources.ResourceLocation("minecraft", "is_magic"));

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        DamageSource source = event.getSource();
        if (source == null || !source.is(MAGIC)) return;   // 仅魔法伤害
        LivingEntity victim = event.getEntity();

        int lv = ArmorModifierHelper.getTotalModifierLevelOnArmor(victim, MODIFIER_ID);
        if (lv <= 0) return;

        float amount = event.getAmount();
        float converted = amount * 0.05f * lv + 1.5f;      // 5%×等级 + 1.5 点 → 转为物理（此处直接减免）
        event.setAmount(Math.max(0f, amount - converted));
    }

    /**
     * §795 <b>不会获得</b>失明/黑暗 ✓ —— {@link MobEffectEvent.Applicable} 就是原版
     * {@code canBeAffected} 的判定点 ✓，置 {@code DENY} 就等于"这次施加被否决" ✓。
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity().level().isClientSide) return;
        MobEffectInstance instance = event.getEffectInstance();
        if (instance == null || !IMMUNE_EFFECTS.contains(instance.getEffect())) return;
        if (!wears(event.getEntity())) return;
        event.setResult(Event.Result.DENY);
    }

    /**
     * §795 把<b>已经挂在身上</b>的失明/黑暗清掉 ✓（每秒一次 ✓）——
     * 第 {@link #onEffectApplicable} 条只挡新施加的 ✗，这条补"先中招后穿甲"和"随存档带进来的" ✓。
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide) return;
        if (entity.tickCount % 20 != 0) return;                   // 每秒一次 ✓ 够用且省 ✓
        if (!wears(entity)) return;
        for (MobEffect effect : IMMUNE_EFFECTS) {
            // ⚠ 先判存在再移除 ✗ —— 无条件 removeEffect 会凭空发一次 MobEffectEvent.Remove（§758 的教训 ✓）
            if (entity.hasEffect(effect)) {
                entity.removeEffect(effect);
            }
        }
    }

    /** 这个人身上（任意护甲槽）有没有"抗魔"这个词条 ✓ */
    private static boolean wears(LivingEntity entity) {
        return ArmorModifierHelper.getTotalModifierLevelOnArmor(entity, MODIFIER_ID) > 0;
    }
}
