package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.RapierItem;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public final class RapierCombatHandler {
    private RapierCombatHandler() {}

    /**
     * <b>§950 阶段 4：手持西洋剑时，副手盾牌等"无法使用"</b> ✓（匠魂2 原版特征之一 ✓）。
     *
     * <p>为什么还需要这一手：我们右键**后跳**已经吃掉了右键 ✓（`RapierItem#use` 返回 success ✓）
     * ⇒ 绝大多数情况下副手盾牌根本起不来 ✓ —— **但潜行时我们故意放行** ✗（潜行右键留给放置/交互 ✓）
     * ⇒ 那条缝里盾牌还是能举起来 ✗ ⇒ 用这个事件把缝堵上 ✓。
     *
     * <p>口径选择（两档）：**这里选"完全不格挡"** ✓（{@code setCanceled(true)} ✓）
     * —— 匠魂2 的原文是"**无法使用**" ✓ 不是"能举但挡不住" ✗；
     * 想温和一点就改成只把 {@code setBlockedDamage(0)} ✓（保动画、不挡伤害 ✓）一行之差 ✓。
     *
     * <p>判定看的是**主手** ✓（盾牌在副手 ✓ 与哪只手举盾无关 ✓）：主手是 {@link RapierItem} ⇒ 取消格挡 ✓。
     */
    @SubscribeEvent
    public static void onShieldBlock(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player.getMainHandItem().getItem() instanceof RapierItem)) return;
        event.setCanceled(true);              // 完全不格挡 ✓（盾牌"用不了" ✓）
        event.setShieldTakesDamage(false);    // 保险：别白扣盾的耐久 ✓
    }
}
