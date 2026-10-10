package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.BitingFrostModifier;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特性「<b>寒霜刺骨</b>」结算器（见 {@link BitingFrostModifier}）：
 * 命中后给目标挂本模组的<b>霜冻</b>效果，持续 <b>3 × 等级 秒</b>，冷却 <b>6 秒</b> ✓。
 *
 * <p>为什么走 Forge 事件而不是匠魂的 {@code MELEE_HIT} 钩子：见 {@link BitingFrostModifier} 的类注释 ——
 * <b>普通挥砍时匠魂不会调用那个钩子</b> ✗（实测完全没效果），
 * 而本模组所有命中类特性都走 {@code LivingHurtEvent} + {@link ToolHelper#getCombatToolWith} ✓。
 *
 * <p>冷却按<b>攻击者</b>记（不是按目标）✓：同一个人 6 秒内只能触发一次，避免一刀冻住一片 ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BitingFrostHandler {

    private BitingFrostHandler() {
    }

    /** 冷却：攻击者 UUID → 下次可用 gameTime */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    /** 诊断日志开关（平时 false 不刷屏 ✓） */
    private static final boolean DEBUG = false;
    private static volatile long lastLogTime = 0L;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (event.getAmount() <= 0.0F) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == target) return;

        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), attacker, BitingFrostModifier.ID);
        if (tool == null || ToolHelper.getActiveModifierLevel(tool, BitingFrostModifier.ID) <= 0) return;

        int level = BitingFrostModifier.clampLevel(ToolHelper.getActiveModifierLevel(tool, BitingFrostModifier.ID));
        if (level <= 0) return;

        long now = attacker.level().getGameTime();
        Long next = COOLDOWN.get(attacker.getUUID());
        if (next != null && now < next) return;
        COOLDOWN.put(attacker.getUUID(), now + BitingFrostModifier.COOLDOWN);

        int ticks = BitingFrostModifier.SECONDS_PER_LEVEL * level * 20;
        if (ModEffects.FROST.get() != null) {
            target.addEffect(new MobEffectInstance(ModEffects.FROST.get(), ticks, 0, false, true));
            long ms = System.currentTimeMillis();
            if (ms - lastLogTime > 2000L) {
                lastLogTime = ms;
                if (DEBUG) TinkersNewlife.LOGGER.info("[寒霜刺骨] {} 命中 {} → 霜冻 {} 秒（等级 {}）",
                        attacker.getName().getString(), target.getName().getString(), ticks / 20, level);
            }
        } else {
            TinkersNewlife.LOGGER.warn("[寒霜刺骨] 霜冻效果未注册（ModEffects.FROST 为空）→ 无法施加");
        }
    }
}
