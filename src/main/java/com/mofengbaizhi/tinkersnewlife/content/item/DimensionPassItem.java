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
 * <h2>两种用法（§660 原始口径 ＋ §661 用户补充：两种都走 GUI）</h2>
 * <ol>
 *   <li><b>在伟大白色空间之外的（非黑名单）维度</b>对着方块右键 ⇒ 弹 GUI，其中<b>维度锁死</b>为
 *       伟大白色空间、<b>不能填 y</b>，只有 x／z 可改（默认就是门自己的 xz ⇒ 相对落点 ✓）；
 *       确认后在该方块<b>上方</b>开一扇永久门，落点＝白色空间里那个 xz、地面表层，
 *       并在那里同时开一扇指回来的门（成对 ⇒ 可自由往返）。</li>
 *   <li><b>在伟大白色空间里</b>对着方块右键 ⇒ 弹 GUI，<b>下拉</b>选维度（只列去过且不在黑名单的）
 *       ＋ 填 xyz，确认后在该方块上方开一扇通往那个坐标的永久门。</li>
 * </ol>
 * <b>消耗时机</b>：通行证在服务端<b>确认开门成功那一刻</b>才扣 ⇒ 中途 ESC 关掉 GUI 不会白扔一张 ✓。
 * <b>黑名单</b>：狱门疆维度 / 铁魔法口袋维度里用不了通行证，也不能把门开到那两个维度去。
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

        // §661 传送黑名单：狱门疆 / 铁魔法口袋维度里不许用通行证
        // （否则被封印在狱门疆里的囚徒直接开门越狱 ✗ —— 整座牢笼就白做了）
        if (WhiteSpaceDimensions.isBlacklisted(serverLevel.dimension())) {
            serverPlayer.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.white_space.blacklisted",
                    WhiteSpaceDimensions.displayName(serverLevel.dimension())), true);
            return InteractionResult.CONSUME;
        }

        /*
         * §661 两种模式都走 GUI（用户口径「在非伟大空间维度使用通行证的时候，同样打开 gui」）：
         *   · 在伟大白色空间里 ⇒ 自由模式：下拉选维度（只列"去过且不在黑名单"的）＋ 填 xyz；
         *   · 在其他维度       ⇒ 锁定模式：维度锁死为伟大白色空间、不能填 y（只有 x/z 可改，
         *                        默认就是门自己的 xz ⇒ 相对落点自动记录 ✓）。
         * 模式由服务端按"玩家现在站在哪个维度"决定并下发，C2S 那边还会按同一个规则再判一次 ✓
         * ⇒ 客户端改包也没法在别的维度强开"自由模式"。
         */
        boolean inWhiteSpace = WhiteSpaceDimensions.isWhiteSpace(level);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                new PacketOpenDimensionPassScreen(
                        inWhiteSpace ? WhiteSpaceDimensions.unlockedDimensionIds(serverPlayer) : List.of(),
                        pos,
                        inWhiteSpace ? null : WhiteSpaceDimensions.WHITE_SPACE.location().toString(),
                        WhiteSpaceDimensions.GROUND_Y));
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
