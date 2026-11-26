package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CosmicHandler {

    private static final ModifierId COSMIC_ORDER_VOICE = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "cosmic_order_voice"));

    private static final int DURATION_BASE = 100;
    private static final int DURATION_PER_LEVEL = 50;

    /** ⭐ 用户口径（2026-09-20）：**每次触发后冷却 20 秒** ✓ */
    private static final int COOLDOWN_TICKS = 20 * 20;
    /** 上次触发的世界时间（写在**工具**的持久数据里 ✓ 与「魅惑」同款 ✓） */
    private static final ResourceLocation KEY_LAST_TRIGGER =
            new ResourceLocation(TinkersNewlife.MOD_ID, "last_cosmic_trigger");
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker.level().isClientSide) return;
        LivingEntity target = event.getEntity();
        if (target == attacker) return;

        // ⭐ 统一取工具：玩家近战/弹射双路径+咒力核心兜底；怪物只查主手
        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), attacker, COSMIC_ORDER_VOICE);
        if (tool == null) return;

        int level = ToolHelper.getActiveModifierLevel(tool, COSMIC_ORDER_VOICE);
        if (level > 0) {
            int now = (int) attacker.level().getGameTime();
            if (now - tool.getPersistentData().getInt(KEY_LAST_TRIGGER) < COOLDOWN_TICKS) {
                return;                      // ⭐ 冷却中 ⇒ 本次不触发 ✓（也不刷新已有状态 ✓）
            }
            tool.getPersistentData().putInt(KEY_LAST_TRIGGER, now);
            applyEffect(target, level);
        }
    }

    private static void applyEffect(LivingEntity target, int level) {
        int duration = DURATION_BASE + (level - 1) * DURATION_PER_LEVEL;
        target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, duration, 1));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, duration, 10));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, duration, 1));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, duration, 2));
    }
}