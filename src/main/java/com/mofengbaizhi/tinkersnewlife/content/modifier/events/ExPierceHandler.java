package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.ExPierceModifier;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import com.mofengbaizhi.tinkersnewlife.util.TruePierce;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 穿透EX（{@code ex_pierce}）结算器：<b>只负责"什么时候该穿透"</b>——
 * 玩家近战、或任意射手（含仆从）的投射物命中，且所用工具带该修饰符。
 *
 * <p>"怎么穿"全部交给 {@link TruePierce} 的**万能穿透**引擎：真伤源（Goety 那支或本模组
 * {@code true_pierce}）、分块连打、无主源回退、差额直补、事件层顶开、与命灯指轮共存 ——
 * 与天逆鉾、墨默的挥击走的是**同一套逻辑**。
 *
 * <p>为什么必须"重打"而不是在 {@code LivingHurtEvent} 里改数值：灾变 Boss 的无敌/限伤判断
 * （利维坦离水无敌、阶段二、伤害桶）与凋灵出生无敌都在 {@code hurt()} 最前面的
 * {@code #minecraft:bypasses_invulnerability} 标签判断里，事件层根本到不了。
 * 多段 Boss（如灾变利维坦）的受击实体是 PartEntity → 先解析回主实体再打（见 {@link #resolveTarget}）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ExPierceHandler {

    private ExPierceHandler() {
    }

    /** 玩家近战：主手工具带 ex_pierce → 取消原伤害，交给万能穿透引擎 */
    @SubscribeEvent
    public static void onPlayerAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        LivingEntity target = resolveTarget(event.getTarget());
        if (target == null) return;
        if (!hasExPierce(player.getMainHandItem())) return;
        event.setCanceled(true);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (damage <= 0) return;
        TruePierce.apply(player, target, damage);
    }

    /** 投射物命中：射手（玩家/仆从等）的远程工具带 ex_pierce → 取消原命中，交给万能穿透引擎 */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Entity projectile = event.getEntity();
        if (projectile.level().isClientSide) return;
        if (!(projectile instanceof AbstractArrow arrow)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        LivingEntity target = resolveTarget(hit.getEntity());
        if (target == null) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        ItemStack weapon;
        if (shooter instanceof Player playerShooter) {
            weapon = ProjectileWeaponHelper.getProjectileWeapon(arrow, playerShooter);
        } else {
            // 仆从等非玩家射手：弓在主手（goety 仆从 AI 用主手匠魂弓）
            weapon = shooter.getMainHandItem();
        }
        if (weapon.isEmpty() || !hasExPierce(weapon)) return;
        event.setCanceled(true);
        float damage = (float) arrow.getBaseDamage();
        if (damage <= 0) damage = 1.0f;
        TruePierce.apply(shooter, target, damage);
        // 取消后原命中不结算；直接移除箭避免继续飞行二次命中
        projectile.discard();
    }

    /**
     * 解析实际受伤目标：多段 Boss（如灾变利维坦）的受击实体是 PartEntity（非 LivingEntity），
     * 其 hurt 仅转发到主实体（attackEntityFromPart → 主实体 hurt）。
     * 这里把 part 解析回主实体，再以真伤源重打主实体，才能让主实体 hurt() 内的无敌/限伤判断吃到 bypass 标签。
     */
    private static LivingEntity resolveTarget(Entity hit) {
        if (hit instanceof LivingEntity living) return living;
        if (hit instanceof net.minecraftforge.entity.PartEntity<?> part
                && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }

    private static boolean hasExPierce(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return ToolHelper.getActiveModifierLevel(tool, ExPierceModifier.ID) > 0;
    }
}
