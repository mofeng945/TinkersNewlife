package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeDamageModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

/**
 * 词条·<b>海王之力</b>（§960 规格 · 无等级 ✓ · 水产养殖2 联动材料「海王金属」自带 ✓）。
 *
 * <p>四条效果（用户口径，逐条抄 ✓）：
 * <ul>
 *   <li><b>工具</b>：水中或雨中 ⇒ 无视挖掘速度惩罚 ✓ 攻速 +50% ✓ 伤害 +60% ✓
 *     （<b>本轮只落了"伤害 +60%"</b> ✓ 攻速与挖掘惩罚待下一批 ✗）；</li>
 *   <li><b>盔甲</b>：水下呼吸 ✓ 水中视野清晰 ✓ 水中或雨中：速度 +20% ✓ 减伤 +30% ✓（待做 ✗）；</li>
 *   <li><b>钓鱼竿</b>：自动 海之眷顾 II ＋ 饵钓 I ✓（待做 ✗ —— 匠魂工具不能正常附魔，要在钓鱼结算处模拟 ✓）；</li>
 *   <li><b>远程</b>：弹射物在水中无视阻力衰减 ✓（待做 ✗ 最重，需要 mixin）。</li>
 * </ul>
 *
 * <p>本类照 {@link SoldiersSaberModifier} 的先例：注册标记 ＋ 少量钩子 ✓ 复杂逻辑放 handler ✓。
 */
public class SeaKingsPowerModifier extends BaseCombatModifier implements MeleeDamageModifierHook, ProjectileLaunchModifierHook {

    /** 水中/雨中时的近战伤害倍率加成（用户口径 +60% ✓） */
    private static final float WATER_DAMAGE_BONUS = 0.6F;
    /** 攻速加成（用户口径 +50% ✓）—— 待实现 ✗ 先留常量免得以后忘记口径 ✓ */
    @SuppressWarnings("unused")
    private static final float WATER_ATTACK_SPEED_BONUS = 0.5F;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.MELEE_DAMAGE);
        hookBuilder.addHook(this, ModifierHooks.PROJECTILE_LAUNCH);
    }

    /** 水中或雨中 ⇒ 近战伤害 +60% ✓（判据用原版 isInWaterOrRain ✓） */
    @Override
    public float getMeleeDamage(IToolStackView tool, ModifierEntry modifier,
                                ToolAttackContext context, float baseDamage, float damage) {
        net.minecraft.world.entity.LivingEntity attacker =
                context == null ? null : context.getAttacker();
        if (attacker == null || !attacker.isInWaterOrRain()) return damage;
        return damage * (1.0F + WATER_DAMAGE_BONUS);
    }

    /**
     * 远程：有海王之力的武器射出的弹射物 ⇒ <b>打上标记</b> ✓
     * （随后由 {@code AbstractArrowSeaKingsMixin} 让它在水中不减速 ✓）。
     *
     * <p>⚠ 参数表以**编译器报错**为准 ✓（7 参数重载才是抽象的那个 ✓ —— 我原先照反编译猜的 8 参数版本 ✗
     * 被 javac 直接指出 "does not override abstract method onProjectileLaunch(IToolStackView,ModifierEntry,
     * LivingEntity,Projectile,AbstractArrow,ModDataNBT,boolean)" ✓）。
     */
    @Override
    public void onProjectileLaunch(IToolStackView tool, ModifierEntry modifier, LivingEntity shooter,
                                   Projectile projectile, AbstractArrow arrow, ModDataNBT persistentData,
                                   boolean primary) {
        if (arrow != null) arrow.getPersistentData().putBoolean("tnl_sea_kings", true);
    }
}
