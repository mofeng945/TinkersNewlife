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
 * TNL927 \u2605\u2605 \u5f3a\u5316\u7bad\u547d\u4e2d\u540e**\u624e\u5728\u8eab\u4e0a\u7684\u9ed1\u7bad** \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff1a
 * <ul>
 *   <li>\u2605\u751f\u6210\u5728\u751f\u7269\u8eab\u4e0a\u65f6\u6709 **20 \u6ef4\u8840** \u2713\uff08\u2605\u6211\u4eec\u7528\u201c\u5341\u5206\u4e4b\u4e00\u70b9\u201d\u8ba1\u6570 \u2713\uff1a200 \u4efd \u2713\uff09\uff1b</li>
 *   <li>\u2605\u5168\u7a0b**\u65e0\u654c \u002b \u514d\u75ab\u53d7\u51fb\u4e8b\u4ef6** \u2713\uff08\u2605\u5b83\u4e0d\u662f\u5b9e\u4f53 \u2717 \u2014\u2014
 *       \u2605\u800c\u662f\u751f\u7269\u8eab\u4e0a\u7684\u72b6\u6001 \u2713\uff0c\u2605\u6240\u4ee5\u6839\u672c\u65e0\u4ece\u6253\u5b83 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u6bcf tick \u81ea\u52a8\u6d41\u5931 **0.1 \u8840** \u2713 \u21d2 \u2605200 tick\uff08\u260510 \u79d2\uff09\u81ea\u7136\u6d88\u5931 \u2713\uff1b</li>
 *   <li>\u2605\u624e\u7740\u65f6\u79fb\u52a8\u901f\u5ea6 **-80%** \u2713\uff08\u2605\u5c5e\u6027\u4e58\u7b97 0.2 \u2713\uff09\uff1b</li>
 *   <li>\u2605\u73a9\u5bb6\u53ef\u957f\u6309 G 2 \u79d2\u62d4\u9664 \u2713\uff08\u2605\u677e\u624b\u5931\u8d25 \u2713 \u21d2 \u2605\u91cd\u6765 \u2713\uff09\uff1b</li>
 *   <li>\u2605**\u751f\u7269\u4e0d\u4f1a\u62d4** \u2713\uff1b\u2605**\u58a8\u9ed8\u514d\u75ab\u6b64\u7bad** \u2713\u3002</li>
 * </ul>
 */
public final class StuckArrowManager {

    /** \u2605\u5168\u8840 = 20.0 \u21d2 \u2605\u4ee5 0.1 \u4e3a\u5355\u4f4d = 200 \u4efd \u2713 */
    public static final int FULL = 200;

    private static final String KEY = "tinkersnewlife:stuck_arrow";
    private static final UUID SLOW_UUID = UUID.fromString("6a1f0c22-9d54-4c1b-9a3f-77b0c4d51e88");

    private StuckArrowManager() {
    }

    public static boolean has(LivingEntity e) {
        return e.getPersistentData().getInt(KEY) > 0;
    }

    /** \u2605\u88ab\u5f3a\u5316\u7bad\u547d\u4e2d \u21d2 \u2605\u624e\u4e0a\u4e00\u652f \u2713 */
    public static void apply(LivingEntity e) {
        if (e.level().isClientSide) {
            return;
        }
        if (e instanceof KtuluMomo) {
            return;                          // \u2605\u58a8\u9ed8\u514d\u75ab \u2713
        }
        e.getPersistentData().putInt(KEY, FULL);
        applySlow(e);
        notify(e, true);
    }

    /** \u2605\u6bcf tick\uff08\u2605\u7531 LivingTickEvent \u9a71\u52a8 \u2713\uff09 */
    public static void tick(LivingEntity e) {
        if (e.level().isClientSide) {
            return;
        }
        int hp = e.getPersistentData().getInt(KEY);
        if (hp <= 0) {
            return;
        }
        hp -= 1;                             // \u2605 0.1 \u8840 / tick \u2713
        e.getPersistentData().putInt(KEY, hp);
        if (hp <= 0) {
            clear(e);
        } else {
            applySlow(e);
        }
    }

    /** \u2605\u62d4\u9664\uff08\u2605\u957f\u6309\u6210\u529f / \u2605\u8840\u91cf\u8017\u5c3d \u2713\uff09 */
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
