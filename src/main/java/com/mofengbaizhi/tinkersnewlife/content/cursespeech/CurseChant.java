package com.mofengbaizhi.tinkersnewlife.content.cursespeech;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 通用吟唱读条支持（服务端）：
 * <p>
 * 把「构筑术式拟造读条」提炼为可复用的读条状态：开始（记录结束时刻+总时长）、
 * 查询剩余、结束清理；读条期间移动速度减半。状态存玩家持久数据，
 * 由调用方在自己的每 tick 驱动里推进（到期执行完成逻辑）。
 * 客户端进度渲染见 {@code client/hud/ChannelBarRenderer}。
 */
public final class CurseChant {

    private CurseChant() {}

    /** 读条结束时刻（gameTime） */
    public static final String KEY_END = "tnl_chant_end";
    /** 读条总时长（tick） */
    public static final String KEY_TOTAL = "tnl_chant_total";
    /** 读条备注/标题文本（可选，用于进度条显示） */
    public static final String KEY_LABEL = "tnl_chant_label";
    /** 吟唱减速属性修饰符 UUID */
    private static final UUID CHANT_SLOW_UUID = UUID.fromString("8a4b0c2d-3e5f-4a6b-9c7d-1e2f3a4b5c6d");

    /** 是否正在读条 */
    public static boolean isChanting(ServerPlayer player) {
        return player.getPersistentData().contains(KEY_END);
    }

    /** 剩余 tick（<=0 表示已到期） */
    public static long remaining(ServerPlayer player) {
        long end = player.getPersistentData().getLong(KEY_END);
        return end - player.serverLevel().getGameTime();
    }

    /** 开始读条（立即减速） */
    public static void start(ServerPlayer player, int totalTicks, String label) {
        var data = player.getPersistentData();
        data.putLong(KEY_END, player.serverLevel().getGameTime() + totalTicks);
        data.putLong(KEY_TOTAL, totalTicks);
        if (label != null && !label.isEmpty()) {
            data.putString(KEY_LABEL, label);
        }
        applySlow(player, true);
    }

    /** 结束清理（恢复速度）；返回是否确有在读条 */
    public static boolean clear(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.contains(KEY_END)) return false;
        data.remove(KEY_END);
        data.remove(KEY_TOTAL);
        data.remove(KEY_LABEL);
        applySlow(player, false);
        return true;
    }

    /** 读条标题（无则空串） */
    public static String label(ServerPlayer player) {
        return player.getPersistentData().getString(KEY_LABEL);
    }

    /** 读条期间移动速度减半（enable=true 施加，false 移除） */
    public static void applySlow(ServerPlayer player, boolean enable) {
        AttributeInstance attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;
        AttributeModifier mod = new AttributeModifier(CHANT_SLOW_UUID,
                "curse_chant_slow", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
        if (enable) {
            if (!attr.hasModifier(mod)) {
                attr.addTransientModifier(mod);
            }
        } else if (attr.hasModifier(mod)) {
            attr.removeModifier(mod);
        }
    }
}
