package com.mofengbaizhi.tinkersnewlife.integration.enigmaticlegacy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.server.level.ServerPlayer;

/**
 * ⭐ §1118y <b>神秘遗物·受七咒时长桥</b>（用户口径 ✓「尽量用原版」✓）。
 *
 * <h2>⭐ 只用**原版统计 API**，一行神秘遗物的类都不 import ✓</h2>
 * 反编译神秘遗物 {@code com.aizistral.enigmaticlegacy.api.capabilities.PlayerPlaytimeCounter} 实证 ✓：
 * 它自己就把两个时长**同步进原版自定义统计** ✓ ——
 * <pre>
 * public static final ResourceLocation TIME_WITH_CURSES_STAT =
 *         new ResourceLocation("enigmaticlegacy", "play_time_with_seven_curses");
 * public static final ResourceLocation TIME_WITHOUT_CURSES_STAT =
 *         new ResourceLocation("enigmaticlegacy", "play_time_without_seven_curses");
 * ...
 * player.getStats().setValue(player, Stats.CUSTOM.get(stat), value);
 * </pre>
 * ⇒ 所以只要读这两条**原版统计**即可 ✓（语言键也印证 ✓：
 * {@code stat.enigmaticlegacy.play_time_with_seven_curses} ＝「游戏时间（佩戴七咒戒指时）」✓
 * {@code …without_seven_curses} ＝「游戏时间（没有佩戴七咒戒指）」✓）。
 *
 * <p>⚠ **为什么不直接用它的 capability** ✗：它有一个更精确的 {@code IPlaytimeCounter}
 * （返回 {@code long} ✓）✓，但本仓 {@code build.gradle} **没有**神秘遗物的编译依赖 ✗
 * （其他 15 个联动都有 ✓ 就它没有 ✓）⇒ 直接 import 其类会**编译不过** ✗；
 * 而本仓铁律也**禁止** {@code Class.forName} 反射试探 ✗。
 * ⇒ 走**原版统计**既满足"尽量用原版" ✓ 又零依赖风险 ✓ —— ⚠ 代价是统计值是 **int**（原版统计如此 ✓）
 * ⇒ 约 2^31 tick（≈3.4 年）后才可能溢出 ✓ 实际无影响 ✓。
 *
 * <p>⚠ 统计不到（没装神秘遗物 / 还没被统计过 ✓）⇒ **放行** ✓ ——
 * 宁可漏管 ✓ 也**绝不**因为读不到就误收玩家装备 ✗。
 */
public final class EnigmaticPlaytimeBridge {

    private EnigmaticPlaytimeBridge() {
    }

    /** 佩戴七咒之戒的时长（tick ✓ 原版自定义统计 ✓） */
    private static final ResourceLocation WITH_CURSES =
            new ResourceLocation("enigmaticlegacy", "play_time_with_seven_curses");

    /** 未佩戴七咒之戒的时长（tick ✓ 原版自定义统计 ✓） */
    private static final ResourceLocation WITHOUT_CURSES =
            new ResourceLocation("enigmaticlegacy", "play_time_without_seven_curses");

    private static long read(ServerPlayer player, ResourceLocation id) {
        try {
            // ⚠ 照仓库里已验证的写法（ExecutionDomain:375）✓ 中间不要多一层类型变量 ✗（会推断失败 ✓）
            return player.getStats().getValue(Stats.CUSTOM.get(id));
        } catch (Throwable thrown) {
            // ⚠ 读失败一定要**留痕** ✗ —— 否则"到底读没读到"从游戏里完全看不出来 ✓
            org.slf4j.LoggerFactory.getLogger("tinkersnewlife/playtime")
                    .warn("[七咒所缚] 读取神秘遗物统计 {} 失败 ⇒ 视为不合格（会拦截）", id, thrown);
            return -1L;
        }
    }

    /** 戴着七咒之戒的时长 ✓ 取不到 ⇒ -1 ✓ */
    public static long withCurses(ServerPlayer player) {
        return read(player, WITH_CURSES);
    }

    /** 没戴七咒之戒的时长 ✓ 取不到 ⇒ -1 ✓ */
    public static long withoutCurses(ServerPlayer player) {
        return read(player, WITHOUT_CURSES);
    }

    /**
     * 「受七咒时间 ÷ 在世界上时间」✓ 0.0~1.0 ✓。
     * <p>⚠ 分母为 0（还没统计数据 ✓）⇒ 返回 **0.0** ✓（＝不合格 ✓ 符合"还没受够咒"的直觉 ✓）。
     */
    public static double curseRatio(ServerPlayer player) {
        long with = withCurses(player);
        long without = withoutCurses(player);
        if (with < 0L || without < 0L) {
            return -1.0D;
        }
        long total = with + without;
        if (total <= 0L) {
            return 0.0D;
        }
        return (double) with / (double) total;
    }

    /** 是否**已受咒足够比例** ✓（整数比较 ✓ 照用户口径 ✓ 免得浮点误差 ✗） */
    public static boolean meetsRatio(ServerPlayer player, int percent) {
        long with = withCurses(player);
        long without = withoutCurses(player);
        if (with < 0L || without < 0L) {
            // ⚠⚠ 读不到 ⇒ **视为不合格**（＝拦截）✗ —— ⭐ 用户实测反馈："没戴七咒之戒也没把工具丢出去" ✓
            // 根因就是这里原先是"读不到 ⇒ 放行"✗ ⇒ 只要统计读失败 ⇒ 永远不拦截 ✗。
            // ⚠ 安全前提：调用方**先判 isLoaded(ENIGMATIC_LEGACY)** ✓ ⇒ 没装该模组时根本不会走到这里 ✓
            return false;
        }
        long total = with + without;
        if (total <= 0L) {
            return false;  // 还没统计数据 ⇒ 按"未受咒"处理 ✓
        }
        return with * 100L >= total * (long) percent;
    }
}
