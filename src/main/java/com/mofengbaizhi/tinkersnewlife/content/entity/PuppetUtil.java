package com.mofengbaizhi.tinkersnewlife.content.entity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 傀儡操术 共享工具：
 * 朝向向量换算、同队（主人 / 墨默 / 己方式神）豁免判定。
 */
public final class PuppetUtil {

    private PuppetUtil() {}

    /** 水平朝向单位向量（与黑鸟视角公式一致）：yaw=0 朝 +Z，yaw 顺时针增大 */
    public static Vec3 flatDir(float yaw) {
        float rad = yaw * (float) Math.PI / 180F;
        return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
    }

    /** 带俯仰的视线单位向量（与黑鸟俯冲方向公式一致，用于雪球弹道） */
    public static Vec3 viewVec(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180F);
        float g = -yaw * ((float) Math.PI / 180F);
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    /**
     * 同队豁免：目标 == 主人、被主人雇佣的墨默、主人召唤的式神、主人的傀儡/自爆幻翼 → true
     * （傀儡伤害全部豁免）。
     *
     * <p>⭐<b>§832 用户口径</b>：「佩戴同心戒指时<b>同伴的随从也算自己的随从</b>」✓ ——
     * 所以这里把"主人"从**一个人**扩展成<b>{自己, 同心戒同伴}</b>两个人 ✓：
     * 归属判定逐个主人跑一遍 ✓（同伴的墨默 / 式神 / 傀儡 / 自爆幻翼 / 释放体全都算自己人 ✓）。
     * 飞剑索敌正是走 {@code FlyingSwordEntity#isOwnedBy → isAllyOf} ✓ ⇒ 同伴的随从不会再被自家飞剑追着打 ✓。
     * <p>⚠ 同伴必须**在线**才算得出来（同伴身份靠 {@code TwinRingLink.findPartner} 解析 ✓ 它只认在线玩家 ✓）——
     * 同伴下线时他留下的随从仍会被当成敌方 ✗（这是该 API 的固有限制 ✓ 如实说明 ✓）。
     */
    public static boolean isAllyOf(LivingEntity target, ServerPlayer owner) {
        if (target == null || owner == null) return false;
        if (target == owner) return true;
        // ⭐ 同心戒：戴着同一对戒指的两名玩家互为同伴 → 术式不该把他当目标（双向，谁看谁都一样）
        if (com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink.arePaired(target, owner)) return true;
        // ⭐§832：先按"自己"判 ✓ 再按"同伴"判 ✓（同伴的随从也算自己的随从 ✓）
        if (isAllyOfPlayer(target, owner)) return true;
        ServerPlayer partner =
                com.mofengbaizhi.tinkersnewlife.content.curse.TwinRingLink.findPartner(owner);
        return partner != null && partner != owner && isAllyOfPlayer(target, partner);
    }

    /** §832 单个"主人"的归属判定（＝ §639f 原来的全部逻辑 ✓ 抽出来给"同伴"复用 ✓） */
    private static boolean isAllyOfPlayer(LivingEntity target, ServerPlayer owner) {
        if (target == null || owner == null) return false;
        if (target == owner) return true;
        UUID ownerId = owner.getUUID();
        if (target instanceof MomoMerchant momo && momo.isHired()) {
            ServerPlayer boss = momo.getEmployer();
            if (boss != null && boss.getUUID().equals(ownerId)) return true;
        }
        if (target instanceof ShikigamiMob shikigami) {
            // 只有已调伏的式神才算队友；未调伏（调伏战中）是敌人，必须能被主人攻击
            UUID oid = shikigami.getOwnerId();
            if (oid != null && oid.equals(ownerId) && shikigami.isTamed()) return true;
        }
        if (target instanceof FlamePhantom flame) {
            ServerPlayer boss = flame.getOwner();
            if (boss != null && boss.getUUID().equals(ownerId)) return true;
        }
        if (com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique
                .isSpiritTeam(target, owner)) {
            return true;
        }
        return false;
    }
}
