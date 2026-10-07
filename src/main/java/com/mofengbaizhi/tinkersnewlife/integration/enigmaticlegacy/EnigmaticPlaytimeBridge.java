package com.mofengbaizhi.tinkersnewlife.integration.enigmaticlegacy;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ⭐ §1118y <b>受七咒时长桥</b>（本包是唯一允许放该模组专属知识的地方 ✓）。
 *
 * <h2>⚠⚠ 为什么不用"原版统计"了（**实测崩溃** ✗）</h2>
 * 我早先想"尽量用原版"✓ ⇒ 改成读神秘遗物同步过去的**原版统计**：
 * {@code player.getStats().getValue(Stats.CUSTOM.get(new ResourceLocation("enigmaticlegacy","play_time_with_seven_curses")))}
 * ⇒ ⚠ **实测直接抛 NPE** ✗（服务器日志实证 ✓）：
 * <pre>
 * java.lang.NullPointerException: Cannot invoke "ResourceLocation.toString()" because "p_12866_" is null
 *     at net.minecraft.stats.Stat.&lt;init&gt;(Stat.java:16)
 *     at net.minecraft.stats.StatType.get(…)
 *     at …EnigmaticPlaytimeBridge.read(EnigmaticPlaytimeBridge.java:51)
 * </pre>
 * ⚠ 而**神秘遗物真正的数据是 capability `IPlaytimeCounter`** ✓（用 `long` ✓ 不经过原版统计 ✓）
 * —— 但本仓 `build.gradle` **没有该模组的编译依赖** ✗（其 jar 在 mods 里是 SRG 名 ✓ 不能直接编译 ✗），
 * 本仓铁律又**禁止 `Class.forName` 反射试探** ✗ ⇒ ⭐ **那条路走不通** ✗。
 *
 * <h2>⭐ 现在的做法（可靠 ✓ 且有实证 ✓）</h2>
 * <ol>
 *   <li><b>自己数</b> ✓：每 tick 给玩家持久数据里的两个计数器 ＋1 ✓
 *       （{@link #KEY_TOTAL} 在线 ✓ / {@link #KEY_CURSED} 受咒 ✓），
 *       只有**戒指戴在饰品栏**那一刻才算受咒 ✓（判据与神秘遗物一致 ✓ 见 {@link #isWearingRing} ✓）；</li>
 *   <li><b>"此刻是否戴着"用 Curios 直接问</b> ✓（与 EL 的 {@code SuperpositionHandler.hasCurio} 同一个 API ✓
 *       {@code CuriosApi.getCuriosHelper().findEquippedCurio(...)} ✓）—— ⚠ 实测日志里这一项为 **true** ✓ 可靠 ✓；</li>
 *   <li>合格 ✓ ＝ <b>此刻戴着</b> ✓ <b>且</b> 受咒比例 ≥ 门槛 ✓（与 EL 的 {@code isTheWorthyOne} 口径一致 ✓）。</li>
 * </ol>
 * ⚠ 这样**不再依赖任何会抛异常的原版统计** ✗，也不违反"不反射"的铁律 ✓。
 */
public final class EnigmaticPlaytimeBridge {

    private EnigmaticPlaytimeBridge() {
    }

    /** 在线总 tick ✓ */
    private static final String KEY_TOTAL = "tn_curse_total";
    /** 其中"戴着七咒之戒"的 tick ✓ */
    private static final String KEY_CURSED = "tn_curse_cursed";

    /** 神秘遗物·七咒之戒 ✓（与 {@code CursedRingTooltipHandler} 同口径 ✓） */
    private static final String CURSED_RING = "enigmaticlegacy:cursed_ring";

    /** 戒指**此刻**是否戴在饰品栏 ✓（⚠ 与 EL 的 hasCurio 用同一个 Curios API ✓） */
    public static boolean isWearingRing(net.minecraft.world.entity.player.Player player) {
        if (player == null) {
            return false;
        }
        try {
            var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(CURSED_RING));
            if (item == null) {
                return false;
            }
            return top.theillusivec4.curios.api.CuriosApi.getCuriosHelper()
                    .findEquippedCurio(item, player).isPresent();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * ⭐ 每个玩家 tick 调一次 ✓：记"在线"与"受咒"两个计数 ✓。
     * <p>⚠ 必须由调用方**只对服务端玩家**调用 ✓（持久数据在服务端为准 ✓）。
     */
    public static void tick(ServerPlayer player) {
        try {
            CompoundTag data = player.getPersistentData();
            boolean cursed = isWearingRing(player);
            data.putLong(KEY_TOTAL, data.getLong(KEY_TOTAL) + 1L);
            if (cursed) {
                data.putLong(KEY_CURSED, data.getLong(KEY_CURSED) + 1L);
            }
        } catch (Throwable ignored) {
            // 计数失败绝不干扰游戏 ✓
        }
    }

    /** 戴着七咒之戒的时长（tick ✓ 自计 ✓） */
    public static long withCurses(ServerPlayer player) {
        try {
            return player.getPersistentData().getLong(KEY_CURSED);
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /** 没戴的时长（tick ✓ 自计 ✓） */
    public static long withoutCurses(ServerPlayer player) {
        try {
            long total = player.getPersistentData().getLong(KEY_TOTAL);
            return Math.max(0L, total - player.getPersistentData().getLong(KEY_CURSED));
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /**
     * 「受七咒时间 ÷ 在世界上时间」✓ 0.0~1.0 ✓。
     * <p>⚠ 分母为 0（全新玩家 ✓）⇒ 返回 **0.0** ✓（＝不合格 ✓）。
     */
    public static double curseRatio(ServerPlayer player) {
        long with = withCurses(player);
        long without = withoutCurses(player);
        long total = with + without;
        if (total <= 0L) {
            return 0.0D;
        }
        return (double) with / (double) total;
    }

    /**
     * 是否**合格** ✓ ＝ <b>此刻戴着七咒之戒</b> ✓ <b>且</b> 受咒比例 ≥ {@code percent} ✓
     * （⚠ 与 EL 的 {@code isTheWorthyOne} 同一口径 ✓：只算时长不够 ✗ 必须**当前戴着** ✓）。
     */
    public static boolean meetsRatio(ServerPlayer player, int percent) {
        if (!isWearingRing(player)) {
            return false;   // ⚠ 没戴着 ⇒ 不合格 ✓（与 EL 的 isTheCursedOne 前提一致 ✓）
        }
        long with = withCurses(player);
        long without = withoutCurses(player);
        long total = with + without;
        if (total <= 0L) {
            // ⚠ 宽限 ✓：本模组刚开始计数（老玩家升级上来时 total 为 0 ✓）
            // ⇒ 只要**此刻戴着**就先算合格 ✓ 并从这一刻开始累计 ✓
            //（否则所有人的计数器都从 0 起 ✗ ⇒ 会被误判成不合格而**误收装备** ✗✗）
            return true;
        }
        return with * 100L >= total * (long) percent;
    }
}
