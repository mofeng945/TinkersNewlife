package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.ArmorModifierHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.Modifier;

/**
 * 悚怖武装（恐钢／悚怖钢盔甲自带特性）—— <b>纯被动</b>。
 *
 * <p>用户 2026 口径：<b>删去「悚域展开」主动技能</b>（连同按键绑定、经验消耗、冷却、判定与提示），
 * 只保留盔甲给予的效果；授予逻辑改为「穿戴时每 40 tick 检查一次，缺失就用<b>无限时长</b>补上」，
 * 卸下则移除。</p>
 *
 * <ul>
 *   <li><b>穿戴</b>（任一部件带该特性且未损坏）：每 40 tick 维持 夜视／抗火／限伤（无限时长）；</li>
 *   <li><b>卸下</b>（不再有任何带该特性的部件）：移除这三个效果；</li>
 *   <li>另外每 tick 解冻穿戴者（免疫冰霜，保持原口径）。</li>
 * </ul>
 *
 * <p>⚠ 只认「无限时长 + 隐藏粒子」这一签名 ⇒ 移除时<b>不会</b>误删玩家自己喝的药水或其它来源的同类效果。</p>
 */
public class DreadsteelArmorTrait extends Modifier {

    /** 被动检查间隔：每 40 tick（2 秒）检查一次 */
    private static final int PASSIVE_CHECK_INTERVAL = 40;

    /** 被动效果等级：0 ⇒ I 级 */
    private static final int PASSIVE_AMPLIFIER = 0;

    /** 名字用于在盔甲上查等级（与注册 id 一致） */
    private static final String MODIFIER_ID = "dreadsteel_armor";

    /**
     * §768：记录"上一次看到的本特性等级"的持久数据键 ✓
     * <p>⚠ 用它区分"**曾经穿着、现在脱了**"（该清 ✓）与"**从来没穿过**"（**绝不该清** ✗）。
     */
    private static final String KEY_LAST_LEVEL = "tinkersnewlife:dreadsteel_last_level";

    /** 盔甲上该特性的总等级（损坏的部件不计入；≤0 ⇒ 没穿） */
    private static int getTotalLevel(LivingEntity wearer) {
        return ArmorModifierHelper.getTotalModifierLevelOnArmor(wearer, MODIFIER_ID);
    }

    /** 该效果是否是我们加的：无限时长 + 无粒子 + 非信标/潮涌来源 */
    private static boolean isOurEffect(LivingEntity entity, MobEffect effect) {
        MobEffectInstance instance = entity.getEffect(effect);
        return instance != null
                && instance.isInfiniteDuration()
                && !instance.isVisible()
                && !instance.isAmbient();
    }

    /** 缺失才补：已是我们的无限时长实例就不再 addEffect（避免无谓的同步包） */
    private static void keepEffect(LivingEntity entity, MobEffect effect) {
        if (!isOurEffect(entity, effect)) {
            entity.addEffect(new MobEffectInstance(
                    effect, MobEffectInstance.INFINITE_DURATION, PASSIVE_AMPLIFIER, false, false, true));
        }
    }

    /** 卸下时移除我们加的效果（只认上面那个签名） */
    private static void dropEffect(LivingEntity entity, MobEffect effect) {
        if (isOurEffect(entity, effect)) {
            entity.removeEffect(effect);
        }
    }

    // ======================== 事件处理器 ========================

    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
    public static class Handler {

        /**
         * 被动效果（对任意穿甲者：玩家／仆从等）：
         * 每 40 tick 检查一次 —— 穿着则以无限时长维持 夜视／抗火／限伤；卸下则移除。
         */
        @SubscribeEvent
        public static void onArmorTick(LivingEvent.LivingTickEvent event) {
            LivingEntity wearer = event.getEntity();
            if (wearer.level().isClientSide) return;

            if (wearer.tickCount % PASSIVE_CHECK_INTERVAL == 0) {
                int level = getTotalLevel(wearer);
                net.minecraft.nbt.CompoundTag data = wearer.getPersistentData();
                int lastLevel = data.getInt(KEY_LAST_LEVEL);

                if (level <= 0) {
                    // ⚠⚠ §768 修：**只有"曾经穿着、现在脱了"才清** ✗
                    //   原来这里是无条件清 ✗ ⇒ **没穿过悚怖钢的人也会被我们清掉夜视** ✗✗：
                    //   `isOurEffect` 的签名是"无限时长 + 无粒子 + 非环境" ✓，
                    //   而**血族的夜视**正好也是这个签名 ✗（`VampireNightVisionEffectInstance`：
                    //   `MobEffectInstance(NIGHT_VISION, -1, 0, false, false, false)` ✓ javap 实核 ✓）
                    //   ⇒ 于是我们每 2 秒把血族的夜视删一次 ✗，血族每 2.5 秒再补回来 ✗
                    //   ⇒ **血族夜视"开了关关了开"就是这条引起的** ✗✗（§767 完整调用栈实证 ✓）。
                    if (lastLevel > 0) {
                        // 卸下（或部件全部损坏）：移除**本特性曾经给予的**效果 ✓
                        dropEffect(wearer, MobEffects.NIGHT_VISION);
                        dropEffect(wearer, MobEffects.FIRE_RESISTANCE);
                        if (ModEffects.DAMAGE_LIMIT.get() != null) {
                            dropEffect(wearer, ModEffects.DAMAGE_LIMIT.get());
                        }
                    }
                } else {
                    // 穿着：缺失就补，无限时长（不会再自己过期）
                    keepEffect(wearer, MobEffects.NIGHT_VISION);
                    keepEffect(wearer, MobEffects.FIRE_RESISTANCE);
                    if (ModEffects.DAMAGE_LIMIT.get() != null) {
                        keepEffect(wearer, ModEffects.DAMAGE_LIMIT.get());
                    }
                }

                if (level != lastLevel) {
                    data.putInt(KEY_LAST_LEVEL, level);
                }
            }

            // 免疫冰霜：仍每 tick 解冻（只在真被冻结时才去查等级，开销可忽略）
            if (wearer.getTicksFrozen() > 0 && getTotalLevel(wearer) > 0) {
                wearer.setTicksFrozen(0);
            }
        }
    }
}
