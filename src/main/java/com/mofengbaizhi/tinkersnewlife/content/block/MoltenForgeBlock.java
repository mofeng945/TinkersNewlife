package com.mofengbaizhi.tinkersnewlife.content.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import slimeknights.tconstruct.library.tools.item.IModifiable;

import javax.annotation.Nullable;

/**
 * 融锻炉（独立熔炼方块）：右键放入匠魂工具 → 读取所有部件材料，多流体产物注入本炉容器。
 * <p>产物可通过桶/管道抽取（{@link MoltenForgeBlockEntity} 实现多流体 {@code IFluidHandler}）。
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

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof MoltenForgeBlockEntity forge) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof IModifiable) {
                if (forge.molten(held)) {
                    held.shrink(1);
                    player.displayClientMessage(Component.translatable("message.tinkersnewlife.melt_forge.molten"), true);
                } else {
                    player.displayClientMessage(Component.translatable("message.tinkersnewlife.melt_forge.no_fluid"), true);
                }
                return InteractionResult.sidedSuccess(true);
            }
            // 空手查看：列出容器内流体
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < forge.getTanks(); i++) {
                net.minecraftforge.fluids.FluidStack fs = forge.getFluidInTank(i);
                if (!fs.isEmpty()) {
                    if (sb.length() > 0) sb.append("，");
                    sb.append(fs.getFluid().getFluidType().getDescription().getString())
                      .append(" x").append(fs.getAmount());
                }
            }
            player.displayClientMessage(Component.literal(
                    sb.length() == 0
                            ? net.minecraft.network.chat.Component.translatable("message.tinkersnewlife.melt_forge.empty").getString()
                            : "§b" + sb), true);
            return InteractionResult.sidedSuccess(true);
        }
        return InteractionResult.PASS;
    }
}
