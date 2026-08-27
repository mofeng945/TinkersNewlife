package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordFootEntity;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
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
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool == null || tool.isBroken();
    }

    @SubscribeEvent
    public static void onCurioEquip(CurioEquipEvent event) {
        ItemStack stack = event.getStack();
        if (!(stack.getItem() instanceof FlyingSwordItem)) return;
        if (!"feet".equals(event.getSlotContext().identifier())) return;

        // 移除所有 event.setCanceled(true) 调用，不再阻止任何装备行为
        // 飞剑损坏时也可装备，由 Tick 事件控制飞行启用与否

        Player player = (Player) event.getEntity();
        if (!player.level().isClientSide) {
            // 防止重复装备同一把飞剑（但不取消事件，只是静默返回）
            UUID emittingId = FlyingSwordItem.EMITTING_PLAYER.get();
            if (emittingId != null && emittingId.equals(player.getUUID())) {
                return;
            }

            // 记录飞剑主动开启飞行的标记
            player.getPersistentData().putBoolean(FLYING_SWORD_ACTIVE, true);
            // 保存玩家原本的 mayfly 状态，以便恢复
            player.getPersistentData().putBoolean("flying_sword_prev_mayfly", player.getAbilities().mayfly);

            // 启用飞行能力（即使损坏也会启用，但 Tick 事件会立即处理）
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
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // 死亡复活后先清空遗留的飞行状态（避免 curios 重放 equip 造成状态错乱）
        clearFlyingState(player);

        // 若脚部仍装备着未损坏的飞剑，重新启用飞行能力（不强制飞行，由玩家自行起飞）
        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return;
        var slotResult = curios.get().findFirstCurio(stack -> stack.getItem() instanceof FlyingSwordItem);
        if (slotResult.isEmpty()) return;
        ItemStack stack = slotResult.get().stack();
        if (isFlyingSwordBroken(stack)) return;

        player.getPersistentData().putBoolean(FLYING_SWORD_ACTIVE, true);
        player.getPersistentData().putBoolean("flying_sword_prev_mayfly", player.getAbilities().mayfly);
        player.getAbilities().mayfly = true;
        // 复活后不强制 flying，避免自动消耗耐久与不可控飞行；玩家双击空格起飞
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.isCreative()) return; // 创造性玩家不干预

        var curios = CuriosApi.getCuriosInventory(player).resolve();
        if (curios.isEmpty()) return;
        var slotResult = curios.get().findFirstCurio(stack -> stack.getItem() instanceof FlyingSwordItem);

        boolean hasFlyingSword = slotResult.isPresent();
        boolean active = player.getPersistentData().getBoolean(FLYING_SWORD_ACTIVE);

        // 如果标记为 active，但实际没有飞剑或飞剑损坏 → 清除飞行状态
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

        // 有飞剑且未损坏，且玩家正在飞行 → 确保脚部实体存在
        if (hasFlyingSword && slotResult.isPresent()) {
            ItemStack stack = slotResult.get().stack();
            if (!isFlyingSwordBroken(stack) && player.getAbilities().flying) {
                if (!hasFootEntity(player)) {
                    spawnFootEntity(player, stack);
                }
            } else {
                // 飞剑损坏或玩家没有飞行，移除实体
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
            // 损坏后不再消耗耐久，并清除飞行状态
            if (isFlyingSwordBroken(stack)) {
                clearFlyingState(player);
                continue;
            }

            // ✅ 走匠魂正常耐久逻辑：受粘液覆层（slime covering）等 onDamageTool 钩子减免
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool != null) {
                slimeknights.tconstruct.library.tools.helper.ToolDamageUtil.damage(tool, 1, player, stack);
            }
        }
    }

    // ===== 摔落伤害逻辑修正 =====
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // 只在飞行状态下取消摔伤
        if (player.getAbilities().flying) {
            event.setCanceled(true);
        }
        // 关闭飞行后，正常计算摔伤，由原版处理
    }

    // ===== 辅助方法 =====

    /**
     * 清除飞剑带来的飞行状态，并恢复玩家原有的 mayfly 权限
     */
    private static void clearFlyingState(Player player) {
        // 只有飞剑主动开启了飞行，才恢复 mayfly 和清除标记
        if (player.getPersistentData().getBoolean(FLYING_SWORD_ACTIVE)) {
            boolean prevMayfly = player.getPersistentData().getBoolean("flying_sword_prev_mayfly");
            player.getAbilities().mayfly = prevMayfly;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();

            player.getPersistentData().remove(FLYING_SWORD_ACTIVE);
            player.getPersistentData().remove("flying_sword_prev_mayfly");
        }
        // 无论是否主动开启，都移除实体
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