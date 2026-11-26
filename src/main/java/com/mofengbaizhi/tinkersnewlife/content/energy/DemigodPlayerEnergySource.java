package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * <b>来源 ⑤「半神之力」：只要台座旁边有人站着就充</b>（用户口径 §545 ✓）。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li><b>范围 5 格</b>（球半径 ✓ 与其它条一致 ✓）；</li>
 *   <li><b>每名</b>范围内的玩家 <b>0.5 EE/秒</b> ✓ <b>多人叠加</b> ✓（3 人 = 1.5 EE/秒 ✓）；</li>
 *   <li><b>无代价</b> ✓ —— 不扣血、不扣饱食度、不加负面效果、玩家自己也<b>感受不到</b> ✓
 *       （世界观里的"半神之力"是被动外溢 ✓ 用户明确"无代价"✓）；</li>
 *   <li>不设人数上限 ✓（用户口径"不设总上限" ✓）。</li>
 * </ul>
 *
 * <h2>为什么计入"玩家"要单独一个来源</h2>
 * 因为它在语义上与{@link SoulDeathEnergySource}相反：那条要<b>有人死</b>才给，
 * 这条只要<b>有人活着</b>就给 ✓ —— 两者可以同时生效 ✓（玩家在台座旁战死的那一刻：
 * 死亡那条给 0.5 EE，活人那条照旧按人头给 ✓ 不冲突 ✓）。
 *
 * <p>实现是纯只读扫描（{@code getEntitiesOfClass} 一次）✓ 无副作用 ✓
 * ⇒ {@code simulate} 对它没有影响 ✓。
 */
public final class DemigodPlayerEnergySource implements AmbientEnergySource {

    /** 配置允许清单里写的 id */
    public static final String ID = "demigod_player";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String shortName() {
        return "半神之力（玩家）";
    }

    @Override
    public String summary() {
        return "sphere r=5; every player inside gives player_ee_per_second EE (no cost, players stack)";
    }

    @Override
    public double eePerSecond(Level level, BlockPos pos, boolean simulate) {
        if (!(level instanceof ServerLevel server) || pos == null) return 0.0D;

        int radius = ModConfig.playerRadius();
        double perPlayer = ModConfig.playerEePerSecond();
        if (radius < 0 || perPlayer <= 0.0D) return 0.0D;

        // AABB 是"立方体"，再用平方距离筛成球 ✓（AABB 只用来少遍历实体，不用来定范围 ✗）
        double box = radius + 1.0D;
        List<Player> players = server.getEntitiesOfClass(Player.class,
                new net.minecraft.world.phys.AABB(
                        pos.getX() + 0.5D - box, pos.getY() + 0.5D - box, pos.getZ() + 0.5D - box,
                        pos.getX() + 0.5D + box, pos.getY() + 0.5D + box, pos.getZ() + 0.5D + box),
                player -> true);

        double maxDistanceSqr = (double) radius * (double) radius;
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;

        int found = 0;
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player.isSpectator()) continue;   // 旁观者不算"人" ✓（他连方块都碰不到 ✓）
            double dx = player.getX() - centerX;
            double dy = player.getY() - centerY;
            double dz = player.getZ() - centerZ;
            if (dx * dx + dy * dy + dz * dz <= maxDistanceSqr) found++;
        }

        return found == 0 ? 0.0D : perPlayer * found;
    }
}
