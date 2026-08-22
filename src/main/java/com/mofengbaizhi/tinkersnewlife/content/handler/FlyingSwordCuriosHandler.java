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
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlyingSwordCuriosHandler {

    private static int tickCounter = 0;
    private static final String FLYING_SWORD_ACTIVE = "flying_sword_active";

    private static boolean isFlyingSwordBroken(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ToolStack tool = ToolStack.from(stack);
        return tool == null || tool.isBroken();
    }

    @SubscribeEvent
    public static void onCurioEquip(CurioEquipEvent event) {
        ItemStack stack = event.getStack();
        if (!(stack.getItem() instanceof FlyingSwordItem)) return;
        if (!"feet".equals(event.getSlotContext().identifier())) return;

        // ★ 损坏时静默阻止装备
        if (isFlyingSwordBroken(stack)) {
            event.setCanceled(true);
            return;
        }

        Player player = (Player) event.getEntity();
        if (!player.level().isClientSide) {
            UUID emittingId = FlyingSwordItem.EMITTING_PLAYER.get();
            if (emittingId != null && emittingId.equals(player.getUUID())) {
                event.setCanceled(true);
                return;
            }

            player.getPersistentData().putBoolean(FLYING_SWORD_ACTIVE, true);
            player.getPersistentData().putBoolean("flying_sword_prev_mayfly", player.getAbilities().mayfly);

            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();

            spawnFootEntity(player, stack);
        }
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioUnequipEvent event) {
        ItemStack stack = event.getStack();
        if (!(stack.getItem() instanceof FlyingSwordItem)) return;
        if (!"feet".equals(event.getSlotContext().identifier())) return;

        Player player = (Player) event.getEntity();
        if (!player.level().isClientSide) {
            clearFlyingState(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.isCreative()) return;

        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return;
        var slotResult = curios.get().findFirstCurio(stack -> stack.getItem() instanceof FlyingSwordItem);

        boolean hasFlyingSword = slotResult.isPresent();
        boolean active = player.getPersistentData().getBoolean(FLYING_SWORD_ACTIVE);

        // 如果标记为 active，但实际没有飞剑或飞剑损坏 → 静默清除飞行状态
        if (active) {
            if (!hasFlyingSword) {
                clearFlyingState(player);
                return;
            }
            ItemStack stack = slotResult.get().stack();
            if (isFlyingSwordBroken(stack)) {
                clearFlyingState(player);
                return;
            }
        }

        // 有飞剑且未损坏，且玩家正在飞行 → 确保实体存在
        if (hasFlyingSword && slotResult.isPresent()) {
            ItemStack stack = slotResult.get().stack();
            if (!isFlyingSwordBroken(stack) && player.getAbilities().flying) {
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
            // ★ 损坏后不再消耗耐久
            if (isFlyingSwordBroken(stack)) {
                clearFlyingState(player);
                continue;
            }

            stack.hurt(1, player.getRandom(), player);
        }
    }

    // ===== 摔落伤害逻辑修正 =====
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // ★ 只在飞行状态下取消摔伤
        if (player.getAbilities().flying) {
            event.setCanceled(true);
            return;
        }

        // ★ 关闭飞行后，正常计算摔伤（由原版处理，我们不干预）
        // 让原版逻辑自行处理，这里不做任何操作
    }

    // ===== 辅助方法 =====

    private static void clearFlyingState(Player player) {
        player.getPersistentData().remove(FLYING_SWORD_ACTIVE);
        boolean prevMayfly = player.getPersistentData().getBoolean("flying_sword_prev_mayfly");
        player.getAbilities().mayfly = prevMayfly;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        removeFootEntity(player);
    }

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