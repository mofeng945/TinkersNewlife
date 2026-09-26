package com.mofengbaizhi.tinkersnewlife.integration.vampirism;

import com.mofengbaizhi.tinkersnewlife.mixin.BloodStatsInvoker;
import de.teamlapen.vampirism.api.VampirismAPI;
import de.teamlapen.vampirism.api.entity.factions.IFaction;
import de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler;
import de.teamlapen.vampirism.api.entity.factions.IPlayableFaction;
import de.teamlapen.vampirism.api.entity.player.vampire.IBloodStats;
import de.teamlapen.vampirism.api.entity.player.vampire.IVampirePlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * 与「吸血鬼／血族传说」（Vampirism）的联动桥：**血族等级判定** + **血液值读写**。
 *
 * <p>⚠ 加载纪律（与 build.gradle 里 Goety / Mekanism / AE2 / Create 同一套口径 ✗）：
 * 本类里到处都是 Vampirism 的类型 ⇒ **只有在 {@code ModList.isLoaded("vampirism")} 判断通过之后**
 * 才允许被碰到 ✗。调用方（{@code content/modifier/events/} 下的事件处理器）必须先判 ModList，
 * 没装血族的环境里本类不会被 JVM 加载，也就不会有 NoClassDefFoundError ✓。
 *
 * <p>⚠ 血液值只能"走后门"写入：{@code BloodStats#addBlood(int, float)} 是**包级私有**的
 * （{@code IVampirePlayer#getBloodStats()} 返回的 {@code IBloodStats} 只有 getter ✗），
 * 所以本模组用 {@link BloodStatsInvoker} 这个 Mixin {@code @Invoker} 把它暴露出来 ✓
 * （不装血族时该 Mixin 静默不生效，{@code instanceof} 判失败 ⇒ 直接放弃写入，不崩 ✗）。
 */
public final class VampireIntegration {

    /** 血族模组 id */
    public static final String MOD_ID = "vampirism";
    /** 血族阵营 id（{@code vampirism:vampire}） */
    private static final ResourceLocation VAMPIRE_FACTION_ID = new ResourceLocation(MOD_ID, "vampire");
    /** 本联动两条强化的生效门槛：**5 级以上的血族**（用户口径 ✓） */
    public static final int REQUIRED_LEVEL = 5;

    private VampireIntegration() {
    }

    /** 血族模组是否在场（唯一允许在"没碰过血族类"的上下文里调用的方法 ✓） */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 玩家是不是「5 级以上的血族」。
     * <p>未装血族 / 不是血族阵营 / 等级不够 ⇒ 一律 false（不抛异常 ✓）。
     */
    public static boolean isHighRankVampire(Player player) {
        if (!isLoaded() || player == null) {
            return false;
        }
        IFactionPlayerHandler handler = VampirismAPI.getFactionPlayerHandler(player).resolve().orElse(null);
        if (handler == null) {
            return false;
        }
        IFaction<?> faction = VampirismAPI.factionRegistry().getFactionByID(VAMPIRE_FACTION_ID);
        if (!(faction instanceof IPlayableFaction<?> playable)) {
            return false;
        }
        return handler.isInFaction(playable) && handler.getCurrentLevel(playable) >= REQUIRED_LEVEL;
    }

    /** 血族等级（不是血族 / 读不到 ⇒ -1） */
    public static int getVampireLevel(Player player) {
        if (!isLoaded() || player == null) {
            return -1;
        }
        IFactionPlayerHandler handler = VampirismAPI.getFactionPlayerHandler(player).resolve().orElse(null);
        IFaction<?> faction = VampirismAPI.factionRegistry().getFactionByID(VAMPIRE_FACTION_ID);
        if (handler == null || !(faction instanceof IPlayableFaction<?> playable) || !handler.isInFaction(playable)) {
            return -1;
        }
        return handler.getCurrentLevel(playable);
    }

    /** 当前血液值（读不到 ⇒ -1） */
    public static int getBlood(Player player) {
        IBloodStats stats = bloodStats(player);
        return stats == null ? -1 : stats.getBloodLevel();
    }

    /** 血液上限（读不到 ⇒ -1） */
    public static int getMaxBlood(Player player) {
        IBloodStats stats = bloodStats(player);
        return stats == null ? -1 : stats.getMaxBlood();
    }

    /**
     * 加血液值：与"吃东西"同形 —— {@code amount} 取食物的**营养值**，
     * {@code saturationMultiplier} 取食物的**饱和度系数**（血族内部的饱和度也按原版食物那套算法走 ✓）。
     *
     * @return 是否真的写进去了（没装血族 / 不是血族 / Mixin 没生效 ⇒ false）
     */
    public static boolean addBlood(Player player, int amount, float saturationMultiplier) {
        if (!isLoaded() || player == null || amount <= 0) {
            return false;
        }
        IBloodStats stats = bloodStats(player);
        if (!(stats instanceof BloodStatsInvoker invoker)) {
            return false;
        }
        invoker.tinkersnewlife$addBlood(amount, saturationMultiplier);
        return true;
    }

    /** 取 {@code IBloodStats}（没装血族 / 不是血族 ⇒ null） */
    private static IBloodStats bloodStats(Player player) {
        if (!isLoaded() || player == null) {
            return null;
        }
        IVampirePlayer vampire = VampirismAPI.getVampirePlayer(player).resolve().orElse(null);
        return vampire == null ? null : vampire.getBloodStats();
    }
}
