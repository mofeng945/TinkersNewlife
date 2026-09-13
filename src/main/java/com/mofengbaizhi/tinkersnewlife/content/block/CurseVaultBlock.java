package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseVaultData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * 呪蔵（zhòu zàng）：可放置的咒力容器方块。
 *
 * <ul>
 *   <li><b>放置即绑定</b>：放下时把放置者记为该呪蔵的使用者（{@link CurseVaultData}）；</li>
 *   <li><b>容量 10 万咒力</b>（= 10000 mb 咒力残秽，1mb = 10 咒力）；</li>
 *   <li><b>无法破坏</b>：硬度 -1（同基岩）、抗爆 3600000、活塞推不动、无掉落物表；</li>
 *   <li><b>回收</b>：使用者主手为空 + 潜行 + 右键 → 收回成物品并**保留其中咒力**；</li>
 *   <li><b>空手右键</b>：报出当前储量 / 容量与使用者；</li>
 *   <li>流体能力见 {@link CurseVaultBlockEntity}（接匠魂熔炉里的咒力残秽）。</li>
 * </ul>
 */
public class CurseVaultBlock extends Block implements EntityBlock {

    public CurseVaultBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(-1.0F, 3600000.0F)   // 无法破坏（同基岩硬度 -1 + 高抗爆）
                .sound(SoundType.NETHERITE_BLOCK)
                .pushReaction(PushReaction.BLOCK)   // 活塞推不动
                .noLootTable());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CurseVaultBlockEntity(pos, state);
    }

    /** 每 20 tick 把存量广播给客户端（十字光标提示用） */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return type == com.mofengbaizhi.tinkersnewlife.content.ModBlockEntities.CURSE_VAULT.get()
                ? (lvl, pos, st, be) -> CurseVaultBlockEntity.serverTick(lvl, pos, st, (CurseVaultBlockEntity) be)
                : null;
    }

    // ============================================================
    //  放置：绑定使用者 + 继承物品里的咒力
    // ============================================================

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                           @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || placer == null) return;
        CurseVaultData.get(serverLevel).register(pos, placer.getUUID(),
                com.mofengbaizhi.tinkersnewlife.content.item.CurseVaultItem.getPower(stack));
    }

    // ============================================================
    //  交互
    // ============================================================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // ⚠ 这条路径只在"主手与副手都为空"时才会被原版调用（潜行时副手拿东西原版直接跳过方块 use）。
        //    真正的入口是 CurseVaultInteractionHandler（Forge 的 RightClickBlock 事件），这里只是兜底。
        InteractionResult result = handleInteraction(level, pos, player, player.getMainHandItem().isEmpty());
        return result == null ? InteractionResult.PASS : result;
    }

    /**
     * 共享交互逻辑：
     * <ul>
     *   <li>潜行 + 主手为空 → 回收成物品（保留咒力；仅使用者本人）；</li>
     *   <li>主手为空（未潜行）→ 报储量；</li>
     *   <li>手里有东西 → 返回 {@code null} 表示"不处理"，交给正常逻辑。</li>
     * </ul>
     */
    @Nullable
    public static InteractionResult handleInteraction(Level level, BlockPos pos, Player player, boolean emptyMainHand) {
        if (level.isClientSide) return InteractionResult.SUCCESS;   // 客户端预测成功，真正执行在服务端
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        CurseVaultData data = CurseVaultData.get(serverLevel);
        CurseVaultData.Entry entry = data.get(pos);

        // 1) 使用者 + 主手为空 + 潜行 → 回收成物品（保留咒力）
        if (player.isShiftKeyDown() && emptyMainHand) {
            if (entry != null && !entry.owner.equals(player.getUUID())) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_vault.not_owner")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }
            double power = entry == null ? 0 : entry.power;
            // ⭐ 先删数据再拆方块：onRemove 发现条目已不存在就不会再掉一份物品
            data.remove(pos);
            ItemStack stack = new ItemStack(ModItems.CURSE_VAULT.get());
            com.mofengbaizhi.tinkersnewlife.content.item.CurseVaultItem.setPower(stack, power);
            level.removeBlock(pos, false);
            CurseVaultInteractionHandler.giveStackToPlayer(player, stack);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_vault.picked_up",
                    CursePowerHelper.formatAmount(power)), true);
            return InteractionResult.SUCCESS;
        }

        // 2) 空手右键 → 报储量
        if (emptyMainHand) {
            double power = entry == null ? 0 : entry.power;
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_vault.status",
                            CursePowerHelper.formatAmount(power),
                            CursePowerHelper.formatAmount(CurseVaultData.CAPACITY))
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return InteractionResult.SUCCESS;
        }
        // 手里有东西（拿方块/工具/桶…）→ 不拦截，交给正常逻辑（放方块、用工具、倒流体等）
        return null;
    }

    // ============================================================
    //  其它移除方式（/setblock、世界编辑等）：把存量以物品形式还回去，避免凭空消失
    // ============================================================

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            CurseVaultData data = CurseVaultData.get(serverLevel);
            CurseVaultData.Entry entry = data.get(pos);
            if (entry != null) {
                data.remove(pos);
                ItemStack stack = new ItemStack(ModItems.CURSE_VAULT.get());
                com.mofengbaizhi.tinkersnewlife.content.item.CurseVaultItem.setPower(stack, entry.power);
                Block.popResource(level, pos, stack);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // 抗爆炸/活塞：硬度 -1 + pushReaction BLOCK 已足够；这里再声明不会被方块实体清除
    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 15;
    }
}
