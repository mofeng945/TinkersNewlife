package com.mofengbaizhi.tinkersnewlife.content.curse.technique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.entity.BlackBirdEntity;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdCamera;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * 术式「黑鸟操术」：
 * <p>
 * 发动：在玩家位置生成一只黑鸟（蝙蝠），玩家视角转移到黑鸟身上，玩家本体留在原地（隐形/无敌/暂停移动）。
 * 操控：W 朝视线飞 / A/D 侧移 / 空格上升；Shift 俯冲自爆。
 * 再次按释放键：若黑鸟存活，回收（返还一半咒力）并恢复玩家。
 * 黑鸟死亡（自爆/被杀）：视角回归，玩家恢复。
 * <p>
 * 咒力消耗 = (1 + (输出 + 亲和/10)/10) × 40；蝙蝠血量 = (1 + (输出 + 亲和/10)/10) × 原版蝙蝠血量(6)。
 * 自爆中心伤害 = (1 + 亲和/100) × (输出×3 + 蝙蝠血量) × 10，半径 2 格，不破坏方块。
 */
public final class BlackBirdTechnique extends BaseTechnique {

    public static final BlackBirdTechnique INSTANCE = new BlackBirdTechnique();

    private BlackBirdTechnique() {
        super(Modifiers.BLACK_BIRD.getId());
    }

    /** 该玩家当前存活的黑鸟（场上唯一） */
    public static BlackBirdEntity findActiveBird(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<BlackBirdEntity> birds = level.getEntitiesOfClass(BlackBirdEntity.class,
                player.getBoundingBox().inflate(512.0), e -> e.isAlive());
        for (BlackBirdEntity b : birds) {
            if (b.getOwner() == player) return b;
        }
        return null;
    }

    /** 封印瞬间终止黑鸟操控（雅各布天梯命中）：黑鸟消失、视角回归 */
    public static void sealRecall(ServerPlayer player) {
        BlackBirdEntity bird = findActiveBird(player);
        if (bird != null) {
            bird.finish(false);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.black_bird.returned"), true);
        }
    }

    @Override
    public void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        BlackBirdEntity existing = findActiveBird(player);
        if (existing != null) {
            // 已激活：回收（返还一半咒力，蝙蝠消失，视野回归）
            existing.recall(player);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.black_bird.recall"), true);
            return;
        }
        // 咒力消耗 = (1 + (输出 + 亲和/10)/10) × 40
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int cost = (int) Math.ceil((1.0 + (output + affinity / 10.0) / 10.0) * 40.0);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        // 生成黑鸟
        ServerLevel level = player.serverLevel();
        BlackBirdEntity bird = new BlackBirdEntity(ModEntities.BLACK_BIRD.get(), level);
        bird.moveTo(player.getX(), player.getY() + 1.0, player.getZ(), player.getYRot(), player.getXRot());
        // 蝙蝠血量 = (1 + (输出 + 亲和/10)/10) × 6
        double hpMult = 1.0 + (output + affinity / 10.0) / 10.0;
        bird.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                .setBaseValue(6.0 * hpMult);
        bird.setHealth((float) (6.0 * hpMult));
        bird.setOwner(player);
        level.addFreshEntity(bird);
        // 玩家身体留在原地：暂停移动（不隐身、非无敌，身体可见且可被攻击，死亡则视角回归）
        player.setNoGravity(true);
        // 视角转移到黑鸟
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketBlackBirdCamera(bird.getId(), true));
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.black_bird.summon"), true);
    }
}
