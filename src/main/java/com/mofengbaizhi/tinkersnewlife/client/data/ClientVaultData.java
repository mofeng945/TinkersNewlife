package com.mofengbaizhi.tinkersnewlife.client.data;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketQueryCurseVault;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端：呪蔵存量缓存（十字光标提示用）。
 *
 * <p>真值在服务端 world data 里，客户端看不到 —— 所以这里按需询问：
 * 准星对准某个呪蔵时，如果缓存超过 {@link #REFRESH_TICKS} 未更新就发一次查询，
 * 服务端回包写进缓存，HUD 直接读缓存。这样既不用方块实体广播，也不会每帧发包。
 */
public final class ClientVaultData {

    /** 缓存有效期（tick）：约 0.5 秒刷新一次 */
    private static final long REFRESH_TICKS = 10;

    /** 坐标 → {存量, 上次查询的游戏时间} */
    private static final Map<BlockPos, double[]> CACHE = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_QUERY = new HashMap<>();

    private ClientVaultData() {
    }

    /** 收到服务端回包：写入缓存 */
    public static void update(BlockPos pos, double power) {
        CACHE.put(pos.immutable(), new double[]{power, net.minecraft.client.Minecraft.getInstance().level == null
                ? 0 : net.minecraft.client.Minecraft.getInstance().level.getGameTime()});
    }

    /** 缓存中该呪蔵的存量；没有缓存返回 {@code null}（HUD 会显示"…"） */
    public static Double get(BlockPos pos) {
        double[] entry = CACHE.get(pos);
        return entry == null ? null : entry[0];
    }

    /**
     * 准星对准某呪蔵时调用：缓存过期就发一次查询（在客户端 tick 里按游戏时间节流）。
     */
    public static void requestIfStale(BlockPos pos) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        Long last = LAST_QUERY.get(pos);
        if (last != null && now - last < REFRESH_TICKS) return;
        LAST_QUERY.put(pos.immutable(), now);
        TinkersNewlife.CHANNEL.sendToServer(new PacketQueryCurseVault(pos));
    }

    /** 退出世界/换维度时清缓存 */
    public static void clear() {
        CACHE.clear();
        LAST_QUERY.clear();
    }
}
