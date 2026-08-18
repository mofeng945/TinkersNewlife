package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordFootEntity;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlyingSwordCuriosHandler {

    private static int tickCounter = 0;
    private static final String FLYING_SWORD_ACTIVE = "flying_sword_active";

    @SubscribeEvent
    public static void onCurioEquip(CurioEquipEvent event) {
        ItemStack stack = event.getStack();
        if (stack.getItem() instanceof FlyingSwordItem && "feet".equals(event.getSlotContext().identifier())) {
            Player player = (Player) event.getEntity();
            if (!player.level().isClientSide) {
                UUID emittingId = FlyingSwordItem.EMITTING_PLAYER.get();
                if (emittingId != null && emittingId.equals(player.getUUID())) {
                    event.setCanceled(true);
                    TinkersNewlife.LOGGER.info("【飞剑】阻止了右键发射触发的自动装备");
                    return;
                }

                // ✅ 标记：飞剑开启了飞行能力
                player.getPersistentData().putBoolean(FLYING_SWORD_ACTIVE, true);
                // 保存原始状态，用于卸下时恢复
                player.getPersistentData().putBoolean("flying_sword_prev_mayfly", player.getAbilities().mayfly);

                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();

                spawnFootEntity(player, stack);
            }
        }
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioUnequipEvent event) {
        ItemStack stack = event.getStack();
        if (stack.getItem() instanceof FlyingSwordItem && "feet".equals(event.getSlotContext().identifier())) {
            Player player = (Player) event.getEntity();
            if (!player.level().isClientSide) {
                // ✅ 清除标记
                player.getPersistentData().remove(FLYING_SWORD_ACTIVE);

                boolean prevMayfly = player.getPersistentData().getBoolean("flying_sword_prev_mayfly");
                player.getAbilities().mayfly = prevMayfly;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();

                removeFootEntity(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        // ✅ 创造模式不干预
        if (player.isCreative()) return;

        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return;
        var slotResult = curios.get().findFirstCurio(stack -> stack.getItem() instanceof FlyingSwordItem);

        boolean hasFlyingSword = slotResult.isPresent();
        boolean active = player.getPersistentData().getBoolean(FLYING_SWORD_ACTIVE);

        // ✅ 关键：只在标记为 active 但实际没有飞剑时才重置
        if (!hasFlyingSword && active) {
            TinkersNewlife.LOGGER.warn("【飞剑】玩家 {} 标记为 active 但无飞剑，重置飞行能力", player.getName().getString());
            player.getPersistentData().remove(FLYING_SWORD_ACTIVE);
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            removeFootEntity(player);
            return;
        }

        // 有装备飞剑，且玩家在飞行，确保实体存在
        if (hasFlyingSword && slotResult.isPresent()) {
            ItemStack stack = slotResult.get().stack();
            if (player.getAbilities().flying) {
                if (!hasFootEntity(player)) {
                    spawnFootEntity(player, stack);
                }
            } else {
                if (hasFootEntity(player)) {
                    removeFootEntity(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % 100 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive() || !player.getAbilities().flying) continue;

            var curios = CuriosApi.getCuriosInventory(player).resolve();
            if (curios.isEmpty()) continue;
            var slotResult = curios.get().findFirstCurio(stack -> stack.getItem() instanceof FlyingSwordItem);
            if (slotResult.isEmpty()) continue;

            ItemStack stack = slotResult.get().stack();
            stack.hurt(1, player.getRandom(), player);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return;
        var slotResult = curios.get().findFirstCurio(stack -> stack.getItem() instanceof FlyingSwordItem);
        if (slotResult.isEmpty()) return;

        if (player.getAbilities().flying) return;

        event.setCanceled(true);
        float damage = (event.getDistance() - 3.0f) * 1.0f;
        if (damage > 0) {
            player.hurt(player.damageSources().fall(), damage);
        }
    }

    // ===== 辅助方法 =====

    private static boolean hasFootEntity(Player player) {
        List<FlyingSwordFootEntity> entities = player.level().getEntitiesOfClass(
                FlyingSwordFootEntity.class,
                player.getBoundingBox().inflate(3),
                e -> e.getOwnerUUID() != null && e.getOwnerUUID().equals(player.getUUID())
        );
        return !entities.isEmpty();
    }

    private static void spawnFootEntity(Player player, ItemStack stack) {
        if (player.level().isClientSide) return;
        FlyingSwordFootEntity footEntity = new FlyingSwordFootEntity(player.level(), player, stack);
        player.level().addFreshEntity(footEntity);
    }

    private static void removeFootEntity(Player player) {
        if (player.level().isClientSide) return;
        player.level().getEntitiesOfClass(FlyingSwordFootEntity.class, player.getBoundingBox().inflate(3))
                .stream()
                .filter(e -> e.getOwnerUUID() != null && e.getOwnerUUID().equals(player.getUUID()))
                .findFirst()
                .ifPresent(Entity::discard);
    }
}