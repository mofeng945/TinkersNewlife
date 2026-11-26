package com.mofengbaizhi.tinkersnewlife.integration.iceandfire;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * TNL1312 「被冰龙龙息冻上」的冻结 —— **复刻冰火传说（Ice and Fire）自己的冻结状态** ✓
 *
 * <h2>用户口径</h2>
 * 「把龙霜钢击中目标后的效果改成被冰龙龙息喷中后的相似效果，被冻上那样？我现在这个冰冻太粗糙了」
 *
 * <h2>冰火是怎么做的（反编译实读 ✓）</h2>
 * <ul>
 *   <li>`EntityDataProvider.getCapability(Entity)` ⇒ `LazyOptional&lt;EntityData&gt;`（public static ✓）；</li>
 *   <li>`EntityData.frozenData`（public 字段）⇒ `FrozenData`；</li>
 *   <li>`FrozenData.setFrozen(LivingEntity, int ticks)`（public ✓）——
 *       冰龙龙息就是**冻 400 tick**（`IafDragonDestructionManager` 里 ICE 分支 `sipush 400` ✓），
 *       冰龙钢剑命中是**冻 300 tick**（`DragonSteelOverrides` ✓）；</li>
 *   <li>冻住后由 `FrozenData#tickFrozen` 与客户端 `RenderFrozenState` 接管：
 *       目标**动不了/不能攻击**，并且渲染成**冰雕** ✓（这才是"被冻上"的样子 ✓）。</li>
 * </ul>
 *
 * <h2>为什么走反射</h2>
 * 本仓 **不把冰火当编译依赖**（`integration/iceandfire/` 下没有任何 I&amp;F 的 import ✓）。
 * ⇒ 全部反射；冰火不在场 / 版本变了 ⇒ {@link #freeze(Entity, int)} 返回 {@code false}，
 * 调用方自行退回原来的效果（功能不丢 ✓ 不崩 ✓）。
 */
public final class IceAndFireFrozen {

    private static final String PROVIDER = "com.github.alexthe666.iceandfire.entity.props.EntityDataProvider";

    private static boolean resolved;
    private static boolean usable;
    private static Method getCapabilityM;      // EntityDataProvider.getCapability(Entity)
    private static Method resolveM;            // LazyOptional#resolve()
    private static Field frozenDataField;      // EntityData.frozenData
    private static Method setFrozenM;          // FrozenData#setFrozen(LivingEntity, int)

    private IceAndFireFrozen() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            Class<?> provider = Class.forName(PROVIDER);
            getCapabilityM = provider.getMethod("getCapability", Entity.class);

            Class<?> frozen = Class.forName("com.github.alexthe666.iceandfire.entity.props.FrozenData");
            setFrozenM = frozen.getMethod("setFrozen", LivingEntity.class, int.class);

            Class<?> data = Class.forName("com.github.alexthe666.iceandfire.entity.props.EntityData");
            frozenDataField = data.getField("frozenData");

            Class<?> lazyOptional = Class.forName("net.minecraftforge.common.util.LazyOptional");
            resolveM = lazyOptional.getMethod("resolve");

            usable = getCapabilityM != null && setFrozenM != null && frozenDataField != null && resolveM != null;
        } catch (Throwable ignored) {
            // 冰火不在场 / 版本不符 ⇒ 保持不可用
            usable = false;
        }
    }

    /** 冰火的冻结能力是否可用 */
    public static boolean available() {
        resolve();
        return usable;
    }

    /**
     * 把目标冻成"冰雕"（与冰龙龙息同一套状态）。
     *
     * @param entity 目标（通常 LivingEntity）
     * @param ticks  冻结时长（tick）；≤0 视为不冻
     * @return true = 已成功施加冰火冻结；false = 冰火不在场/失败 ⇒ 调用方退回原效果
     */
    public static boolean freeze(@Nullable Entity entity, int ticks) {
        if (entity == null || ticks <= 0) {
            return false;
        }
        resolve();
        if (!usable) {
            return false;
        }
        try {
            Object lazy = getCapabilityM.invoke(null, entity);
            if (lazy == null) {
                return false;
            }
            Object optionalObj = resolveM.invoke(lazy);
            if (!(optionalObj instanceof Optional<?> optional)) {
                return false;
            }
            Object data = optional.orElse(null);
            if (data == null) {
                return false;
            }
            Object frozenData = frozenDataField.get(data);
            if (frozenData == null) {
                return false;
            }
            if (!(entity instanceof LivingEntity living)) {
                return false;
            }
            setFrozenM.invoke(frozenData, living, ticks);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
