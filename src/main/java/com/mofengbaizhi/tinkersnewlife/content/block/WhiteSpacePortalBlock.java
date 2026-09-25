package com.mofengbaizhi.tinkersnewlife.content.block;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.portal.WhiteSpaceDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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

import javax.annotation.Nullable;
import java.util.Set;

/**
 * <b>伟大白色空间传送门</b>（§660）。
 *
 * <ul>
 *   <li><b>无碰撞、不挡视线、自发光 15</b> —— 走进去就传送，不会挡住路；</li>
 *   <li><b>目的地</b>存在 {@link WhiteSpacePortalBlockEntity} 里（维度 + xyz）⇒ 永久、任意坐标 ✓；</li>
 *   <li><b>传送时机</b>：玩家身体与门方块相交时（{@code entityInside}，每 tick 由
 *       {@code Entity#checkInsideBlocks} 调用）。<b>只传送玩家</b> —— 怪物/掉落物直接穿过，
 *       免得门变成怪物农场或者把掉落物丢到别的维度去 ✗；</li>
 *   <li><b>防乒乓</b>：两扇门互相指向对方，落地就在对面门里 ⇒ 没有冷却会来回弹。
 *       冷却记在玩家自己的持久化数据里（{@link WhiteSpaceDimensions#armCooldown}）✓。</li>
 *   <li><b>可挖</b>：硬度 25 / 抗爆 1200（比黑曜石略软），<b>无掉落表 ⇒ 挖掉什么都不掉</b>。
 *       维度类型里 {@code bed_works=true}、这个世界是交通枢纽，玩家必须能把开错位置的门清掉 ✓
 *       （地面那一层白色方块才是不可破坏的，见 {@code ModBlocks.WHITE_SPACE_BLOCK}）。</li>
 * </ul>
 */
public class WhiteSpacePortalBlock extends Block implements EntityBlock {

    public WhiteSpacePortalBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.SNOW)
                .strength(25.0F, 1200.0F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 15)
                .noCollission()
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .pushReaction(PushReaction.BLOCK));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WhiteSpacePortalBlockEntity(pos, state);
    }

    /** 天空光能透下来（同玻璃）—— 免得白色空间里那一层门在地面上投出一块暗斑 */
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** 客户端气氛：偶尔飘几颗白色光点 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0D, 0.01D, 0.0D);
    }

    // ============================================================
    //  传送
    // ============================================================

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)) return;
        if (WhiteSpaceDimensions.isOnCooldown(player)) return;
        if (!(level.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity portal)) return;
        if (!portal.hasDestination()) return;

        ResourceKey<Level> destKey = portal.getDestinationDimension();
        if (destKey == null) return;
        MinecraftServer server = player.getServer();
        ServerLevel target = server == null ? null : server.getLevel(destKey);
        if (target == null) return;

        BlockPos dest = portal.getDestinationPos();
        // 先上冷却：落地时人就在对面那扇门里，没冷却会立刻被送回来
        WhiteSpaceDimensions.armCooldown(player, WhiteSpaceDimensions.ARRIVE_COOLDOWN);
        try {
            player.teleportTo(target, dest.getX() + 0.5D, dest.getY(), dest.getZ() + 0.5D,
                    Set.of(), player.getYRot(), player.getXRot());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[伟大白色空间] 跨维度传送异常：{}", t.toString());
        }
        if (player.serverLevel() != target) {
            // 被别的模组拦了（Forge 的 EntityTravelToDimensionEvent 是可以被取消的）⇒ 1 秒后再试
            WhiteSpaceDimensions.armCooldown(player, 20);
            player.displayClientMessage(
                    Component.translatable("message.tinkersnewlife.white_space.travel_blocked"), true);
        }
    }
}
