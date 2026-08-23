package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.DurandalSwordItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class SilentGloveInteractionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SilentGloveInteractionHandler.class);

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DarkSilentManager.isActive(player.getUUID())) return;

        ItemStack mainHand = player.getMainHandItem();

        if (mainHand.isEmpty()) {
            // 尝试从手套中取出武器
            if (equipRandomTool(player)) {
                // 成功取出武器，取消原版攻击事件，执行自定义攻击
                event.setCanceled(true);

                ItemStack weapon = player.getMainHandItem();
                if (weapon.isEmpty()) {
                    LOGGER.warn("[噤默手套] 掏武器失败，主手仍为空");
                    return;
                }

                float damage = DarkSilentManager.getWeaponDamage(player, weapon);
                event.getTarget().hurt(player.damageSources().playerAttack(player), damage);
                player.swing(InteractionHand.MAIN_HAND);
                player.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);

                if (weapon.isDamageableItem()) {
                    weapon.hurtAndBreak(1, player, (p) -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
                }

                if (weapon.getItem() instanceof DurandalSwordItem) {
                    DarkSilentManager.countHit(player, weapon);
                }
            }
            // ✅ 如果无法取出武器，不取消事件，允许原版空手攻击
            return;
        }

        // 如果手中持有武器且是杜兰达尔剑，记录攻击计数
        if (mainHand.getItem() instanceof DurandalSwordItem) {
            DarkSilentManager.countHit(player, mainHand);
        }
    }

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DarkSilentManager.isActive(player.getUUID())) return;
        if (!player.getOffhandItem().isEmpty()) return;
        if (!player.getMainHandItem().isEmpty()) return;

        ItemStack tool = GloveWeaponStorage.extractRandomTool(player);
        if (tool.isEmpty()) return;

        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        event.setCanceled(true);

        Level level = player.level();
        InteractionHand hand = event.getHand();
        InteractionResultHolder<ItemStack> result = tool.use(level, player, hand);
        if (result.getResult().consumesAction()) {
            player.setItemInHand(hand, result.getObject());
        }

        GloveWeaponStorage.scheduleReturn(player);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (DarkSilentManager.isActive(player.getUUID())) return;
        if (!player.getMainHandItem().isEmpty()) return;

        ItemStack tool = GloveWeaponStorage.extractRandomTool(player);
        if (tool.isEmpty()) return;

        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        event.setCanceled(true);

        player.interactOn(event.getTarget(), event.getHand());
        GloveWeaponStorage.scheduleReturn(player);
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack picked = event.getItem().getItem().copy();
        if (picked.isEmpty()) return;

        if (GloveWeaponStorage.tryStoreInVault(player, picked)) {
            event.setCanceled(true);
            event.getItem().discard();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        UUID uuid = event.getEntity().getUUID();
        DarkSilentManager.removePlayer(uuid);
        GloveWeaponStorage.removePlayerData(uuid);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUUID();
        GloveWeaponStorage.forceReturnPending(player);
        GloveWeaponStorage.removePlayerData(uuid);
        DarkSilentManager.removePlayer(uuid);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        GloveWeaponStorage.incrementTickCounter();
        if (GloveWeaponStorage.getTickCounter() % GloveWeaponStorage.getScanIntervalTicks() != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GloveWeaponStorage.tickScan(player);
        }
    }

    private static boolean equipRandomTool(Player player) {
        if (player.level().isClientSide) return false;
        if (!player.getMainHandItem().isEmpty()) return false;

        ItemStack tool = GloveWeaponStorage.extractRandomTool(player);
        if (tool.isEmpty()) return false;

        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        GloveWeaponStorage.scheduleReturn(player);
        return true;
    }
}