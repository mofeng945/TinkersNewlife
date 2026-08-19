package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ChildOfTheStarsHandler {

    private static final ModifierId CHILD_OF_THE_STARS = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "child_of_the_stars")
    );

    // ==================== 辅助方法：获取弹射物对应的武器 ====================
    private static ItemStack getProjectileWeapon(Projectile projectile) {
        // 1. 尝试从弹射物本身获取物品（标枪、三叉戟、匠魂标枪等）
        try {
            Method method = projectile.getClass().getMethod("getPickupItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}
        try {
            Method method = projectile.getClass().getMethod("getItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}
        return ItemStack.EMPTY;
    }

    // ==================== 伤害翻倍（支持近战和弹射物） ====================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 检查伤害来源是否为玩家
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        ItemStack weapon = ItemStack.EMPTY;

        // 判断是否由弹射物造成伤害
        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            // 弹射物伤害：从弹射物获取发射武器
            weapon = getProjectileWeapon(projectile);
            // 若无法从弹射物获取，则从玩家主手获取（弓/弩）
            if (weapon.isEmpty()) {
                weapon = player.getMainHandItem();
            }
        } else {
            // 近战伤害：从玩家主手获取
            weapon = player.getMainHandItem();
        }

        if (weapon.isEmpty()) return;
        ToolStack tool = ToolStack.from(weapon);
        if (tool == null) return;

        int level = tool.getModifierLevel(CHILD_OF_THE_STARS);
        if (level <= 0) return;

        // 伤害翻倍：每级 ×2
        float originalDamage = event.getAmount();
        float multipliedDamage = (float) (originalDamage * Math.pow(2, level));
        event.setAmount(multipliedDamage);
    }

    // ==================== 弹射物事件（不再需要，保留但空） ====================
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        // 所有逻辑已移至 LivingHurtEvent
    }
}