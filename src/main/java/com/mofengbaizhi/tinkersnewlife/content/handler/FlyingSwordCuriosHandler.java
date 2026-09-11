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
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.List;
import java.util.UUID;

/**
 * 飞剑·脚部饰品飞行。
 *
 * <h3>为什么必须"严格校验脚部槽位"</h3>
 * Curios 的 {@code findFirstCurio(predicate)} 会<b>无视槽位类型</b>扫描所有饰品槽，
 * 于是"飞剑塞在别的饰品槽里"甚至"使用飞剑时触发的伪装备事件"都会被当成已装备 →
 * 飞行能力被错误开启（玩家没穿在脚上却能飞）。本类因此：
 * <ul>
 *   <li>{@link #hasFlyingSwordInFeet(Player)}：只认 <b>feet</b> 槽位里的飞剑；</li>
 *   <li>装备时<b>只授予 mayfly（飞行权限），不再强制 flying=true</b>——是否起飞交给玩家
 *       （双击空格），避免"一装备就悬空"；</li>
 *   <li>每 tick 校验：不在脚部 → 立即撤销飞行并还原玩家原本的 mayfly 权限。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlyingSwordCuriosHandler {

    private static int tickCounter = 0;
    private static final String FLYING_SWORD_ACTIVE = "flying_sword_active";
    /** 飞剑所在的脚部饰品槽标识 */
    private static final String FEET_SLOT = "feet";

    private static boolean isFlyingSwordBroken(ItemStack stack) {
        if (stack.isEmpty()) return true;
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool == null || tool.isBroken();
    }

    /**
     * 飞剑是否<b>确实装备在脚部饰品位</b>（严格版）。
     * 不使用 {@code findFirstCurio}——它会无视槽位类型，是"飞行被错误开启"的根源。
     */
    public static boolean hasFlyingSwordInFeet(Player player) {
        try {
            var curios = CuriosApi.getCuriosInventory(player).resolve();
            if (curios.isEmpty()) return false;
            var handler = curios.get().getStacksHandler(FEET_SLOT);
            if (handler.isEmpty()) return false;
            var stacks = handler.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (stacks.getStackInSlot(i).getItem() instanceof FlyingSwordItem) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 脚部槽位里的那把飞剑（没有则 EMPTY） */
    private static ItemStack getFeetSword(Player player) {
        try {
            var curios = CuriosApi.getCuriosInventory(player).resolve();
            if (curios.isEmpty()) return ItemStack.EMPTY;
            var handler = curios.get().getStacksHandler(FEET_SLOT);
            if (handler.isEmpty()) return ItemStack.EMPTY;
            var stacks = handler.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.getItem() instanceof FlyingSwordItem) return stack;
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void onCurioEquip(CurioEquipEvent event) {
        ItemStack stack = event.getStack();
        if (!(stack.getItem() instanceof FlyingSwordItem)) return;
        if (!FEET_SLOT.equals(event.getSlotContext().identifier())) return;   // 只认脚部槽

        Player player = (Player) event.getEntity();
        if (player.level().isClientSide) return;

        // 右键发射飞剑时 TCon 会更新工具 NBT，可能连带触发一次伪装备事件 → 忽略
        UUID emittingId = FlyingSwordItem.EMITTING_PLAYER.get();
        if (emittingId != null && emittingId.equals(player.getUUID())) {
            return;
        }
        // 损坏的飞剑不授予飞行
        if (isFlyingSwordBroken(stack)) return;

        // ⭐ 只授予"飞行权限"，不强制起飞（原来直接 flying=true 会导致一装备就悬空）
        player.getPersistentData().putBoolean(FLYING_SWORD_ACTIVE, true);
        player.getPersistentData().putBoolean("flying_sword_prev_mayfly", player.getAbilities().mayfly);
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
        TinkersNewlife.LOGGER.info("[飞剑] 授予飞行能力（脚部饰品）：玩家={}", player.getName().getString());
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioUnequipEvent event) {
        ItemStack stack = event.getStack();
        if (!(stack.getItem() instanceof FlyingSwordItem)) return;
        if (!FEET_SLOT.equals(event.getSlotContext().identifier())) return;

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

        // 若脚部仍装备着未损坏的飞剑，重新授予飞行权限（不强制起飞）
        ItemStack stack = getFeetSword(player);
        if (stack.isEmpty() || isFlyingSwordBroken(stack)) return;

        player.getPersistentData().putBoolean(FLYING_SWORD_ACTIVE, true);
        player.getPersistentData().putBoolean("flying_sword_prev_mayfly", player.getAbilities().mayfly);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.isCreative()) return; // 创造性玩家不干预

        boolean active = player.getPersistentData().getBoolean(FLYING_SWORD_ACTIVE);
        // ⭐ 严格判定：只有脚部饰品槽里的飞剑才算"装备中"（手持/其他饰品槽都不算）
        ItemStack feetSword = getFeetSword(player);
        boolean equipped = !feetSword.isEmpty();

        // 标记为启用中，但实际没有（或损坏）→ 撤销
        if (active) {
            if (!equipped || isFlyingSwordBroken(feetSword)) {
                clearFlyingState(player);
                return;
            }
        } else if (equipped && !isFlyingSwordBroken(feetSword) && player.getAbilities().mayfly) {
            // 外部把标记清了但权限还在（例如其它模组/维度切换）：补回标记，保持状态一致
            player.getPersistentData().putBoolean(FLYING_SWORD_ACTIVE, true);
        }

        // 正在飞行 → 确保脚下的飞剑实体存在；否则清掉
        if (equipped && !isFlyingSwordBroken(feetSword)) {
            if (player.getAbilities().flying) {
                if (!hasFootEntity(player)) {
                    spawnFootEntity(player, feetSword);
                }
            } else if (hasFootEntity(player)) {
                removeFootEntity(player);
            }
        } else if (hasFootEntity(player)) {
            removeFootEntity(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % 100 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive() || !player.getAbilities().flying) continue;
            // ⭐ 只有脚部饰品槽的飞剑才消耗耐久（手持的不消耗）
            ItemStack stack = getFeetSword(player);
            if (stack.isEmpty()) continue;
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
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = prevMayfly;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                TinkersNewlife.LOGGER.info("[飞剑] 撤销飞行能力：玩家={}", player.getName().getString());
            }

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
