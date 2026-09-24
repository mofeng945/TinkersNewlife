package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.advancements.Advancement;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 成就调试指令（模组 20 个成就 = {@code data/tinkersnewlife/advancements/achievements/*}）。
 *
 * <pre>
 * /tinkersnewlife achievement list [玩家]                 —— 列出全部成就 + 该玩家拿到/没拿到
 * /tinkersnewlife achievement grant &lt;成就|all&gt; [玩家]     —— 发放（all = 全部 20 个）
 * /tinkersnewlife achievement revoke &lt;成就|all&gt; [玩家]    —— 撤销（测"重新拿一次"用）
 * </pre>
 *
 * <p>成就名用**路径**（不用打命名空间 ✓）且带 Tab 补全 ✓：{@code root} / {@code curse_core} /
 * {@code all_techniques} / {@code alignment_evil} … 或关键字 {@code all} ✓。
 * 补全列表是**从数据包实际加载的进度里现取** ✓ ⇒ 以后加了新成就**自动出现** ✓（不用改指令 ✗）。
 *
 * <p>⚠ 权限：整支都要**权限 2（OP）** ✓（§507 用户口径「所有指令都需要权限」✓）。
 * <p>⚠ `revoke` 只删进度本身 ✓ **也会**顺便清掉 {@code AchievementHandler} 的"本进程已发过"缓存 ✓
 * （否则撤销后再满足条件就发不出来了 ✗）；但它**不会**回退玩家的咒力/物品/善恶等真实状态 ✗
 * —— 那些是各自系统的事 ✓ 想复位得用各自的指令 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AchievementCommand {

    /** 成就目录（`advancements/achievements/`） */
    private static final String DIR = "achievements/";

    /** 关键字 {@code all} = 全部成就 */
    private static final String ALL = "all";

    private AchievementCommand() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("tinkersnewlife")
                .then(Commands.literal("achievement")
                        .requires(src -> src.hasPermission(2))
                        // ---- list ----
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> list(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target")))))
                        // ---- grant ----
                        .then(Commands.literal("grant")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                suggestNames(ctx.getSource()), b))
                                        .executes(ctx -> apply(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"), true))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> apply(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "name"), true)))))
                        // ---- revoke ----
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                suggestNames(ctx.getSource()), b))
                                        .executes(ctx -> apply(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(ctx, "name"), false))
                                        .then(Commands.argument("target", EntityArgument.player())
                                                .executes(ctx -> apply(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "target"),
                                                        StringArgumentType.getString(ctx, "name"), false)))))));
    }

    // ============================================================
    //  实现
    // ============================================================

    /** 目录下全部成就的**路径名**（从数据包实际加载的进度里筛 ✓ 不写死清单 ✗） */
    private static List<String> allNames(CommandSourceStack src) {
        List<String> out = new ArrayList<>();
        ServerAdvancementManager manager = src.getServer().getAdvancements();
        for (Advancement a : manager.getAllAdvancements()) {
            ResourceLocation id = a.getId();
            if (!TinkersNewlife.MOD_ID.equals(id.getNamespace())) continue;
            String path = id.getPath();
            if (path.startsWith(DIR)) out.add(path.substring(DIR.length()));
        }
        out.sort(String::compareTo);
        return out;
    }

    /** 补全用（现取 + 关键字 all ✓；拿不到 server 就只给 all ✓ 补全不该因为没世界而报错 ✗） */
    private static List<String> suggestNames(CommandSourceStack src) {
        try {
            List<String> out = new ArrayList<>(allNames(src));
            out.add(ALL);
            return out;
        } catch (Throwable t) {
            List<String> out = new ArrayList<>();
            out.add(ALL);
            return out;
        }
    }

    private static int list(CommandSourceStack src, ServerPlayer target) {
        List<String> names = allNames(src);
        int done = 0;
        src.sendSuccess(() -> Component.literal("§6[成就] " + target.getName().getString()
                + " —— 共 " + names.size() + " 个"), false);
        for (String name : names) {
            Advancement holder = holder(src, name);
            boolean has = holder != null && target.getAdvancements().getOrStartProgress(holder).isDone();
            if (has) done++;
            src.sendSuccess(() -> Component.literal((has ? "  §a✔ " : "  §7✘ ") + name), false);
        }
        final int d = done;
        src.sendSuccess(() -> Component.literal("§6[成就] 已拿到 §e" + d + "§6 / " + names.size()), false);
        return 1;
    }

    private static int apply(CommandSourceStack src, ServerPlayer target, String name, boolean grant) {
        List<String> targets;
        if (ALL.equalsIgnoreCase(name)) {
            targets = allNames(src);
            if (targets.isEmpty()) {
                src.sendFailure(Component.literal("§c[成就] 没有找到任何成就（数据包没加载？）"));
                return 0;
            }
        } else {
            if (holder(src, name) == null) {
                src.sendFailure(Component.literal(
                        "§c[成就] 找不到成就：" + name + "（用 /tinkersnewlife achievement list 看全部）"));
                return 0;
            }
            targets = List.of(name);
        }
        int ok = 0;
        for (String n : targets) {
            Advancement holder = holder(src, n);
            if (holder == null) continue;
            boolean done = target.getAdvancements().getOrStartProgress(holder).isDone();
            if (grant && !done) {
                // ⚠ 全部成就的第一条 criteria 都叫 "unlock" ✓（见 advancements/achievements/*.json ✓）
                //    照项目既有发放写法（TechniqueAdvancementHandler ✓）只调 award 一句 ✓
                //    toast / 聊天提示由原版自己处理 ✓ 不需要额外 flush ✗
                target.getAdvancements().award(holder, "unlock");
                ok++;
            } else if (!grant && done) {
                target.getAdvancements().revoke(holder, "unlock");
                // ⚠ 同时清掉 AchievementHandler 的"本进程已发过"缓存 ✓
                //    否则撤销后再满足条件会被它短路 ⇒ 再也发不出来 ✗（测试"重新拿一次"就会卡住 ✓）
                com.mofengbaizhi.tinkersnewlife.content.curse.AchievementHandler.forgetAwarded(target, n);
                ok++;
            }
        }
        final int cnt = ok;
        final String who = target.getName().getString();
        if (grant) {
            src.sendSuccess(() -> Component.literal("§6[成就] 已给 §e" + who + "§6 发放 §e" + cnt + "§6 个成就"
                    + (cnt == 0 ? "§7（本来就有 ✓）" : "")), true);
        } else {
            src.sendSuccess(() -> Component.literal("§6[成就] 已撤销 §e" + who + "§6 的 §e" + cnt + "§6 个成就"
                    + (cnt == 0 ? "§7（本来也没有 ✓）" : "")), true);
        }
        return 1;
    }

    private static Advancement holder(CommandSourceStack src, String name) {
        return src.getServer().getAdvancements()
                .getAdvancement(new ResourceLocation(TinkersNewlife.MOD_ID, DIR + name));
    }
}
