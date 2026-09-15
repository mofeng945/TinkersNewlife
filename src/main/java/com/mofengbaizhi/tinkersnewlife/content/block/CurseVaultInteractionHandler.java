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
 * <b>空手左键直接回收</b>（不要求潜行）；手上有东西时给一次提示（带冷却）。
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
            // ⭐⭐ 只接管"能装/能倒咒力残秽"的容器（空桶、装着残秽的桶、封呪瓶…）。
            //     水桶、方块、工具一律不干预，交回正常逻辑。
            if (!isResidueContainer(player.getMainHandItem())) return;

            boolean moved = false;
            if (!level.isClientSide) {
                moved = tryFluidTransfer(level, pos, player);
            }
            // ⭐⭐ 客户端与**服务端都必须取消**：
            //   以前只在服务端取消（客户端因为不能查方块实体直接 return 了），客户端于是照常走完原版交互、
            //   又补发一个 useItem 包 → 服务端接着执行一次 Item#use，后果是：
            //     · 空桶：刚接满的残秽被"倒在外面"，桶还变空；
            //     · 封呪瓶：Curios 的 canEquipFromUse 走 PlayerInteractEvent.RightClickItem 把它戴到饰品槽上。
            //   两端一起取消，客户端就不会再发那个包，两个毛病一起消失。
            event.setCanceled(true);
            event.setCancellationResult(!level.isClientSide && !moved
                    ? InteractionResult.PASS
                    : InteractionResult.SUCCESS);
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
            // ② 容器是空的（或装的不是残秽）→ 从呪蔵接出残秽
            //    ⚠ 以前直接把"呪蔵全部存量"丢给 container.fill()：存量 1000~2000 时会出现
            //      "容器只接了 1000、呪蔵却按容器返回的量扣"的错配，>2000 时更是把 2000+ 塞进一个桶。
            //    现在改成"先问容器能接多少（SIMULATE），再按这个量精确取"。
            int available = vault.drain(Integer.MAX_VALUE, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE).getAmount();
            if (available > 0) {
                int want = Math.min(available, 1000);      // 单次最多搬一桶（1000mb），桶/瓶都按各自容量自动截断
                net.minecraftforge.fluids.FluidStack probe = new net.minecraftforge.fluids.FluidStack(
                        com.mofengbaizhi.tinkersnewlife.content.ModFluids.CURSE_RESIDUE.still.get(), want);
                int accepted = container.fill(probe, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
                if (accepted > 0) {
                    net.minecraftforge.fluids.FluidStack taken =
                            vault.drain(accepted, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    if (!taken.isEmpty()) {
                        container.fill(taken, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                        withdraw = true;
                    }
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

    /**
     * 手里这件东西是不是"能装 / 能倒咒力残秽"的容器。
     *
     * <p>客户端也要能判断（客户端拿不到呪蔵的方块实体数据），所以我们**只看手里那件物品**：
     * <ul>
     *   <li>没有流体能力的物品 → 不干预；</li>
     *   <li>容器里装的是**别的**流体（如水桶）→ 不干预，交给原版倒水；</li>
     *   <li>容器是空的（可接残秽）或装着残秽（可倒回呪蔵）→ 由我们接管。</li>
     * </ul>
     */
    private static boolean isResidueContainer(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemStack probe = stack.copyWithCount(1);
        var opt = net.minecraftforge.fluids.FluidUtil.getFluidHandler(probe);
        if (!opt.isPresent()) return false;
        var handler = opt.orElse(null);
        if (handler == null) return false;
        net.minecraftforge.fluids.FluidStack contained =
                handler.drain(Integer.MAX_VALUE, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        return contained.isEmpty() || isResidue(contained);
    }

    /**
     * 左键：
     * <ul>
     *   <li><b>空手（不要求潜行）→ 直接回收</b>：方块"无法破坏"，玩家左键本来就是"拆"的意思，
     *       所以空手左键直接收成物品（保留咒力）；</li>
     *   <li>手上拿着东西 → 给一次提示（40 tick 冷却），避免"怎么挖不掉"的困惑。</li>
     * </ul>
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!isVault(level, pos)) return;

        if (player.getMainHandItem().isEmpty()) {
            InteractionResult result = CurseVaultBlock.pickup(level, pos, player);
            event.setCanceled(true);
            event.setCancellationResult(result);
            return;
        }

        // 手上有东西（想挖）：方块无法破坏，提示一下怎么回收
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
