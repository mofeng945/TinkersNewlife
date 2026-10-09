package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ⭐ §1173 <b>启动到主菜单时自动跑一次匠魂材质生成器，并强制启用生成的资源包</b>
 * （用户口径 ✓ 2026-10-09 四条 ✓）：
 * <ol>
 *   <li>⭐ 「**启动游戏后自动跑一次匠魂生成器生成所有 miss 材质**」✓；</li>
 *   <li>⭐ 「**然后自动应用资源包**」✓（⭐ 并明确「**资源包强制启用**」✓）；</li>
 *   <li>⭐ 「**这个功能添加到配置文件中可以自由开关，默认开启**」✓
 *       （⭐ 配置项 {@code auto_part_textures.enable_auto_part_textures} ✓ 默认 `true` ✓）；</li>
 *   <li>⭐ 「**如果某一项缺失纹理或其他原因生成失败那就跳过**」✓。</li>
 * </ol>
 *
 * <h2>⭐ 为什么"主菜单"就能跑（⭐ 反编译实证 ✓ 不是我猜的 ✗）</h2>
 * ⭐ 匠魂那个入口 ⭐ `ClientGeneratePartTexturesCommand.generateTextures(Operation, String, String)`
 * ⭐ **是 `public static`** ✓ ⭐ 而且它**只用 `Minecraft.getInstance()`** ✗：
 * ⭐ `getResourceManager()` ✓（⭐ 主菜单也有 ✓ 客户端资源已加载 ✓）、
 * ⭐ `getGameDirectory()` ✓、⭐ `player` 还做了 **null 保护** ✓
 * ⇒ ⭐ **完全不需要世界** ✓ ✓（⚠ 我先前只查了 `MaterialPartTextureGenerator` ✗
 * 那个确实是 datagen 期的 ✓ ⭐ 但这条正路可以直接调 ✓）。
 *
 * <h2>⭐ 模式取 {@code MISSING} ✓（⭐ 正好是用户说的"所有 miss 材质" ✓）</h2>
 * ⭐ `Operation` 只有两个值：⭐ `ALL` ／ ⭐ `MISSING` ✓
 * ⇒ ⭐ 用 `MISSING` ⇒ ⭐ **只补缺的** ✓（⭐ 已有的不动 ⇒ 也快得多 ✓）。
 *
 * <h2>⚠⚠ 第 4 条「失败要跳过」只能做到"整轮不崩"（⚠ 如实交代 ✗）</h2>
 * ⭐ 反编译看到 ⭐ 匠魂内部 ⭐ **只有一个外层 `try/catch`** ✗ ——
 * ⭐ 材质循环里 ⭐ **没有逐项 try** ✗ ⇒ ⚠ **一项失败（比如某材料没有纹理 ✓）会中止整轮** ✓
 *（⭐ 源码里就有 `throw error.create("Unable to create generator for material … as it has no texture")` ✓）。
 * ⇒ ⭐ 我们**改不了它的内部** ✗ ⇒ ⭐ 只能：
 *   ① ⭐ 外面包 `try/catch` ⇒ ⭐ **至少不崩游戏** ✓ ⭐ 失败写日志 ✓；
 *   ② ⭐ 提供 ⭐ **按材料逐个调用** 的能力 ✗（⭐ 那个 API 支持 `modId`／`materialPath` 过滤 ✓）
 *      —— ⚠ 但要先能枚举出所有材料 ✗ ⭐ 那是**另一轮**的活 ✓（⭐ 已在备忘录里记为待办 ✓）。
 *
 * <h2>⚠ 强制启用资源包（⭐ 用户明确要求 ✓）</h2>
 * ⭐ 匠魂把包写到 ⭐ `<游戏目录>/TinkersConstructGeneratedPartTextures/` ✓
 * ⇒ ⭐ 这里把它 ⭐ **加进 `options.resourcePacks` 的第 0 位** ✗（⭐ 强制置顶 ✓ 按用户口径 ✓）
 * ⭐ 并从 ⭐ `incompatibleResourcePacks` 移除 ✓ ⭐ 然后 ⭐ **重载资源** ✓。
 * ⚠ 这会**改玩家的资源包列表** ✗ —— ⭐ 用户已明确要求"强制启用" ✓ ⭐ 且可在配置里整体关掉 ✓。
 *
 * <h2>⚠ 只跑一次</h2>
 * ⭐ 生成器**写盘很重** ✗ ⇒ ⭐ 每个客户端实例**只跑一次** ✓
 * （⚠ 重载资源会再触发一次 `ScreenEvent.Init` ✗ ⭐ 所以必须有这个哨兵 ✓ 否则会**死循环** ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AutoPartTexturesHandler {

    private AutoPartTexturesHandler() {
    }

    /** ⭐ 生成器产出的资源包名（⭐ 与匠魂源码里的常量**一致** ✓ 抄自反编译 ✓） */
    private static final String PACK_NAME = "TinkersConstructGeneratedPartTextures";

    /** ⚠ **只跑一次** ✗（⭐ 重载资源会再次触发本事件 ✓ 没它就会死循环 ✓） */
    private static boolean done = false;

    /**
     * ⭐ 触发点：⭐ **主菜单出现** ✗（用户口径 ✓「主菜单触发」✓）。
     * <p>⚠ 用 `ScreenEvent.Init.Post` ✗ 而不是 `ClientTickEvent` ✓ ——
     * ⭐ 前者**只在界面初始化时**触发一次 ✓ 后者每 tick 都跑 ✓（⭐ 还得自己判重 ✓）。
     */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (done || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        done = true;
        // ⭐ 配置开关（用户第 3 条 ✓ 默认 true ✓）
        try {
            if (!ModConfig.AUTO_PART_TEXTURES.get()) {
                return;
            }
        } catch (Throwable ignored) {
            // ⚠ 读不到配置就当作关闭 ✗（⭐ 宁可不动 ✓ 不要擅自改资源包 ✓）
            return;
        }
        // ⚠ 生成器写盘 + 重载资源**都很重** ✗ ⇒ ⭐ 延到本 tick 结束后再做 ✓
        //   （⭐ 直接在 `ScreenEvent` 里重载资源会把正在初始化的界面搞乱 ✓）
        Minecraft.getInstance().tell(AutoPartTexturesHandler::run);
    }

    /** ⭐ 真正干活 ✓（⭐ 每一段都各自 try ✗ 一处失败不影响后面的 ✓） */
    private static void run() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(PACK_NAME);

        // ⓪ ⭐⭐ **生成前先数一遍已有贴图**（用户口径 ✓ 2026-10-09：
        //    「**加一条：如果生成条目为0就不做任何处理**」✓）
        //    ⚠ `generateTextures` **返回 void** ✗ ⭐ 不给条数 ✓ ⇒ ⭐ 只能自己数 ✓
        //    ⚠⚠ **不能只看"目录里有没有文件"** ✗ —— ⭐ 上次跑过的那些**还在** ✓
        //    ⇒ ⭐ 必须 ⭐ **前后对比差值** ✗ ⇒ ⭐ 差为 0 才是"这次一条都没生成" ✓ ✓。
        int before = countTextures(dir);

        // ① ⭐ 跑生成器（⭐ 只补缺失的 ✓ 用户第 1 条 ✓）
        try {
            slimeknights.tconstruct.shared.client.ClientGeneratePartTexturesCommand.generateTextures(
                    slimeknights.tconstruct.shared.network.GeneratePartTexturesPacket.Operation.MISSING,
                    "", "");
            TinkersNewlife.LOGGER.info("[自动材质] 已跑一次匠魂材质生成器（模式 MISSING ⇒ 只补缺失的 ✓）");
        } catch (Throwable t) {
            // ⚠ 匠魂内部只有一个外层 try ✗ ⇒ ⭐ 一项失败就会整轮中止 ✓
            //   ⇒ ⭐ 我们**至少保证不崩游戏** ✓ ⭐ 并把原因写进日志 ✓（⭐ 用户第 4 条 ✓）
            TinkersNewlife.LOGGER.warn("[自动材质] 生成器报错（⚠ 匠魂内部不逐项容错，一处失败会中止整轮）：{}",
                    t.toString());
        }

        // ①.5 ⭐⭐ **生成了 0 条 ⇒ 什么都不做**（用户口径 ✓）
        int after = countTextures(dir);
        int delta = after - before;
        if (delta <= 0) {
            TinkersNewlife.LOGGER.info(
                    "[自动材质] 这次生成 {} 条 ⇒ **不做任何处理**（不碰资源包 ✓）现有总数 {}",
                    Math.max(0, delta), after);
            return;
        }
        TinkersNewlife.LOGGER.info("[自动材质] 这次生成 {} 条 ⇒ 继续启用资源包 ✓（现有总数 {}）", delta, after);

        // ② ⭐ 强制启用生成的资源包（⭐ 用户第 2 条 ✓「强制启用」✓）
        try {
            forceEnablePack();
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[自动材质] 启用资源包失败：{}", t.toString());
        }
    }

    /**
     * ⭐ 数一数生成目录下有多少张 png ✓（⭐ 递归 ✓ —— 匠魂是分目录写的 ✓）。
     * <p>⚠ 目录不存在（⭐ 还没生成过 ✓）⇒ ⭐ 返回 0 ✓（⭐ 不抛异常 ✓）。
     */
    private static int countTextures(Path dir) {
        try {
            if (!Files.isDirectory(dir)) {
                return 0;
            }
            try (var stream = Files.walk(dir)) {
                return (int) stream.filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT)
                        .endsWith(".png")).count();
            }
        } catch (Throwable t) {
            // ⚠ 数不出来 ⇒ ⭐ 返回 -1 ✓ ⇒ ⭐ 后面 `delta = after - before` 会 ≤ 0
            //   ⇒ ⭐ **按"没生成"处理** ✓（⭐ 宁可不动资源包 ✓）
            return -1;
        }
    }

    /**
     * ⭐ <b>把生成的包强制启用并置顶</b> ✓（用户口径「资源包强制启用」✓）。
     * <p>⚠ 只动 ⭐ **我们这一个包** ✗ —— ⭐ 玩家其它包的顺序**不动** ✓。
     * ⚠ 包目录不存在（⭐ 生成失败时 ✓）⇒ ⭐ **不加**（⭐ 免得列表里出现一个空包 ✓）。
     */
    private static void forceEnablePack() {
        Minecraft mc = Minecraft.getInstance();
        Path dir = mc.gameDirectory.toPath().resolve(PACK_NAME);
        if (!Files.isDirectory(dir)) {
            TinkersNewlife.LOGGER.info("[自动材质] 没有生成目录 {} ⇒ 不加资源包（生成大概是失败或无需生成）", dir);
            return;
        }
        List<String> packs = new ArrayList<>(mc.options.resourcePacks);
        boolean changed = false;
        if (!packs.contains(PACK_NAME)) {
            packs.add(0, PACK_NAME);      // ⭐ 置顶 ⇒ 强制生效 ✓
            changed = true;
        } else if (packs.indexOf(PACK_NAME) != 0) {
            packs.remove(PACK_NAME);
            packs.add(0, PACK_NAME);      // ⭐ 已在列表但不在首位 ⇒ 提到首位 ✓
            changed = true;
        }
        // ⚠ 它可能被标记成"不兼容"✗ ⇒ ⭐ 必须从那里移除 ✓ 否则不生效 ✓
        if (mc.options.incompatibleResourcePacks.remove(PACK_NAME)) {
            changed = true;
        }
        if (!changed) {
            TinkersNewlife.LOGGER.info("[自动材质] 资源包 {} 已经启用且置顶 ⇒ 无需改动 ✓", PACK_NAME);
            return;
        }
        mc.options.save();
        mc.reloadResourcePacks();
        TinkersNewlife.LOGGER.info("[自动材质] 已强制启用并置顶资源包 {} 并重载资源 ✓", PACK_NAME);
    }
}
