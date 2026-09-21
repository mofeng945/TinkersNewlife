package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 墨默**好感度**调试指令（用户口径：「加一个指令可以直接设置墨默的好感度」✓）。
 *
 * <pre>
 * /tinkersnewlife momo favor get [玩家]          —— 看当前好感度 + 对应价格倍率（改自己不用权限 ✓）
 * /tinkersnewlife momo favor set &lt;值&gt; [玩家]     —— 直接设成某个值（自动夹到 −50 ~ +50 ✓）
 * /tinkersnewlife momo favor add &lt;增量&gt; [玩家]   —— 加减（负数扣好感 ✓）
 * </pre>
 *
 * 改自己不需要权限 ✓；**改别人要权限 2**（OP ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MomoCommand {

    private MomoCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tinkersnewlife")
                .then(Commands.literal("momo")
                        .then(Commands.literal("favor")
                                // §507 用户口径「所有指令都需要权限」⇒ 整支 `momo favor` 一律要权限 2（OP）✓
                                //   （原来"改自己不用权限"是留给单机自测的后门 ✗ 现在**堵上** ✓）
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("get")
                                        .executes(ctx -> show(ctx.getSource(), self(ctx.getSource())))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .requires(src -> src.hasPermission(2))
                                                .executes(ctx -> show(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "target")))))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(MomoFavor.MIN, MomoFavor.MAX))
                                                .executes(ctx -> set(ctx.getSource(), self(ctx.getSource()),
                                                        IntegerArgumentType.getInteger(ctx, "value")))
                                                .then(Commands.argument("target", EntityArgument.player())
                                                        .requires(src -> src.hasPermission(2))
                                                        .executes(ctx -> set(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "target"),
                                                                IntegerArgumentType.getInteger(ctx, "value"))))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("delta", IntegerArgumentType.integer(-100, 100))
                                                .executes(ctx -> add(ctx.getSource(), self(ctx.getSource()),
                                                        IntegerArgumentType.getInteger(ctx, "delta")))
                                                .then(Commands.argument("target", EntityArgument.player())
                                                        .requires(src -> src.hasPermission(2))
                                                        .executes(ctx -> add(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "target"),
                                                                IntegerArgumentType.getInteger(ctx, "delta")))))))));
    }

    private static ServerPlayer self(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    private static String describe(int favor) {
        double f = MomoFavor.priceFactor(favor);
        String mood;
        if (favor >= 50) mood = "满好感";
        else if (favor >= 40) mood = "很高";
        else if (favor >= 20) mood = "不错";
        else if (favor >= 10) mood = "还行";
        else if (favor >= 0) mood = "普通";
        else if (favor >= -20) mood = "冷淡（不能对话 / 不能雇佣）";
        else mood = "敌视（不能对话 / 不能雇佣）";
        return favor + "（×" + String.format(java.util.Locale.ROOT, "%.2f", f) + "，" + mood + "）";
    }

    private static int show(CommandSourceStack source, ServerPlayer player) {
        int v = MomoFavor.get(player);
        source.sendSuccess(() -> Component.literal("§b[墨默] §f" + player.getScoreboardName() + " 的好感度：§e" + describe(v)), false);
        return v;
    }

    private static int set(CommandSourceStack source, ServerPlayer player, int value) {
        int before = MomoFavor.get(player);
        MomoFavor.set(player, value);
        int after = MomoFavor.get(player);
        source.sendSuccess(() -> Component.literal("§b[墨默] §f" + player.getScoreboardName() + " 的好感度：§7"
                + before + " §f→ §e" + describe(after)), true);
        return after;
    }

    private static int add(CommandSourceStack source, ServerPlayer player, int delta) {
        int before = MomoFavor.get(player);
        MomoFavor.add(player, delta);
        int after = MomoFavor.get(player);
        source.sendSuccess(() -> Component.literal("§b[墨默] §f" + player.getScoreboardName() + " 的好感度：§7"
                + before + " §f→ §e" + describe(after) + " §7(" + (delta >= 0 ? "+" : "") + delta + ")"), true);
        return after;
    }
}
