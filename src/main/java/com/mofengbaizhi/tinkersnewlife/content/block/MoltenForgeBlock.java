package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * 融锻炉（独立熔炼方块）：右键打开匠魂风格 GUI——工具放入熔炼槽后每 tick 读取所有部件材料，
 * 多流体产物注入本炉容器（每部件一单位），无流体材料直接化掉；产物可被桶/管道抽取。
 */
public class MoltenForgeBlock extends Block implements EntityBlock {

    public MoltenForgeBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(5.0f, 8.0f)
                .sound(SoundType.METAL));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MoltenForgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, st, te) -> {
            if (te instanceof MoltenForgeBlockEntity mf) mf.serverTick();
        };
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.sidedSuccess(true);
        if (level.getBlockEntity(pos) instanceof MoltenForgeBlockEntity forge && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, new MoltenForgeMenuProvider(forge),
                    (FriendlyByteBuf data) -> data.writeBlockPos(pos));
            return InteractionResult.sidedSuccess(true);
        }
        return InteractionResult.PASS;
    }
}
