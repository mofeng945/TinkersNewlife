package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.content.ModEffects;
import com.mofengbaizhi.tinkersnewlife.content.modifier.GungnirModifier;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 冈格尼尔（远程特性）结算器：
 * <ul>
 *   <li>虚影弹射物（远程武器所发箭矢）：命中时无视重力（见发射路径，此处补伤害/效果）；</li>
 *   <li>命中给目标震撼（stun）0.9s；</li>
 *   <li>直接命中：伤害 ×1.2（更高）+ 目标脚下点燃狱火（长时着火）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GungnirHandler {

    private GungnirHandler() {
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Projectile projectile = event.getProjectile();
        if (!(projectile instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter)) return;
        ItemStack weapon = rangedWeapon(shooter);
        if (weapon.isEmpty() || !hasGungnir(weapon)) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        if (!(hit.getEntity() instanceof LivingEntity target)) return;

        // 取消原命中，按冈格尼尔虚影结算：伤害 = 弹射物的 80%（真伤源），命中给原版"震撼(STUNNED)"+"狱火(BURN_HEX)" + 脚下生成狱火投射物
        event.setCanceled(true);
        float dmg = (float) arrow.getBaseDamage() * 0.8f;
        target.invulnerableTime = 0;
        target.hurt(target.damageSources().magic(), dmg);
        applyOriginalEffects(target, target.level());
    }

    /** 原版效果：goety STUNNED（震撼）+ BURN_HEX（狱火药效）+ 目标脚下生成 Hellfire 投射物（反射/软依赖） */
    private static void applyOriginalEffects(LivingEntity target, net.minecraft.world.level.Level level) {
        Object stunned = goetyEffect("STUNNED");
        if (stunned instanceof net.minecraft.world.effect.MobEffect e) {
            target.addEffect(new MobEffectInstance(e, 40, 0, false, false, true));
        }
        Object burn = goetyEffect("BURN_HEX");
        if (burn instanceof net.minecraft.world.effect.MobEffect e) {
            target.addEffect(new MobEffectInstance(e, 160, 0, false, false, true));
        }
        // 脚下生成地狱火投射物（best-effort，失败不影响效果）
        try {
            Class<?> hellfire = Class.forName("com.Polarice3.Goety.common.entities.projectiles.Hellfire");
            net.minecraft.world.entity.Entity ent = (net.minecraft.world.entity.Entity) hellfire
                    .getConstructor(net.minecraft.world.level.Level.class, double.class, double.class, double.class,
                            LivingEntity.class)
                    .newInstance(level, target.getX(), target.getY(), target.getZ(), target);
            level.addFreshEntity(ent);
        } catch (Throwable ignored) {
        }
    }

    private static Object goetyEffect(String field) {
        try {
            java.lang.reflect.Field f = java.lang.Class.forName("com.Polarice3.Goety.common.effects.GoetyEffects")
                    .getField(field);
            Object ro = f.get(null);
            return ((net.minecraftforge.registries.RegistryObject<?>) ro).get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack rangedWeapon(LivingEntity shooter) {
        ItemStack main = shooter.getMainHandItem();
        if (hasGungnir(main)) return main;
        ItemStack off = shooter.getOffhandItem();
        if (hasGungnir(off)) return off;
        return ItemStack.EMPTY;
    }

    private static boolean hasGungnir(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(GungnirModifier.ID) > 0;
    }
}
