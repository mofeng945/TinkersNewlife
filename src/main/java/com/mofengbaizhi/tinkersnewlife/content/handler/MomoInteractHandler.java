package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.MomoMerchant;
import com.mofengbaizhi.tinkersnewlife.content.menu.MomoMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * 墨默的**交互入口**（用户口径 §455 A/B ✓）。
 *
 * <ul>
 *   <li>右键墨默 ⇒ **拦掉原版行为**（它原本会直接开自己的交易 ✓）并打开我们的
 *       {@link MomoMenu}（对话 / 交易 / 雇佣 ✓ 三选项 + 回退 ✓）；
 *       打开时把 **entityId + 好感度** 一起塞进 `data` 同步给客户端 ✓；</li>
 *   <li>**攻击墨默 ⇒ 好感度 −1** ✓（`AttackEntityEvent` ✓ 只算玩家的主动攻击 ✓
 *       火焰/摔落之类的环境伤害不算 ✓ 与用户口径"攻击墨默"一致 ✓）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MomoInteractHandler {

    private MomoInteractHandler() {}

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        try {
            if (!(event.getTarget() instanceof MomoMerchant momo)) return;
            if (event.getLevel().isClientSide()) return;                     // 只在服务端开菜单 ✓
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            event.setCanceled(true);                                         // 拦掉原版直接交易 ✓
            if (!MomoFavor.canTalk(sp) && !MomoFavor.canHire(sp)) {
                // 好感度负数：仍然让他开菜单看得到（界面里会变灰 ✓）—— 只提示一句 ✓
                sp.displayClientMessage(Component.translatable("menu.tinkersnewlife.momo.cold"), true);
            }
            com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoMenuOpen.sendMenu(sp, momo.getId());} catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onAttackMomo(AttackEntityEvent event) {
        try {
            if (!(event.getTarget() instanceof MomoMerchant)) return;
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            MomoFavor.onHit(sp);                                             // 攻击墨默 −1 ✓
        } catch (Throwable ignored) {
        }
    }
}
