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
     * 共享交互逻辑（右键路径）：
     * <ul>
     *   <li>潜行 + 主手为空 → {@link #pickup}（回收成物品，保留咒力）；</li>
     *   <li>主手为空（未潜行）→ 报储量；</li>
     *   <li>手里有东西 → 返回 {@code null} 表示"不处理"，交给正常逻辑。</li>
     * </ul>
     */
    @Nullable
    public static InteractionResult handleInteraction(Level level, BlockPos pos, Player player, boolean emptyMainHand) {
        if (player.isShiftKeyDown() && emptyMainHand) return pickup(level, pos, player);
        if (emptyMainHand) return statusMessage(level, pos, player);
        // 手里有东西（拿方块/工具/桶…）→ 不拦截，交给正常逻辑（放方块、用工具、倒流体等）
        return null;
    }

    /**
     * **回收**成物品并保留其中咒力（仅使用者本人；主手为空时由右键-潜行 或 左键触发）。
     * <p>先删数据再拆方块：{@link #onRemove} 发现条目已不存在，就不会再掉一份物品。
     */
    public static InteractionResult pickup(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;   // 客户端预测成功，真正执行在服务端
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        CurseVaultData data = CurseVaultData.get(serverLevel);
        CurseVaultData.Entry entry = data.get(pos);
        if (entry != null && !entry.owner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_vault.not_owner")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        double power = entry == null ? 0 : entry.power;
        data.remove(pos);
        ItemStack stack = new ItemStack(ModItems.CURSE_VAULT.get());
        com.mofengbaizhi.tinkersnewlife.content.item.CurseVaultItem.setPower(stack, power);
        // ⭐ 这一块如果是"拟造"出来的方块，收回时要把拟造标记（前缀 + 原到期时间）一并还回去：
        //    否则放下再收起就变成一件真呪蔵 = 白嫖一个永久方块。
        stack = com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique
                .reclaimPlacedTemp(serverLevel, pos, stack, true);
        level.removeBlock(pos, false);
        CurseVaultInteractionHandler.giveStackToPlayer(player, stack);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_vault.picked_up",
                CursePowerHelper.formatAmount(power)), true);
        return InteractionResult.SUCCESS;
    }

    /** 空手右键：报出储量 / 容量 */
    public static InteractionResult statusMessage(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
        CurseVaultData.Entry entry = CurseVaultData.get(serverLevel).get(pos);
        double power = entry == null ? 0 : entry.power;
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.curse_vault.status",
                        CursePowerHelper.formatAmount(power),
                        CursePowerHelper.formatAmount(CurseVaultData.CAPACITY))
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return InteractionResult.SUCCESS;
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
                if (com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique
                        .isConstructedBlockAt(serverLevel, pos, state)) {
                    // ⭐ 这是"拟造"出来的呪蔵，而且是**到期消散**（ConstructTechnique 那边无掉落直接消失）：
                    //    这里绝不能掉出一件真呪蔵——否则一件临时方块到期反而洗成永久方块 + 白送存量。
                    //    玩家主动挖掉走的是 ConstructTechnique#onBlockBreak（会把带原到期时间的本体还给他），
                    //    走不到这条分支。
                    data.remove(pos);
                } else {
                    data.remove(pos);
                    ItemStack stack = new ItemStack(ModItems.CURSE_VAULT.get());
                    com.mofengbaizhi.tinkersnewlife.content.item.CurseVaultItem.setPower(stack, entry.power);
                    // 拟造方块被外力移除（这里方块已经换掉了，所以 requireSameBlock=false）→ 同样保留拟造标记
                    stack = com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique
                            .reclaimPlacedTemp(serverLevel, pos, stack, false);
                    Block.popResource(level, pos, stack);
                }
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
