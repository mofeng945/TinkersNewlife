package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.WarScytheItem;
import com.mofengbaizhi.tinkersnewlife.content.modifier.util.InvulnerabilityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FeverHandler {

    private static final int FEVER_PER_ATTACK = 10;
    private static final int FEVER_COST_DASH = 20;
    private static final int DASH_DISTANCE = 5;
    private static final int INVULNERABLE_TICKS = 10;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        // 大招期间不积攒 Fever（按玩家隔离）
        if (WarScytheItem.isPerformingUltimate(player.getUUID())) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity)) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof WarScytheItem)) return;

        int currentFever = WarScytheItem.getFever(stack);
        currentFever = Math.min(100, currentFever + FEVER_PER_ATTACK);
        WarScytheItem.setFever(stack, currentFever);

        if (player.isShiftKeyDown() && currentFever >= FEVER_COST_DASH) {
            WarScytheItem.setFever(stack, currentFever - FEVER_COST_DASH);
            Vec3 lookVec = player.getLookAngle();
            Vec3 moveDir = new Vec3(lookVec.x, 0, lookVec.z).normalize();
            if (moveDir.lengthSqr() < 0.01) {
                moveDir = new Vec3(0, 0, 1);
            }
            movePlayer(player, moveDir.scale(DASH_DISTANCE));
        }
    }

    private static void movePlayer(Player player, Vec3 delta) {
        double targetX = player.getX() + delta.x;
        double targetY = player.getY();
        double targetZ = player.getZ() + delta.z;

        Vec3 safePos = getSafeTeleportPosition(player, targetX, targetY, targetZ);
        if (safePos != null) {
            player.teleportTo(safePos.x, safePos.y, safePos.z);
            player.fallDistance = 0;
            InvulnerabilityManager.applyInvulnerability(player, INVULNERABLE_TICKS);
        } else {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§c无法移动！"),
                true
            );
        }
    }

    private static Vec3 getSafeTeleportPosition(Player player, double targetX, double targetY, double targetZ) {
        double step = 0.5;
        double currentX = player.getX();
        double currentY = player.getY();
        double currentZ = player.getZ();

        double dx = targetX - currentX;
        double dz = targetZ - currentZ;
        double dist = Math.sqrt(dx*dx + dz*dz);
        if (dist < 0.01) {
            return new Vec3(currentX, currentY, currentZ);
        }

        double dirX = dx / dist;
        double dirZ = dz / dist;

        double testX = currentX;
        double testZ = currentZ;
        double lastSafeX = currentX;
        double lastSafeZ = currentZ;
        boolean firstStep = true;

        for (double d = 0; d <= dist; d += step) {
            testX = currentX + dirX * d;
            testZ = currentZ + dirZ * d;
            if (isSafeBlock(player, testX, currentY, testZ)) {
                lastSafeX = testX;
                lastSafeZ = testZ;
                firstStep = false;
            } else {
                break;
            }
        }

        if (firstStep) {
            return null;
        }

        return new Vec3(lastSafeX, currentY, lastSafeZ);
    }

    private static boolean isSafeBlock(Player player, double x, double y, double z) {
        var level = player.level();
        BlockPos footPos = new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        BlockPos headPos = footPos.above();
        return !level.getBlockState(footPos).isSolid() && !level.getBlockState(headPos).isSolid();
    }
}