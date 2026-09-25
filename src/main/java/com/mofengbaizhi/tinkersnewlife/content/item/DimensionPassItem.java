package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.portal.WhiteSpaceDimensions;
import com.mofengbaizhi.tinkersnewlife.network.portal.PacketOpenDimensionPassScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * <b>维度通行证</b>（§660）：一次性开门的钥匙。只能堆叠 1 个，<b>用后消耗</b>。
 *
 * <h2>两种用法</h2>
 * <ol>
 *   <li><b>在非白色空间</b>（主世界等）对着方块右键 ⇒ 直接在该方块<b>上方</b>开一扇通往
 *       伟大白色空间的永久传送门；落点＝白色空间里<b>相同 xz、地面表层</b>的位置，
 *       并在那里同时开一扇指回来的门（成对 ⇒ 可自由往返）。</li>
 *   <li><b>在伟大白色空间里</b>对着方块右键 ⇒ 先弹 GUI（下拉选维度 + 填 xyz），
 *       确认后在该方块上方开一扇通往那个坐标的永久传送门 —— <b>通行证在确认开门时才消耗</b>，
 *       所以中途按 ESC 关掉 GUI 不会白扔一张 ✓。</li>
 * </ol>
 *
 * <p>⚠ 客户端只返回 {@code SUCCESS}（摆手），真正的判定与消耗全在服务端 ✓ 防刷。
 */
public class DimensionPassItem extends Item {

    public DimensionPassItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        if (WhiteSpaceDimensions.isWhiteSpace(level)) {
            // 白色空间里：交给 GUI 决定去哪（锚点一起发过去，确认时原样带回来）
            TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new PacketOpenDimensionPassScreen(
                            WhiteSpaceDimensions.dimensionIds(serverPlayer.getServer()), pos));
            return InteractionResult.CONSUME;
        }

        WhiteSpaceDimensions.PortalResult result = WhiteSpaceDimensions.linkFromOutside(serverLevel, pos);
        serverPlayer.displayClientMessage(result.message(), true);
        if (result.ok()) {
            // 门就开在玩家脚边：给个冷却，免得开完门站在原地立刻被自己的门吸走
            WhiteSpaceDimensions.armCooldown(serverPlayer, WhiteSpaceDimensions.CREATE_COOLDOWN);
            consume(context.getItemInHand(), serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    /** 用掉一张（{@code stacksTo(1)} ⇒ 直接变空）并同步给客户端 */
    public static void consume(ItemStack stack, ServerPlayer player) {
        stack.shrink(1);
        player.containerMenu.broadcastChanges();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.tinkersnewlife.dimension_pass.desc")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinkersnewlife.dimension_pass.desc2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.tinkersnewlife.dimension_pass.desc3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
