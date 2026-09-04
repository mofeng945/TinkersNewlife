package com.mofengbaizhi.tinkersnewlife.content.curse.technique;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlameArrowEntity;
import com.mofengbaizhi.tinkersnewlife.content.item.FlameArrowItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式「灶·开」：按住蓄力（主手手持火焰箭，动态火焰纹理），松开发射
 * <p>
 * - 按下按键：主手临时装备火焰箭（手持蓄力，无拉弓动画）
 * - 松开按键：恢复原主手物品、扣除咒力，向当前朝向发射火焰箭
 *   （笔直轨迹、速度较慢），命中引发火焰爆炸（不破坏方块）：
 *   中心伤害 = (1 + 咒力亲和/100) × (当前攻击伤害 + 咒力输出×10) × 200%，随距离衰减；
 *   并点燃 (1 + 咒力输出) 半径的区域
 * - 咒力消耗为「解」的 10 倍（松开发射时扣除）
 */
public final class ZaoKaiTechnique extends BaseTechnique {

    public static final ZaoKaiTechnique INSTANCE = new ZaoKaiTechnique();

    /** 火焰箭飞行速度（较慢，笔直） */
    private static final float ARROW_SPEED = 1.2F;

    /** 蓄力中：玩家 UUID → 原主手物品（蓄力期间临时换成火焰箭） */
    private static final Map<UUID, ItemStack> CHARGING = new ConcurrentHashMap<>();

    private ZaoKaiTechnique() {
        super(Modifiers.ZAO_KAI.getId());
    }

    /** 咒力消耗为「解」的 10 倍 */
    @Override
    protected int getCost(ServerPlayer player) {
        return super.getCost(player) * 10;
    }

    /** 按下：主手临时装备火焰箭（手持蓄力） */
    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CHARGING.containsKey(player.getUUID())) return; // 已在蓄力
        CHARGING.put(player.getUUID(), player.getMainHandItem());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FLAME_ARROW_ITEM.get()));
    }

    /** 松开：恢复原主手物品、扣除咒力，向当前朝向发射火焰箭（不足则取消） */
    @Override
    public void onKeyRelease(ServerPlayer player) {
        ItemStack original = CHARGING.remove(player.getUUID());
        if (original == null) return;
        // 仅当主手仍是火焰箭时才恢复（防止蓄力中玩家自行换走物品）
        if (player.getMainHandItem().getItem() instanceof FlameArrowItem) {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
        // 松开发射时扣除（解 ×10），不足则取消
        if (!payCost(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        // 向松开瞬间的朝向发射：笔直、慢速
        FlameArrowEntity arrow = new FlameArrowEntity(player.level(), player);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, ARROW_SPEED, 0.0F);
        player.level().addFreshEntity(arrow);
    }

    /** 取消蓄力（登出/死亡时）：恢复原主手物品 */
    public static void cancelCharge(ServerPlayer player) {
        ItemStack original = CHARGING.remove(player.getUUID());
        if (original == null) return;
        if (player.getMainHandItem().getItem() instanceof FlameArrowItem) {
            player.setItemInHand(InteractionHand.MAIN_HAND, original);
        }
    }
}
