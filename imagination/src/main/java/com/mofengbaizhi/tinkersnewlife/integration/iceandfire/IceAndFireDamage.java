package com.mofengbaizhi.tinkersnewlife.integration.iceandfire;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * §801 冰火传说的**自定义龙息伤害类型**（★反编译实证 ✓）。
 *
 * <p>冰火自带四个伤害类型（★数据包 ✓）：
 * <pre>
 *   iceandfire:dragon_fire       { "message_id": "dragon_fire"      }
 *   iceandfire:dragon_ice        { "message_id": "dragon_ice"       }
 *   iceandfire:dragon_lightning  { "message_id": "dragon_lightning" }
 *   iceandfire:gorgon            { "message_id": "gorgon"           }
 * </pre>
 * 代码侧在 {@code com.github.alexthe666.iceandfire.misc.IafDamageRegistry}，
 * 提供 {@code causeDragonFireDamage(Entity)} / {@code causeDragonIceDamage(Entity)} /
 * {@code causeDragonLightningDamage(Entity)}（★均为 public static ✓）。
 *
 * <p>⚠ **冰火不是编译依赖** ✗（★本仓 {@code integration/iceandfire} 下一行 I&amp;F 的 import 都没有 ✓）
 * ⇒ 只能**反射**取用；冰火不在场时全部返回 null ✓ 绝不抛异常 ✓。
 */
public final class IceAndFireDamage {

    private static final String REGISTRY = "com.github.alexthe666.iceandfire.misc.IafDamageRegistry";

    /** ★龙息三系：0=火 1=冰 2=雷 ✓ */
    public static final int FIRE = 0;
    public static final int ICE = 1;
    public static final int LIGHTNING = 2;

    /** ★三系对应的 message_id（★即 source.getMsgId() ✓） */
    public static final String[] MSG_IDS = {"dragon_fire", "dragon_ice", "dragon_lightning"};

    private static Method fireM;
    private static Method iceM;
    private static Method lightningM;
    private static boolean resolved = false;

    private IceAndFireDamage() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> c = Class.forName(REGISTRY);
            fireM = c.getMethod("causeDragonFireDamage", Entity.class);
            iceM = c.getMethod("causeDragonIceDamage", Entity.class);
            lightningM = c.getMethod("causeDragonLightningDamage", Entity.class);
        } catch (Throwable ignored) {
            // ★冰火不在场（或版本变了）⇒ 保持 null ✓
        }
    }

    /** ★冰火是否可用（★三个方法都拿到了才算 ✓） */
    public static boolean available() {
        resolve();
        return fireM != null && iceM != null && lightningM != null;
    }

    /** ★取对应属性的龙息伤害源；拿不到就返回 null ✓ */
    @Nullable
    public static DamageSource breath(Level level, @Nullable Entity source, int kind) {
        resolve();
        Method m = switch (kind) {
            case FIRE -> fireM;
            case ICE -> iceM;
            case LIGHTNING -> lightningM;
            default -> null;
        };
        if (m == null || level == null) {
            return null;
        }
        try {
            return (DamageSource) m.invoke(null, source);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** ★这一击是不是龙息：返回 0/1/2，不是则 -1 ✓ */
    public static int kindOf(@Nullable DamageSource src) {
        if (src == null) {
            return -1;
        }
        try {
            String id = src.getMsgId();
            for (int i = 0; i < MSG_IDS.length; i++) {
                if (MSG_IDS[i].equals(id)) {
                    return i;
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }
}
