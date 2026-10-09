package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * ⭐ §1137 <b>本模组的粒子注册表</b>（用户口径 ✓「我准备自制新粒子：样式为内黑外红的横向斩击，
 * 用于在**处决**时应用」✓）。
 *
 * <h2>⚠ 本仓此前**一个自定义粒子都没有**（实测 ✓）</h2>
 * 所以这一套是**从零搭**的 ✗：① 本注册表 ✓ ② 客户端粒子类
 * （{@code client/particle/ExecuteSlashParticle} ✓）③ {@code RegisterParticleProvidersEvent} 注册 ✓
 * ④ {@code assets/tinkersnewlife/particles/execute_slash.json} ✓
 * ⑤ {@code textures/particle/execute_slash.png} ✓。
 *
 * <h2>⭐ 为什么用 {@link SimpleParticleType} ✗</h2>
 * ⭐ 它**不带任何数据载荷** ✓ ⇒ ⭐ 生成时只要 {@code level.addParticle(type, x,y,z, vx,vy,vz)} ✓
 * ⇒ ⚠ 不需要写 {@code ParticleOptions}／Codec／StreamCodec 那一串 ✗（⚠ 1.20.1 里那是几十行样板 ✓）
 * —— 本粒子的"样式"完全由**贴图**决定 ✓ 不需要参数 ✓。
 *
 * <h2>⚠ 必须挂到 mod 事件总线（否则运行时才炸 ✗）</h2>
 * ⭐ 在 {@link TinkersNewlife} 的构造里调 {@code ModParticles.PARTICLES.register(modEventBus)} ✓
 * —— ⚠ 漏了这一步**不会编译报错** ✗ 只会在**运行时**取到 null ✗。
 */
public final class ModParticles {

    private ModParticles() {
    }

    /** 粒子注册表 ✓（⚠ 别忘了在主类构造里 {@code register(modEventBus)} ✗） */
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, TinkersNewlife.MOD_ID);

    /**
     * ⭐ <b>处决斩击</b>：横向的「内黑外红」斩击 ✓（用户口径 ✓）。
     * <p>⚠ 贴图是**程序化占位** ✓（{@code textures/particle/execute_slash.png} ✓ 由脚本生成 ✓）——
     * ⭐ 用户以后手绘替换时**只换那张 png** ✓ 不用动任何代码 ✓。
     */
    public static final RegistryObject<SimpleParticleType> EXECUTE_SLASH =
            PARTICLES.register("execute_slash", () -> new SimpleParticleType(false));
}
