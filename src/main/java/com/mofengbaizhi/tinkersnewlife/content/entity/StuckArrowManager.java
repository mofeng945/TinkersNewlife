package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.stuck.PacketStuckArrow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.network.PacketDistributor;

import java.util.UUID;

/**
 * TNL927 ★★ 强化箭命中后**扎在身上的黑箭** ✓
 *
 * <p>★用户口径 ✓：
 * <ul>
 *   <li>★生成在生物身上时有 **20 滴血** ✓（★我们用“十分之一点”计数 ✓：200 份 ✓）；</li>
 *   <li>★全程**无敌 + 免疫受击事件** ✓（★它不是实体 ✗ ——
 *       ★而是生物身上的状态 ✓，★所以根本无从打它 ✓）；</li>
 *   <li>★每 tick 自动流失 **0.1 血** ✓ ⇒ ★200 tick（★10 秒）自然消失 ✓；</li>
 *   <li>★扎着时移动速度 **-80%** ✓（★属性乘算 0.2 ✓）；</li>
 *   <li>★玩家可长按 G 2 秒拔除 ✓（★松手失败 ✓ ⇒ ★重来 ✓）；</li>
 *   <li>★**生物不会拔** ✓；★**墨默免疫此箭** ✓。</li>
 * </ul>
 */
public final class StuckArrowManager {

    /** ★全血 = 20.0 ⇒ ★以 0.1 为单位 = 200 份 ✓ */
    public static final int FULL = 200;

    private static final String KEY = "tinkersnewlife:stuck_arrow";
    private static final UUID SLOW_UUID = UUID.fromString("6a1f0c22-9d54-4c1b-9a3f-77b0c4d51e88");

    private StuckArrowManager() {
    }

    public static boolean has(LivingEntity e) {
        return e.getPersistentData().getInt(KEY) > 0;
    }

    /** ★被强化箭命中 ⇒ ★扎上一支 ✓ */
    public static void apply(LivingEntity e) {
        if (e.level().isClientSide) {
            return;
        }
        if (e instanceof KtuluMomo) {
            return;                          // ★墨默免疫 ✓
        }
        e.getPersistentData().putInt(KEY, FULL);
        applySlow(e);
        notify(e, true);
    }

    /** ★每 tick（★由 LivingTickEvent 驱动 ✓） */
    public static void tick(LivingEntity e) {
        if (e.level().isClientSide) {
            return;
        }
        int hp = e.getPersistentData().getInt(KEY);
        if (hp <= 0) {
            return;
        }
        hp -= 1;                             // ★ 0.1 血 / tick ✓
        e.getPersistentData().putInt(KEY, hp);
        if (hp <= 0) {
            clear(e);
        } else {
            applySlow(e);
        }
    }

    /** ★拔除（★长按成功 / ★血量耗尽 ✓） */
    public static void clear(LivingEntity e) {
        e.getPersistentData().putInt(KEY, 0);
        removeSlow(e);
        notify(e, false);
    }

    private static void applySlow(LivingEntity e) {
        var attr = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) {
            return;
        }
        if (attr.getModifier(SLOW_UUID) == null) {
            attr.addTransientModifier(new AttributeModifier(
                    SLOW_UUID, "tnl_stuck_arrow", -0.8D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    private static void removeSlow(LivingEntity e) {
        var attr = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr != null && attr.getModifier(SLOW_UUID) != null) {
            attr.removeModifier(SLOW_UUID);
        }
    }

    private static void notify(LivingEntity e, boolean present) {
        if (!(e.level() instanceof ServerLevel sl)) {
            return;
        }
        PacketStuckArrow pkt = new PacketStuckArrow(e.getUUID(), present);
        for (ServerPlayer sp : sl.players()) {
            if (sp.distanceToSqr(e) < 64.0D * 64.0D) {
                TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), pkt);
            }
        }
    }
}
