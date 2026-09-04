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

    /** 同队豁免：目标 == 主人、被主人雇佣的墨默、主人召唤的式神、主人的傀儡/自爆幻翼 → true（傀儡伤害全部豁免） */
    public static boolean isAllyOf(LivingEntity target, ServerPlayer owner) {
        if (target == null || owner == null) return false;
        if (target == owner) return true;
        UUID ownerId = owner.getUUID();
        if (target instanceof MomoMerchant momo && momo.isHired()) {
            ServerPlayer boss = momo.getEmployer();
            if (boss != null && boss.getUUID().equals(ownerId)) return true;
        }
        if (target instanceof ShikigamiMob shikigami) {
            UUID oid = shikigami.getOwnerId();
            if (oid != null && oid.equals(ownerId)) return true;
        }
        if (target instanceof FlamePhantom flame) {
            ServerPlayer boss = flame.getOwner();
            if (boss != null && boss.getUUID().equals(ownerId)) return true;
        }
        return false;
    }
}
