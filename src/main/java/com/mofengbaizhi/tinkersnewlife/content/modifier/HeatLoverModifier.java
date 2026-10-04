package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.content.handler.HeatLoverHandler;
import com.mofengbaizhi.tinkersnewlife.content.modifier.base.BaseCombatModifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeDamageModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.ranged.ProjectileLaunchModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ModDataNBT;

/**
 * 词条·<b>喜热</b>（§980 · 无等级 · 熔岩钓鱼联动材料「钷」自带）。
 *
 * <p>口径：**盔甲面 1:1 照原模组**（用户 2026-10-04 选定 ✓），工具/远程面由我按同一主题补（原模组没有钷工具 ✗）：
 * <ul>
 *   <li><b>盔甲</b>：每件 25% 火焰伤害减免 ✓（原模组 {@code ItemPromethiumArmor} 就是 {@code 0.25f}/件 ✓）
 *     ＋「金睛」熔岩下视野清晰 ✓（客户端雾）＋「焚身」炎热时缓慢回血 ✓ ＋「幽步」炎热时移动加速 ✓
 *     ＋「蒸汽」可在岩浆上行走 ✓（挂原模组自己的 {@code lavafishing:lava_walker} 效果 ✓ 真正 1:1）；</li>
 *   <li><b>工具</b>（我方设计）：着火或熔岩中 ⇒ 近战伤害 +60% ✓（与「海王之力」水中 +60% 对称 ✓）
 *     ＋ 挖掘速度 +20%（见 handler 的 {@code BreakSpeed}）；</li>
 *   <li><b>远程</b>（我方设计）：射出的弹射物**点燃**目标 ✓（原模组的钷弹丸是"分裂爆炸"，
 *     那是它自己的弹射物实体，匠魂的弓箭/投掷物走不到那条路 ⇒ 换成同主题的点燃 ✓）。</li>
 * </ul>
 *
 * <p>本类照 {@code SeaKingsPowerModifier} 的先例：注册标记 ＋ 少量钩子 ✓ 复杂逻辑放 handler ✓。
 */
public class HeatLoverModifier extends BaseCombatModifier implements MeleeDamageModifierHook, ProjectileLaunchModifierHook {

    /** 着火/熔岩中的近战伤害倍率加成（与我方「海王之力」的水中 +60% 对称 ✓） */
    private static final float HEAT_DAMAGE_BONUS = 0.6F;
    /** 远程：点燃秒数（我方设计 ✓） */
    private static final int PROJECTILE_FIRE_SECONDS = 10;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.MELEE_DAMAGE);
        hookBuilder.addHook(this, ModifierHooks.PROJECTILE_LAUNCH);
    }

    /** 着火或熔岩中 ⇒ 近战伤害 +60%（判据见 {@link HeatLoverHandler#isHot}） */
    @Override
    public float getMeleeDamage(IToolStackView tool, ModifierEntry modifier,
                                ToolAttackContext context, float baseDamage, float damage) {
        LivingEntity attacker = context == null ? null : context.getAttacker();
        if (attacker == null || !HeatLoverHandler.isHot(attacker)) return damage;
        return damage * (1.0F + HEAT_DAMAGE_BONUS);
    }

    /** 远程：射出的弹射物点燃目标（参数表以编译器为准 ✓ 7 参数重载才是抽象的那个 ✓） */
    @Override
    public void onProjectileLaunch(IToolStackView tool, ModifierEntry modifier, LivingEntity shooter,
                                   Projectile projectile, AbstractArrow arrow, ModDataNBT persistentData,
                                   boolean primary) {
        if (arrow != null) {
            arrow.setSecondsOnFire(PROJECTILE_FIRE_SECONDS);
        }
    }
}
