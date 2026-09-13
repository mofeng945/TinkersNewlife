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
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!isVault(level, pos)) return;

        // ① 手里拿着流体容器（桶 / 封呪瓶 …）：做咒力残秽的接与倒
        if (!player.getMainHandItem().isEmpty()) {
            if (tryFluidTransfer(level, pos, player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;   // 手里有东西且不是流体交互 → 完全不干预（放方块/用工具）
        }

        // ② 空手：潜行回收 / 报储量
        InteractionResult result = CurseVaultBlock.handleInteraction(level, pos, player, true);
        if (result == null) return;
        event.setCanceled(true);
        event.setCancellationResult(result);
    }

    /**
     * 手里的流体容器 ⇄ 呪蔵（**手动搬运，不用 Forge 的 tryEmptyContainer**）。
     * <p>⚠ 踩过的坑：{@code FluidUtil.tryEmptyContainer} 最终走的是 {@code ...AndStow}，会把"处理后的容器"
     * <b>塞进玩家背包</b>，而手里那个原容器还在 —— 于是出现"呪蔵里的残秽涨了、瓶子却没清空"的复制现象。
     * 所以这里改成：在**副本**上做 fill/drain，成功后把 {@code handler.getContainer()} 写回手持槽。
     */
    private static boolean tryFluidTransfer(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level instanceof ServerLevel)) return false;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return false;

        var vaultOpt = net.minecraftforge.fluids.FluidUtil.getFluidHandler(level, pos, null);
        if (!vaultOpt.isPresent()) return false;
        net.minecraftforge.fluids.capability.IFluidHandler vault = vaultOpt.orElse(null);

        // 在副本上操作（避免中途失败留下半成品状态）
        ItemStack work = held.copyWithCount(1);
        var itemOpt = net.minecraftforge.fluids.FluidUtil.getFluidHandler(work);
        if (!itemOpt.isPresent()) return false;
        net.minecraftforge.fluids.capability.IFluidHandlerItem container = itemOpt.orElse(null);

        boolean deposit = false;
        boolean withdraw = false;
        // ① 容器里有残秽 → 倒进呪蔵
        net.minecraftforge.fluids.FluidStack contained =
                container.drain(Integer.MAX_VALUE, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        if (!contained.isEmpty() && isResidue(contained)) {
            int accepted = vault.fill(contained, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) {
                container.drain(accepted, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                deposit = true;
            }
        } else {
            // ② 容器是空的 → 从呪蔵接出残秽
            net.minecraftforge.fluids.FluidStack drained =
                    vault.drain(Integer.MAX_VALUE, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
            if (!drained.isEmpty()) {
                int filled = container.fill(drained, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    vault.drain(filled, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    withdraw = true;
                }
            }
        }

        if (!deposit && !withdraw) return false;

        // ⭐ 把处理后的容器写回手持槽（原地替换；绝不 insertItem，避免复制）
        ItemStack result = container.getContainer();
        result.setCount(held.getCount());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, result);

        double power = CurseVaultData.get((ServerLevel) level).getPower(pos);
        player.displayClientMessage(Component.translatable(deposit
                        ? "message.tinkersnewlife.curse_vault.deposited"
                        : "message.tinkersnewlife.curse_vault.withdrawn",
                com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper.formatAmount(power)), true);
        return true;
    }

    /** 是不是本模组的「咒力残秽」 */
    private static boolean isResidue(net.minecraftforge.fluids.FluidStack stack) {
        var fluid = com.mofengbaizhi.tinkersnewlife.content.ModFluids.CURSE_RESIDUE.still.get();
        return fluid != null && stack.getFluid() == fluid;
    }

    /** 供方块自身调用（原版 use 路径的兜底；正常情况下事件层已经处理并取消） */
    public static void giveStackToPlayer(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
