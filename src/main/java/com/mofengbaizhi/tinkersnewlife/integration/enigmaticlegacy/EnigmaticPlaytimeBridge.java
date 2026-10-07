package com.mofengbaizhi.tinkersnewlife.integration.enigmaticlegacy;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/**
 * ⭐ §1118y <b>受七咒时长桥</b>（本包是唯一允许放该模组专属知识的地方 ✓）。
 *
 * <h2>⭐⭐ 关键：读原版统计**必须先把它注册进 `CUSTOM_STAT`**（反编译实证 ✓）</h2>
 * 原版 {@code Stat} 的构造器会这样拼名字 ✓：
 * <pre>
 * public static &lt;T&gt; String buildName(StatType&lt;T&gt; type, T value) {
 *     return name(BuiltInRegistries.STAT_TYPE.getKey(type)) + ":"
 *          + name(type.getRegistry().getKey(value));       // ⭐ 这里
 * }
 * private static &lt;T&gt; String name(@Nullable ResourceLocation loc) {
 *     return loc.toString().replace(':', '.');             // ☠️ loc 为 null ⇒ NPE
 * }
 * </pre>
 * ⇒ ⚠ 若那个 {@code ResourceLocation} **没有注册进 `BuiltInRegistries.CUSTOM_STAT`** ✗
 * ⇒ {@code getKey(value)} 返回 **null** ✗ ⇒ **`Stats.CUSTOM.get(id)` 必抛 NPE** ✗
 * （⭐ 这正是实测日志里那 64 条 NPE 的原因 ✓）。
 * <p>⇒ ⭐ **本类在 `RegisterEvent` 里把神秘遗物那两个自定义统计 id 注册进去** ✓
 * ⇒ 之后既可以用**原版统计**读 ✓（用户口径 ✓「**原版统计有指令可以改 tick ✓ 便于调试**」✓
 * —— 注册后就能用 {@code /scoreboard objectives add … minecraft.custom:enigmaticlegacy.play_time_with_seven_curses}
 * 然后 {@code players set} 直接改数值 ✓）✓。
 *
 * <h2>⭐ 双保险（都用 ✓）</h2>
 * <ol>
 *   <li><b>主</b>：原版统计 ✓（注册成功后可用 ✓ ⇒ 用户能用指令调试 ✓）；</li>
 *   <li><b>备</b>：本模组**自计**的计数器 ✓（每 tick ＋1 ✓ 见 {@link #tick}）——
 *       ⚠ 原版统计读不到时（未注册/异常 ✓）自动用它 ✓ ⇒ **不会再出现"读不到就误收装备"** ✗；</li>
 *   <li><b>"此刻是否戴着"</b>用 Curios 直接问 ✓（与 EL 的 {@code hasCurio} 同一 API ✓，实测可靠 ✓）。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EnigmaticPlaytimeBridge {

    private EnigmaticPlaytimeBridge() {
    }

    /** 神秘遗物·受七咒时长（原版自定义统计 id ✓） */
    private static final ResourceLocation STAT_WITH =
            new ResourceLocation("enigmaticlegacy", "play_time_with_seven_curses");
    /** 神秘遗物·未受咒时长 ✓ */
    private static final ResourceLocation STAT_WITHOUT =
            new ResourceLocation("enigmaticlegacy", "play_time_without_seven_curses");

    /** 自计兜底：在线总 tick ✓ / 其中戴着戒指的 tick ✓ */
    private static final String KEY_TOTAL = "tn_curse_total";
    private static final String KEY_CURSED = "tn_curse_cursed";

    /** 神秘遗物·七咒之戒 ✓ */
    private static final String CURSED_RING = "enigmaticlegacy:cursed_ring";

    /** 注册是否成功 ✓（失败 ⇒ 一直用自计 ✓ 不再抛异常 ✓） */
    private static volatile boolean statsRegistered = false;

    /**
     * ⭐ 把神秘遗物那两个自定义统计 id 注册进 {@code CUSTOM_STAT} ✓
     * —— ⚠ **不注册就 `Stats.CUSTOM.get(id)` 必抛 NPE** ✗（原因见类注释 ✓）。
     */
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        try {
            if (!event.getRegistryKey().equals(BuiltInRegistries.CUSTOM_STAT.key())) {
                return;
            }
            event.register(BuiltInRegistries.CUSTOM_STAT.key(), helper -> {
                Registry.register(BuiltInRegistries.CUSTOM_STAT, STAT_WITH, STAT_WITH);
                Registry.register(BuiltInRegistries.CUSTOM_STAT, STAT_WITHOUT, STAT_WITHOUT);
            });
            statsRegistered = true;
            TinkersNewlife.LOGGER.info("[七咒] 已注册神秘遗物的两条自定义统计 id ⇒ 可用原版统计（指令也能改）✓");
        } catch (Throwable thrown) {
            statsRegistered = false;
            TinkersNewlife.LOGGER.warn("[七咒] 注册自定义统计 id 失败 ⇒ 改用自计兜底 ✓", thrown);
        }
    }

    /** 戒指**此刻**是否戴在饰品栏 ✓（与 EL 的 hasCurio 同一 Curios API ✓） */
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

    /** 每个服务端玩家 tick 调一次 ✓（自计兜底 ✓ ⚠ 必须在任何节流之前 ✗） */
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

    /** 原版统计读一个数 ✓ 读不到返回 -1 ✓（⚠ 必须先注册 ✓ 见 {@link #onRegister}） */
    private static long readStat(ServerPlayer player, ResourceLocation id) {
        if (!statsRegistered) {
            return -1L;
        }
        try {
            return player.getStats().getValue(Stats.CUSTOM.get(id));
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    /** 戴着七咒之戒的时长 ✓：**原版统计优先** ✓ 读不到用自计 ✓ */
    public static long withCurses(ServerPlayer player) {
        long v = readStat(player, STAT_WITH);
        return v >= 0L ? v : player.getPersistentData().getLong(KEY_CURSED);
    }

    /** 没戴的时长 ✓：原版统计优先 ✓ 读不到用自计 ✓ */
    public static long withoutCurses(ServerPlayer player) {
        long v = readStat(player, STAT_WITHOUT);
        if (v >= 0L) {
            return v;
        }
        long total = player.getPersistentData().getLong(KEY_TOTAL);
        return Math.max(0L, total - player.getPersistentData().getLong(KEY_CURSED));
    }

    /** 「受七咒时间 ÷ 在世界上时间」✓ 0.0~1.0 ✓ */
    public static double curseRatio(ServerPlayer player) {
        long with = withCurses(player);
        long without = withoutCurses(player);
        long total = with + without;
        return total <= 0L ? 0.0D : (double) with / (double) total;
    }

    /**
     * 是否**合格** ✓ ＝ <b>此刻戴着七咒之戒</b> ✓ <b>且</b> 受咒比例 ≥ {@code percent} ✓
     * （⚠ 与 EL 的 {@code isTheWorthyOne} 同口径 ✓）。
     * <p>⚠ 宽限 ✓：两条数据都还没有（total=0 ✓ 全新/刚升级 ✓）⇒ **戴着就先算合格** ✓
     * （否则所有人的计数器都从 0 起 ✗ ⇒ 会被误判成不合格而**误收装备** ✗✗）。
     */
    public static boolean meetsRatio(ServerPlayer player, int percent) {
        if (!isWearingRing(player)) {
            return false;
        }
        long with = withCurses(player);
        long without = withoutCurses(player);
        long total = with + without;
        if (total <= 0L) {
            return true;   // 宽限 ✓
        }
        return with * 100L >= total * (long) percent;
    }
}
