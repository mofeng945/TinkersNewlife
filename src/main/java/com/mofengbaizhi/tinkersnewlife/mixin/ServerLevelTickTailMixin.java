package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.util.TruePierce;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⭐⭐ §1206 <b>血量锁定改到 {@code ServerLevel#tick} 的 <b>TAIL</b> ⇒ 物理上最晚</b>
 * （⭐ 用户口径 ✓ 2026-10-10：「**加校验，写mixin**」✓）。
 *
 * <h2>⚠ 为什么原来的 `ServerTickEvent.END` 不够早／不够晚</h2>
 * ⭐ 原来的写入点 ⭐ `TickEvent.ServerTickEvent(END)` ✗ —— ⭐ 它在 ⭐ **服务端 tick 的收尾** ✓
 * ⚠ 但它与 ⭐ **`ServerLevel#tick`（⭐ 所有实体 tick 都在里面 ✓）** 的先后关系
 * ⭐ **并不由我们控制** ✗（⭐ 取决于 Forge 调度 ✓）⇒ ⚠ 可能出现"**它写在我们后面**" ✓
 * ⇒ ⭐ 于是锁不住 ✓。
 *
 * <h2>⭐⭐ 这个注入点为什么"包赢"</h2>
 * ⭐ `ServerLevel#tick` ⭐ **内部**会 ⭐ 遍历并 tick 掉 ⭐ **这个世界里所有实体** ✗
 * ⭐ 而我们在 ⭐ **同一个方法的 `TAIL`** ✗ ⇒ ⭐ **那一刻所有实体都已经 tick 完了** ✓ ✓
 * ⇒ ⭐ **任何实体自己的回血/锁血都已经跑过** ✓ ⇒ ⭐ **我们写的就是最后一下** ✓ ✓
 * ⭐ 没有比这更晚的公开时机了 ✓（⭐ 除非别的模组也注入 TAIL ⚠ 那只能比 priority ✓）。
 *
 * <p>⚠ 两道注入 ✗（⭐ 照 {@code EntityRenderDispatcherMixin} 的先例 ✓）：
 * ⭐ 方法名 ⭐ `tick` ✗ ⭐ 与 ⭐ 直连 SRG ⭐ `m_8793_` ✗（⭐ `remap = false` ✓ ⭐ 兜底 ✓）。
 */
@Mixin(value = ServerLevel.class, priority = 3000)
public class ServerLevelTickTailMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void tinkersnewlife$applyHealthLocks(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        tinkersnewlife$apply((ServerLevel) (Object) this);
    }

    /** ⭐ 兜底注入：直连 SRG 方法名 ✗（⭐ 绕开 refmap ✓ 照仓库既有写法 ✓） */
    @Inject(method = "m_8793_", at = @At("TAIL"), remap = false)
    private void tinkersnewlife$applyHealthLocksSrg(java.util.function.BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        tinkersnewlife$apply((ServerLevel) (Object) this);
    }

    private void tinkersnewlife$apply(ServerLevel level) {
        try {
            TruePierce.applyHealthLocks(level);
        } catch (Throwable ignored) {
            // ⭐ 锁应用出错绝不能连累服务端 tick ✗
        }
        try {
            // ⭐⭐ §1240 **跨 tick 清除复查**（⭐ 狱门疆封印／⭐ 咒灵操术收回 ✓）
            //   ⭐ 挂在这里的理由同血量锁 ✗：⭐ 这是 ⭐ **比所有实体 tick 都晚** 的时机 ✓
            //   ⇒ ⭐ "tick 自愈型"防清除 ⭐ **这一 tick 自愈完 ⭐ 我们紧接着再摘** ✓ ✓。
            com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailEntity.tickPurgeRecheck(level);
        } catch (Throwable ignored) {
            // ⭐ 复查出错绝不能连累服务端 tick ✗
        }
    }
}
