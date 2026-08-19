package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CosmicHandler {

    private static final ModifierId COSMIC_ORDER_VOICE = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "cosmic_order_voice"));

    private static final int DURATION_BASE = 100;
    private static final int DURATION_PER_LEVEL = 50;

    // ==================== 辅助方法：获取弹射物对应的武器 ====================
    private static ItemStack getProjectileWeapon(Projectile projectile, Player shooter) {
        // 1. 尝试从弹射物本身获取物品（标枪、三叉戟、匠魂标枪等）
        try {
            Method method = projectile.getClass().getMethod("getPickupItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}
        try {
            Method method = projectile.getClass().getMethod("getItem");
            return (ItemStack) method.invoke(projectile);
        } catch (Exception ignored) {}

        // 2. 若弹射物无物品，则从玩家主手获取（弓/弩）
        ItemStack mainHand = shooter.getMainHandItem();
        if (!mainHand.isEmpty()) {
            return mainHand;
        }
        // 3. 若主手为空，尝试副手
        ItemStack offHand = shooter.getOffhandItem();
        if (!offHand.isEmpty()) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    // ==================== 统一处理近战和弹射物 ====================
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player)) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        Player player = (Player) event.getSource().getEntity();
        ItemStack weapon = ItemStack.EMPTY;

        // 判断是否由弹射物造成伤害
        if (event.getSource().getDirectEntity() instanceof Projectile projectile) {
            // 弹射物伤害：从弹射物获取发射武器
            weapon = getProjectileWeapon(projectile, player);
            if (weapon.isEmpty()) {
                // 若无法从弹射物获取，则从玩家主手获取（弓/弩）
                weapon = player.getMainHandItem();
            }
        } else {
            // 近战伤害：从玩家主手获取
            weapon = player.getMainHandItem();
        }

        if (weapon.isEmpty()) return;
        ToolStack tool = ToolStack.from(weapon);
        if (tool == null) return;
        if (tool.getStats().getContainedStats().isEmpty()) return;

        int level = tool.getModifierLevel(COSMIC_ORDER_VOICE);
        if (level > 0) {
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