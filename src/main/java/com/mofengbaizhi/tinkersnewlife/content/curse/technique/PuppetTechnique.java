package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetGolemMob;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetUtil;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketBlackBirdCamera;
import com.mofengbaizhi.tinkersnewlife.network.curse.PacketOpenPuppetScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;

/**
 * 术式「傀儡操术」：像黑鸟操术一样的"召唤物 + 视角转移"术式，召唤物为铁傀儡/雪傀儡。
 * <p>
 * 费用：M = 1 + (输出 + 亲和/10)/10；铁傀儡 ceil(M×60)、雪傀儡 ceil(M×35)，召唤时一次性扣除，维持不耗咒力；
 * 雪球与击飞 0 耗；召回返还 30%。
 * 按术式键：有傀儡在场 → 召回；无 → 打开选择界面（GUI 渲染铁/雪傀儡实体）选定召唤。
 * 自爆后重召冷却 max(200, 1200 - 输出×40) tick。
 */
public final class PuppetTechnique extends BaseTechnique {

    public static final PuppetTechnique INSTANCE = new PuppetTechnique();

    private static final String CD_TAG = "tinkersnewlife.puppet_cd_until";

    private PuppetTechnique() {
        super(Modifiers.PUPPET.getId());
    }

    /** 该玩家当前在场的傀儡（场上唯一），无则 null */
    public static PuppetGolemMob findActivePuppet(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<PuppetIronGolem> irons = level.getEntitiesOfClass(PuppetIronGolem.class,
                player.getBoundingBox().inflate(512.0), e -> e.isAlive());
        for (PuppetIronGolem p : irons) {
            if (p.getOwner() == player) return p;
        }
        List<PuppetSnowGolem> snows = level.getEntitiesOfClass(PuppetSnowGolem.class,
                player.getBoundingBox().inflate(512.0), e -> e.isAlive());
        for (PuppetSnowGolem p : snows) {
            if (p.getOwner() == player) return p;
        }
        return null;
    }

    /** 计算当前 M 倍率 */
    private static double mult(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        return 1.0 + (output + affinity / 10.0) / 10.0;
    }

    public static int ironCost(ServerPlayer player) {
        return (int) Math.ceil(mult(player) * 60.0);
    }

    public static int snowCost(ServerPlayer player) {
        return (int) Math.ceil(mult(player) * 35.0);
    }

    @Override
    public void onKeyPress(ServerPlayer player) {
        PuppetGolemMob active = findActivePuppet(player);
        if (active != null) {
            // 已有傀儡：召回（返还 30%）
            recall(player, active);
            return;
        }
        // 无傀儡：打开选择界面（服务端算好两种消耗）
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenPuppetScreen(ironCost(player), snowCost(player)));
    }

    /** 召回：返还本次召唤实付咒力的 30% + 傀儡消散 + 视角回归 */
    public static void recall(ServerPlayer player, PuppetGolemMob puppet) {
        int refund = Math.max(1, (int) Math.ceil(puppet.puppetPaidCost() * 0.3));
        CursePowerHelper.addCurse(player, refund);
        puppet.puppetFinish(false);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.puppet.recall"), true);
    }

    /** 选择界面选定后召唤（kind 0=铁 1=雪），服务端校验：无在场傀儡、自爆冷却、咒力足够 */
    public static void summon(ServerPlayer player, int kind) {
        if (findActivePuppet(player) != null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.puppet.active"), true);
            return;
        }
        // 自爆重召冷却
        long until = player.getPersistentData().getLong(CD_TAG);
        long now = player.serverLevel().getGameTime();
        if (until > now) {
            int remainSec = (int) Math.ceil((until - now) / 20.0);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.puppet.cd", remainSec), true);
            return;
        }
        int cost = kind == 0 ? ironCost(player) : snowCost(player);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity puppet;
        if (kind == 0) {
            PuppetIronGolem iron = new PuppetIronGolem(ModEntities.PUPPET_IRON_GOLEM.get(), level);
            puppet = iron;
        } else {
            PuppetSnowGolem snow = new PuppetSnowGolem(ModEntities.PUPPET_SNOW_GOLEM.get(), level);
            puppet = snow;
        }
        // 面前 1.5 格生成，自动找安全高度
        Vec3 dir = PuppetUtil.flatDir(player.getYRot());
        double px = player.getX() + dir.x * 1.5;
        double pz = player.getZ() + dir.z * 1.5;
        double py = safeSpawnY(level, px, player.getY(), pz, puppet);
        puppet.moveTo(px, py, pz, player.getYRot(), 0);
        ((PuppetGolemMob) puppet).puppetBindOwner(player, cost);
        level.addFreshEntity(puppet);
        // 本体定身 + 视角转移（复用黑鸟相机包）
        player.setNoGravity(true);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketBlackBirdCamera(puppet.getId(), true));
        player.displayClientMessage(Component.translatable(
                kind == 0 ? "message.tinkersnewlife.puppet.summon_iron" : "message.tinkersnewlife.puppet.summon_snow"),
                true);
    }

    /** 自爆后设置重召冷却（由傀儡实体在自爆时调用） */
    public static void onPuppetSelfDestruct(ServerPlayer player) {
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int cdTicks = Math.max(200, 1200 - output * 40);
        player.getPersistentData().putLong(CD_TAG, player.serverLevel().getGameTime() + cdTicks);
    }

    /** 登出/死亡清理：正在场上的傀儡立即消散、视角回归（若主人仍在线） */
    public static void cleanup(ServerPlayer player) {
        PuppetGolemMob puppet = findActivePuppet(player);
        if (puppet != null) {
            puppet.puppetFinish(false);
        }
    }

    private static double safeSpawnY(ServerLevel level, double x, double y, double z, Entity e) {
        for (int i = 0; i < 4; i++) {
            double cy = y + i;
            AABB bb = e.getBoundingBox().move(x - e.getX(), cy - e.getY(), z - e.getZ());
            if (level.noCollision(e, bb)) {
                return cy;
            }
        }
        return y;
    }
}
