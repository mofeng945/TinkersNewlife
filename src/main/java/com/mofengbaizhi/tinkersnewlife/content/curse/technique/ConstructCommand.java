package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mojang.brigadier.CommandDispatcher;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 构筑术式管理指令：
 * <ul>
 *   <li>{@code /tinkersnewlife construct lootsuggest} —— 扫描实体掉落表与结构战利品表，
 *       生成 {@code loot_suggestions.toml}（建议）与 {@code loot_index.json}（缓存）。
 *       默认<b>不会</b>自动改价，需要人工合并或打开 {@code loot_source_auto_apply}。</li>
 *   <li>{@code /tinkersnewlife construct lootstatus} —— 查看当前索引是否已载入、条目数。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConstructCommand {

    private ConstructCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tinkersnewlife")
                .then(Commands.literal("construct")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("lootsuggest")
                                .executes(ctx -> lootSuggest(ctx.getSource())))
                        .then(Commands.literal("lootstatus")
                                .executes(ctx -> lootStatus(ctx.getSource())))));
    }

    private static int lootSuggest(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("该命令需要以玩家身份执行（需要所在维度来构造掉落上下文）"));
            return 0;
        }
        if (ConstructLootIndex.isRunning()) {
            source.sendSuccess(() -> Component.literal("扫描已在进行中，请等待完成（进度见日志）"), false);
            return 0;
        }
        boolean started = ConstructLootIndex.start(source.getServer(), player);
        if (started) {
            source.sendSuccess(() -> Component.literal(
                    "开始扫描掉落/战利品来源：每张表模拟 "
                            + ConstructLootIndex.SCAN_ROLLS + " 次，分帧进行，完成后会在聊天栏与日志提示。\n"
                            + "结果写入 config/mofengbaizhi/construct/（建议文件 + 缓存索引，默认不自动改价）"), true);
        } else {
            source.sendFailure(Component.literal("扫描启动失败"));
        }
        return started ? 1 : 0;
    }

    private static int lootStatus(CommandSourceStack source) {
        ConstructLootIndex.loadCachedIndex();
        int total = ConstructLootIndex.index().size();
        long eligible = ConstructLootIndex.index().values().stream()
                .filter(ConstructLootIndex.Entry::autoEligible).count();
        boolean auto = ConstructLootIndex.autoApply();
        source.sendSuccess(() -> Component.literal(
                "掉落来源索引：" + total + " 个物品（其中可自动应用 " + eligible + " 个）；"
                        + "自动应用 = " + (auto ? "开" : "关")), false);
        return 1;
    }
}
