package com.mofengbaizhi.tinkersnewlife.content.conscience;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 「心」（善恶系统）仅在配置 {@code conscience.enabled} 与世界游戏规则都开启时生效。
 * <pre>
 *   /gamerule doConscienceHeart true     开启（默认）：自动佩戴「心」，善恶规则生效
 *   /gamerule doConscienceHeart false    关闭：不再佩戴「心」（已有的收掉），善恶按 0 处理
 * </pre>
 *
 * <h2>注册时机（关键）</h2>
 * {@code GameRules} 的实例构造时是把**静态**的 {@code GAME_RULE_TYPES} 整表拷一份，
 * 所以必须在**任何世界被创建之前**把自定义规则注册进静态表 ✓
 * ⇒ 在 {@code TinkersNewlife} 构造里调用 {@link #init()}（模组构造早于服务端/世界创建 ✓）。
 *
 * <h2>读取</h2>
 * 每次实时读服务端的 gamerule（`/gamerule` 改完立刻生效，不用重启、不用同步缓存 ✓）；
 * 取不到服务端（纯客户端）或取不到规则时**保守返回 true**（= 保持旧默认开启 ✓ 不静默关系统）。
 */
public final class ConscienceState {

    private ConscienceState() {
    }

    /** 自定义游戏规则名（玩家看到的命令是 {@code /gamerule doConscienceHeart ...}） */
    public static final String RULE_NAME = "doConscienceHeart";

    /** 注册后拿到的 Key；null = 注册失败（此时按默认开启处理） */
    private static GameRules.Key<GameRules.BooleanValue> ruleKey;

    /**
     * 注册自定义 gamerule（模组构造时调用一次；重复调用安全）。
     * ⚠ 必须在任何 {@code GameRules} 实例创建之前调用，否则那一个实例里没有这条规则。
     */
    public static void init() {
        if (ruleKey != null) {
            return;
        }
        try {
            ruleKey = GameRules.register(RULE_NAME, GameRules.Category.MISC,
                    GameRules.BooleanValue.create(true));
        } catch (Throwable ignored) {
            // 注册失败（例如极端情况下表已被冻结）⇒ 开关退化为"默认开启"，不影响其余功能
        }
    }

    /** 全局配置为总闸；开启后再由各世界的游戏规则决定。 */
    public static boolean isEnabled() {
        if (ModConfig.SPEC.isLoaded() && !ModConfig.CONSCIENCE_ENABLED.get()) {
            return false;
        }
        try {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server == null || ruleKey == null) {
                return true;
            }
            return server.overworld().getGameRules().getBoolean(ruleKey);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
