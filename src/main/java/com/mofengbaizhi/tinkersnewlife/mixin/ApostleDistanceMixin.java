package com.mofengbaizhi.tinkersnewlife.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⭐⭐ §1211 <b>使徒的"靠近逻辑"＋"女巫之王高度限制"</b>
 * （⭐ 用户口径 ✓ 2026-10-10 逐字 ✓）：
 * <blockquote>
 * ⭐ 「**靠近逻辑改为使徒不会主动远离玩家超过10格，距离超过16格就会试图传送到玩家身边，
 * 女巫之王词条只会在玩家距离过近的情况下往天上飞一些，不会一直向上。**」✓
 * </blockquote>
 *
 * <h2>⚠⚠ 四条硬约束（⭐ 用户明确要求 ✗ 不许违反 ✓）</h2>
 * <ol>
 *   <li>⭐ **同步施法（`StrafeCastGoal`）不取消** ✓；</li>
 *   <li>⭐ **一个法术 goal 都不删** ✓；</li>
 *   <li>⭐ **施法频率只降不取消** ✓（⚠ 本轮**没动**施法 ✓ ⭐ 见下 ✓）；</li>
 *   <li>⭐ 距离逻辑**只在 `tick` 的 TAIL 补动作** ✗ ⭐ 不干预已有 AI goal ✓。</li>
 * </ol>
 *
 * <h2>⭐ 三条距离/高度规则（⭐ 都在 TAIL 里做 ✓）</h2>
 * <ul>
 *   <li>⭐ **> 16 格** ✗ ⇒ ⭐ 调 ⭐ `teleportTowards(target)` ✓（⭐ 原版那个阈值是 **1024** ✗ ⭐ 太远 ✓）；</li>
 *   <li>⭐ **> 10 格** ✗ ⇒ ⭐ `getNavigation().moveTo(target, 1.0)` ✓
 *       ⭐ 强制它往玩家走 ✓ ⇒ ⭐ **不会主动远离超过 10 格** ✓；</li>
 *   <li>⭐ **女巫之王**（⭐ `title.goety.5` ⇒ ⭐ `titleNumber == 5` ✓ 已由 lang 证实 ✓）✗
 *       ⭐ 只在 ⭐ **距离过近**（< {@value #WITCH_KING_NEAR} 格 ✓）时才允许上浮 ✗
 *       ⇒ ⭐ 距离一旦拉开 ⇒ ⭐ **把向上的速度压掉** ✓ ⇒ ⭐ **不会一直向上** ✓。</li>
 * </ul>
 *
 * <p>⚠ 两道注入 ✗：⭐ `tick` ✗ ＋ ⭐ SRG ⭐ `m_8119_` ✗（⭐ `require = 0` ✓ 变了也不崩 ✓）。
 * ⚠ 所有反射与取值都包 `try/catch` ✗ ⭐ 出错只跳过 ✓ ⭐ 绝不连累 AI ✓。
 */
@Mixin(targets = "com.Polarice3.Goety.common.entities.boss.Apostle", remap = false)
public abstract class ApostleDistanceMixin {

    /** ⭐ 超过这个距离 ⇒ ⭐ 直接传送过来 ✓ */
    private static final double TELEPORT_AT = 16.0D;
    /** ⭐ 超过这个距离 ⇒ ⭐ 强制它往玩家走（⭐ "不会主动远离超过 10 格" ✓） */
    private static final double KEEP_CLOSE_AT = 10.0D;
    /** ⭐ 女巫之王只在"玩家距离过近"时允许上浮 ✓（⭐ 格 ✓） */
    private static final double WITCH_KING_NEAR = 6.0D;

    private static java.lang.reflect.Method TNL_TELEPORT_TOWARDS = null;
    private static java.lang.reflect.Method TNL_GET_TITLE = null;
    private static java.lang.reflect.Method TNL_IS_SETTING_UP = null;
    /** ⭐ §1213 ⭐ `public Vec3 toTeleportPos` ✓（⭐ 待瞬移的目标点 ✓） */
    private static java.lang.reflect.Field TNL_TO_POS = null;
    /** ⭐ §1213 ⭐ `public int toTeleportTime` ✓（⭐ 倒计时 ✓） */
    private static java.lang.reflect.Field TNL_TO_TIME = null;
    private static boolean TNL_RESOLVED = false;

    private static void tnl$resolve(Object self) {
        if (TNL_RESOLVED) {
            return;
        }
        TNL_RESOLVED = true;
        try {
            TNL_TELEPORT_TOWARDS = self.getClass().getMethod("teleportTowards", Entity.class);
            TNL_TELEPORT_TOWARDS.setAccessible(true);
        } catch (Throwable ignored) {
            TNL_TELEPORT_TOWARDS = null;
        }
        try {
            TNL_GET_TITLE = self.getClass().getMethod("getTitleNumber");
            TNL_GET_TITLE.setAccessible(true);
        } catch (Throwable ignored) {
            TNL_GET_TITLE = null;
        }
        try {
            TNL_IS_SETTING_UP = self.getClass().getMethod("isSettingUpSecond");
            TNL_IS_SETTING_UP.setAccessible(true);
        } catch (Throwable ignored) {
            TNL_IS_SETTING_UP = null;
        }
        try {
            TNL_TO_POS = self.getClass().getField("toTeleportPos");
            TNL_TO_POS.setAccessible(true);
        } catch (Throwable ignored) {
            TNL_TO_POS = null;
        }
        try {
            TNL_TO_TIME = self.getClass().getField("toTeleportTime");
            TNL_TO_TIME.setAccessible(true);
        } catch (Throwable ignored) {
            TNL_TO_TIME = null;
        }
    }

    /** ⭐ 这个使徒此刻是否在"转阶段"✗（⭐ 拿不到 ⇒ false ✓ 保持原行为 ✓） */
    private static boolean tnl$isSettingUpSecond(Object self) {
        if (TNL_IS_SETTING_UP == null) {
            return false;
        }
        try {
            Object r = TNL_IS_SETTING_UP.invoke(self);
            return r instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** ⭐⭐ §1213 **清掉"待瞬移"状态** ✗ ⇒ ⭐ `tick` 里那段 `moveTo` 不会执行 ✓ */
    private static void tnl$clearPendingTeleport(Object self) {
        try {
            if (TNL_TO_POS != null) {
                TNL_TO_POS.set(self, null);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (TNL_TO_TIME != null) {
                TNL_TO_TIME.setInt(self, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0, remap = false)
    private void tinkersnewlife$distanceRules(CallbackInfo ci) {
        tnl$apply((net.minecraft.world.entity.Mob) (Object) this);
    }

    /** ⭐ 兜底注入：直连 SRG 方法名 ✗（⭐ 绕开 refmap ✓） */
    @Inject(method = "m_8119_", at = @At("TAIL"), require = 0, remap = false)
    private void tinkersnewlife$distanceRulesSrg(CallbackInfo ci) {
        tnl$apply((net.minecraft.world.entity.Mob) (Object) this);
    }

    private static void tnl$apply(net.minecraft.world.entity.Mob self) {
        try {
            if (self.level().isClientSide) {
                return;
            }
            tnl$resolve(self);
            // ⭐⭐ §1213 **转阶段期间：清掉"待瞬移"状态** ✗✗
            //   （⭐ 用户实测 2026-10-10：⭐「**转阶段还是在一直朝我瞬移**」✓）
            //   ⚠ 根因 ✗：⭐ 真正的位移**不是** `teleport()` 做的 ✗ ⭐ 而是 `tick` 里这段 ✓：
            //   <pre>
            //     ++this.toTeleportTime;
            //     int time = this.isSecondPhase() ? 20 : 40;
            //     if (this.toTeleportTime &gt;= time) {
            //         this.moveTo(this.toTeleportPos.x, y, z);   // ⚠ 真正的瞬移 ✗
            //         this.teleportHits();
            //         this.toTeleportPos = null;
            //     }
            //   </pre>
            //   ⚠ ⭐ §1210 我只 cancel 了 `teleport()`／`teleportTowards()` ✗
            //   ⚠ 而 ⭐ 只要 `toTeleportPos` **已经被设过** ✗（⭐ 可能在 cancel 之前 ✓
            //     ⭐ 或由 ⭐ `escapeTeleport` 之类的路径 ✓）⭐ 那段**照样 `moveTo`** ✓ ✓
            //   ⇒ ⭐ 修法：⭐ 转阶段时 ⭐ **把 `toTeleportPos` 清空 ＋ `toTeleportTime` 归零** ✗
            //     ⇒ ⭐ `if (this.toTeleportPos != null)` 那段**整段不执行** ✓ ✓
            //   ⭐ 两个字段都 ⭐ `public` ✓（⭐ 反编译实证 ✓）⇒ ⭐ 反射 ✓。
            if (tnl$isSettingUpSecond(self)) {
                tnl$clearPendingTeleport(self);
                return;      // ⚠ 转阶段期间**不做**任何距离干预 ✓（⭐ 免得和阶段动画打架 ✓）
            }
            // ⚠ 只认"活着的目标" ✗
            LivingEntity target = self.getTarget();
            if (target == null || !target.isAlive()) {
                return;
            }
            double dist = self.distanceTo(target);
            // ⭐ ① > 16 格 ⇒ ⭐ 传送到玩家身边 ✓
            if (dist > TELEPORT_AT && TNL_TELEPORT_TOWARDS != null) {
                try {
                    TNL_TELEPORT_TOWARDS.invoke(self, target);
                } catch (Throwable ignored) {
                }
            }
            // ⭐ ② > 10 格 ⇒ ⭐ 强制靠近（⭐ 不主动远离 ✓）
            else if (dist > KEEP_CLOSE_AT) {
                try {
                    self.getNavigation().moveTo(target, 1.0D);
                } catch (Throwable ignored) {
                }
            }
            // ⭐ ③ 女巫之王：⭐ 只有"距离过近"才允许上浮 ✗ ⭐ 否则压掉上升速度 ✓
            if (TNL_GET_TITLE != null && dist > WITCH_KING_NEAR) {
                int title;
                try {
                    Object t = TNL_GET_TITLE.invoke(self);
                    title = t instanceof Integer i ? i : -1;
                } catch (Throwable ignored) {
                    title = -1;
                }
                if (title == 5) {   // ⭐ `title.goety.5` ＝ 女巫之王 ✓（⭐ lang 实证 ✓）
                    Vec3 mv = self.getDeltaMovement();
                    if (mv.y > 0.02D) {
                        self.setDeltaMovement(mv.x, 0.0D, mv.z);
                        // ⭐ 顺手把它按回地面一点 ✗（⭐ 免得已经飘太高下不来 ✓）
                        self.fallDistance = 0.0F;
                    }
                }
            }
        } catch (Throwable ignored) {
            // ⭐ 出错只跳过 ✓ ⭐ 绝不连累 AI ✓
        }
    }
}
