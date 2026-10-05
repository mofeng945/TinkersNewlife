package com.mofengbaizhi.tinkersnewlife.mixin;

import mods.flammpfeil.slashblade.entity.EntityAbstractSummonedSword;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>§1020 拔刀剑 × 匠魂：范围/技能攻击也按匠魂数值结算</b>
 * （照 <b>TiCEX</b> 的 {@code AttackManagerMixin} 移植 ✓）。
 *
 * <p>拔刀剑有两套攻击入口 ✗：{@code AttackHelper}（普通那一击 ✓，见 {@link KatanaAttackHelperMixin}）
 * 与 {@code AttackManager#doAttackWith}（斩击特效/范围攻击/技能 ✓）。本 mixin 在后者开头插手 ✓：
 * 若攻击者主手是**匠魂可改造物品** ✓，就按匠魂的算法结算这一下并取消本体原本的结算 ✓
 * ⇒ 技能与范围攻击也能吃到匠魂面板与被我们接上来的修饰符 ✓。
 *
 * <p>与 TiCEX 版本的差异（有意为之 ✓）：TiCEX 还调了它自己的
 * {@code CriticalModifierHook}/{@code DamageSourceModifierHook} ✗ —— 那是它自家的扩展钩子 ✓，
 * 我们不需要 ✓，因此暴击走原版 {@code ForgeHooks.getCriticalHit} ✓、伤害源保持本体给的那个 ✓。
 *
 * <p>⚠ 拔刀剑不在场时本 mixin 静默不生效 ✓（`required:false` ＋ `defaultRequire:0` ✓）。
 */
@Mixin(value = AttackManager.class, remap = false)
public class KatanaAttackManagerMixin {

    /**
     * 拦下 {@code AttackManager#doAttackWith(DamageSource, float, Entity, boolean, boolean)} ✓。
     *
     * @param source  本体构造的伤害源 ✓
     * @param amount  本体算好的伤害 ✓
     * @param target  目标 ✓
     * @param forceHit 是否强制命中（本体会把目标的受击无敌帧清零 ✓）
     * @param resetHit 命中后是否重置无敌帧 ✓
     * @param ci      取消回调 ⇒ 取消本体原本的结算 ✓
     */
    @Inject(method = "doAttackWith", at = @At("HEAD"), cancellable = true, remap = false)
    private static void tnl$dealToolDamage(DamageSource source, float amount, Entity target, boolean forceHit,
                                           boolean resetHit, CallbackInfo ci) {
        // 召唤剑本身不当目标 ✓（与 TiCEX 一致 ✓）
        if (target instanceof EntityAbstractSummonedSword) {
            return;
        }
        if (!(source.getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        ItemStack stack = attacker.getMainHandItem();
        if (!(stack.getItem() instanceof IModifiable)) {
            return;
        }

        ToolStack tool = ToolStack.from(stack);
        ToolAttackContext context = ToolAttackContext.attacker(attacker)
                .hand(InteractionHand.MAIN_HAND)
                .target(target)
                .build();

        // 以"攻击力属性"为基准，串一遍匠魂的近战伤害修饰符 ✓（与 TiCEX 的算法一致 ✓）
        float baseAmplifier = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float amplifier = baseAmplifier;
        for (ModifierEntry entry : tool.getModifiers()) {
            amplifier = entry.getHook(ModifierHooks.MELEE_DAMAGE)
                    .getMeleeDamage(tool, entry, context, baseAmplifier, amplifier);
        }
        if (amplifier <= 0.0F) {
            // 匠魂把它削到 0 ⇒ 这一下不打 ✓（本体原本的结算同样取消 ✓）
            ci.cancel();
            return;
        }

        float realAmount = amount / baseAmplifier * amplifier;
        if (context.getPlayerAttacker() != null) {
            CriticalHitEvent critical = ForgeHooks.getCriticalHit(context.getPlayerAttacker(), target, false, 1.0F);
            if (critical != null) {
                realAmount += realAmount * (critical.getDamageModifier() - 1.0F);
            }
        }

        if (forceHit) {
            target.invulnerableTime = 0;
        }
        for (ModifierEntry entry : tool.getModifiers()) {
            entry.getHook(ModifierHooks.MELEE_HIT).beforeMeleeHit(tool, entry, context, realAmount, 0.0F, 0.0F);
        }

        boolean succeed = target.hurt(source, realAmount);

        if (resetHit) {
            target.invulnerableTime = 0;
        }
        for (ModifierEntry entry : tool.getModifiers()) {
            if (succeed) {
                entry.getHook(ModifierHooks.MELEE_HIT).afterMeleeHit(tool, entry, context, baseAmplifier);
            } else {
                entry.getHook(ModifierHooks.MELEE_HIT).failedMeleeHit(tool, entry, context, baseAmplifier);
            }
        }
        ci.cancel();
    }
}
