package com.mofengbaizhi.tinkersnewlife.content.cursespeech;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 咒言术调试/管理指令：
 * <ul>
 *   <li>{@code /tinkersnewlife cursespeech learnall [玩家]} —— 学会全部咒言词条</li>
 *   <li>{@code /tinkersnewlife cursespeech scroll [玩家]} —— 发一张全词条残卷（供查看）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CursedSpeechCommand {

    private CursedSpeechCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tinkersnewlife")
                .then(Commands.literal("cursespeech")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("learnall")
                                .executes(ctx -> learnAll(ctx.getSource(), selfOrFail(ctx.getSource())))
                                .then(Commands.argument("target",
                                        net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> learnAll(ctx.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("scroll")
                                .executes(ctx -> giveScroll(ctx.getSource(), selfOrFail(ctx.getSource())))
                                .then(Commands.argument("target",
                                        net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> giveScroll(ctx.getSource(),
                                                net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "target")))))));
    }

    private static ServerPlayer selfOrFail(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static int learnAll(CommandSourceStack source, ServerPlayer player) {
        int learned = 0;
        for (CursedSpeechRegistry.Word w : CursedSpeechRegistry.all()) {
            if (CursedSpeechState.learn(player, w.id())) {
                learned++;
            }
        }
        // 用当前最高稀有度词条重新铺默认组合（全学后各段取稀有度最高的那个，便于体验）
        String[] defaults = {
                bestOf(CursedSpeechRegistry.Part.EXCLAMATION),
                bestOf(CursedSpeechRegistry.Part.HONORIFIC),
                "wuzu", // 对象固定咒之祖巫做演示（其余对象可自选）
                bestOf(CursedSpeechRegistry.Part.PRAYER),
                bestOf(CursedSpeechRegistry.Part.CORE),
                bestOf(CursedSpeechRegistry.Part.THANKS)
        };
        for (int i = 0; i < defaults.length; i++) {
            if (defaults[i] != null) {
                CursedSpeechState.setChantPart(player, i, defaults[i]);
            }
        }
        final int newly = learned;
        source.sendSuccess(() -> Component.literal("§d[咒言术] 已为 " + player.getName().getString()
                + " 学会全部 " + CursedSpeechRegistry.all().size() + " 个词条（本次新增 " + newly + " 个），并按最高稀有度铺好了默认咒言"), false);
        return learned;
    }

    private static String bestOf(CursedSpeechRegistry.Part part) {
        String best = null;
        int bestRarity = -1;
        for (CursedSpeechRegistry.Word w : CursedSpeechRegistry.of(part)) {
            if (w.rarity() > bestRarity) {
                bestRarity = w.rarity();
                best = w.id();
            }
        }
        return best;
    }

    private static int giveScroll(CommandSourceStack source, ServerPlayer player) {
        var stack = com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem.roll();
        // 塞入全部词条便于查看 tooltip
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (CursedSpeechRegistry.Word w : CursedSpeechRegistry.all()) {
            list.add(net.minecraft.nbt.StringTag.valueOf(w.id()));
        }
        stack.getOrCreateTag().put(com.mofengbaizhi.tinkersnewlife.content.item.AncientCursedScrollItem.KEY_WORDS, list);
        boolean added = player.getInventory().add(stack);
        if (!added) {
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    player.serverLevel(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            drop.setPickUpDelay(0);
            player.serverLevel().addFreshEntity(drop);
        }
        source.sendSuccess(() -> Component.literal("§d[咒言术] 已给 " + player.getName().getString()
                + " 一张含全部词条的残卷"), false);
        return 1;
    }
}
