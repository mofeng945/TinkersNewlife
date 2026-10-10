package com.mofengbaizhi.tinkersnewlife.imagination;

import net.minecraftforge.fml.common.Mod;

/**
 * ⭐⭐⭐⭐ §1268 <b>匠魂新生·奇想 的 @Mod 入口</b> ✗✗
 *
 * <p>⚠ 为什么**必须**有 ✗：⭐ mods.toml 里写的是 ⭐ modLoader="javafml" ✓
 * ⭐ 而这种包 ⭐ **必须有一个 @Mod 类** ✓ ⇒ ⭐ 三个新包原来一个都没有 ✓
 * ⇒ ⭐ 这就是 §1267「⭐ 大爆炸版一加载就断 ✗ ⭐ 日志停在 Found 25 dependencies… 之后一行都没有」的
 * ⭐ **头号嫌疑** ✓ ✓。
 *
 * <p>⭐ 本类**不做任何注册** ✗ —— ⭐ 所有 DeferredRegister 仍由 common 侧各 hub 的静态块
 * 挂到 **mod 总线**上 ✓（⭐ 命名空间仍是 	inkersnewlife ✗ ⭐ 用户拍板保留 ✓）
 * ⇒ ⭐ 三个新包运行时提供的只是**额外的类** ✓（⭐ 所以它们与 common **必须同时存在** ✓）。
 *
 * <p>⚠ 本包自己的 @SubscribeEvent 由 ⭐ @Mod.EventBusSubscriber 自动挂 ✓ ⭐ 不用手写 ✓。
 */
@Mod(TinkersNewlifeImagination.MOD_ID)
public final class TinkersNewlifeImagination {

    /** ⭐ 本包的 mod id ✓（⭐ 必须与 mods.toml 的 [[mods]] modId 一字不差 ✓） */
    public static final String MOD_ID = "tinkersnewlife_imagination";

    public TinkersNewlifeImagination() {
        com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info("[匠魂新生·奇想] 已加载 ✓");
    }
}