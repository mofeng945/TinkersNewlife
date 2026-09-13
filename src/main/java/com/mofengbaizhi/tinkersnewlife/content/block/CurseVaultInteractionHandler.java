package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseVaultData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 呪蔵的交互入口（Forge 事件层）。
 *
 * <h2>为什么不能只靠 {@code Block#use}</h2>
 * 原版 {@code ServerPlayerGameMode#useItemOn} 有这么一句：
 * <pre>
 * boolean flag  = !主手.isEmpty() || !副手.isEmpty();
 * boolean flag1 = player.isSecondaryUseActive() && flag;   // 潜行 + 任一手有东西
 * if (!flag1) { blockstate.use(...); }                     // 否则连方块 use 都不调用
 * </pre>
 * 也就是说：**只要副手拿着东西（火把/盾/物品）再潜行右键，方块 {@code use} 根本不会被调用**，
 * 于是"潜行空手右键回收"会莫名失效（主手明明是空的）。所以这里改成监听 Forge 的
 * {@link PlayerInteractEvent.RightClickBlock}（在原版那套判定之前触发，可取消），
 * 只要主手为空就由我们自己处理，不受副手影响。
 *
 * <p>另外补一条**左键**路径：方块无法破坏，玩家很容易习惯性地去"挖"它 ——
 * 潜行 + 空手左键同样能回收，非潜行空手左键则提示怎么回收（带冷却，避免刷屏）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseVaultInteractionHandler {

    /** 提示冷却的玩家持久数据键（避免左键提示刷屏） */
    private static final String KEY_HINT_TICK = "tinkersnewlife.curse_vault_hint_tick";
    private static final long HINT_COOLDOWN_TICKS = 40;

    private CurseVaultInteractionHandler() {
    }

    private static boolean isVault(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof CurseVaultBlock;
    }

    /** 潜行 + 主手为空 + 右键 → 回收；主手为空（未潜行）→ 报储量（先把副手干扰排除在外） */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().isEmpty()) return;   // 手里有东西：完全不干预（放方块/用工具/桶）
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!isVault(level, pos)) return;

        InteractionResult result = CurseVaultBlock.handleInteraction(level, pos, player, true);
        if (result == null) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    /** 左键：潜行+空手 → 回收；非潜行空手 → 提示（带冷却） */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.getMainHandItem().isEmpty()) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!isVault(level, pos)) return;

        if (player.isShiftKeyDown()) {
            InteractionResult result = CurseVaultBlock.handleInteraction(level, pos, player, true);
            if (result != null) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
            return;
        }

        // 非潜行（比如直接想挖掉它）：给一次提示，避免"怎么挖不掉"的困惑
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        if (!(player.getPersistentData().getLong(KEY_HINT_TICK) + HINT_COOLDOWN_TICKS < serverLevel.getGameTime())) return;
        player.getPersistentData().putLong(KEY_HINT_TICK, serverLevel.getGameTime());
        CurseVaultData.Entry entry = CurseVaultData.get(serverLevel).get(pos);
        double power = entry == null ? 0 : entry.power;
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_vault.hint",
                com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper.formatAmount(power),
                com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper.formatAmount(CurseVaultData.CAPACITY)), true);
    }

    /** 供方块自身调用（原版 use 路径的兜底；正常情况下事件层已经处理并取消） */
    public static void giveStackToPlayer(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
