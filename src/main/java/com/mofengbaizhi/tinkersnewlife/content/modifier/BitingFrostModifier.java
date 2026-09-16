package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TooltipFlag;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeHitModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.display.TooltipModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 特性·<b>寒霜刺骨</b>（材料「冰封骨头」自带，<b>有等级</b>）：
 * 命中后使敌人获得本模组的<b>霜冻</b>效果（{@code tinkersnewlife:frost}），
 * 持续 <b>3 × 等级 秒</b>，冷却 <b>6 秒</b>。
 *
 * <p>霜冻效果的行为（见 {@code content.effect.FrostEffect}）：持续期间每 tick 累加目标的
 * "冻结刻度"（目标会真的被冻住），效果结束时再补 100 tick 寒霜 ✓ ——
 * 所以"挂上霜冻"就等于"把敌人冻住一段时间" ✓。
 *
 * <p>挂钩用匠魂自己的 {@link MeleeHitModifierHook#afterMeleeHit}（命中之后）✓ ——
 * 比用 Forge 事件更准：它天然只对"这次攻击用的那把工具"生效 ✓，
 * 且自动覆盖近战与投掷类工具 ✓。
 *
 * <p>冷却按<b>攻击者</b>记（不是按目标）✓：同一个人 6 秒内只能触发一次，
 * 防止一刀一片全冻上 ✗。
 */
public class BitingFrostModifier extends Modifier implements MeleeHitModifierHook, TooltipModifierHook {

    public static final ModifierId ID =
            new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "biting_frost"));

    /** 最高 3 级（匠魂没有 getMaxLevel 覆写点，在查询处夹住） */
    private static final int MAX_LEVEL = 3;

    /** 每级持续时间（秒） */
    public static final int SECONDS_PER_LEVEL = 3;

    /** 触发冷却（tick）= 6s */
    public static final int COOLDOWN = 6 * 20;

    /** 冷却：攻击者 UUID → 下次可用 gameTime */
    private static final Map<UUID, Long> COOLDOWN_MAP = new ConcurrentHashMap<>();

    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.MELEE_HIT, ModifierHooks.TOOLTIP);
    }

    @Override
    public void afterMeleeHit(IToolStackView tool, ModifierEntry modifier, ToolAttackContext context, float damage) {
        LivingEntity attacker = context.getAttacker();
        LivingEntity target = context.getLivingTarget();
        if (attacker == null || target == null || target == attacker) return;
        if (attacker.level().isClientSide) return;

        int level = clampLevel(modifier.getLevel());
        if (level <= 0) return;

        long now = attacker.level().getGameTime();
        Long next = COOLDOWN_MAP.get(attacker.getUUID());
        if (next != null && now < next) return;
        COOLDOWN_MAP.put(attacker.getUUID(), now + COOLDOWN);

        // 霜冻：持续 3 × 等级 秒（amplifier 固定 0，等级只影响时长 ✓）
        if (ModEffects.FROST.get() != null) {
            target.addEffect(new MobEffectInstance(ModEffects.FROST.get(),
                    SECONDS_PER_LEVEL * level * 20, 0, false, true));
        }
    }

    @Override
    public void addTooltip(IToolStackView tool, ModifierEntry modifier,
                           @Nullable Player player, List<Component> tooltip,
                           TooltipKey tooltipKey, TooltipFlag tooltipFlag) {
        tooltip.add(Component.translatable("modifier.tinkersnewlife.biting_frost.tip",
                String.valueOf(SECONDS_PER_LEVEL * clampLevel(modifier.getLevel())),
                String.valueOf(COOLDOWN / 20)));
    }
}
