package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.integration.twilightforest.SynchronizedRandomSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * <b>修暮色森林牛头人迷宫在多线程世界生成下的随机源竞态</b>（§779，可配置关闭 ✓）
 *
 * <h2>崩溃实证（NL `crash-2026-09-28_19.49.42-server.txt` ✓）</h2>
 * <pre>
 * Description: Accessing LegacyRandomSource from multiple threads
 *   at twilightforest.world.components.structures.TFMaze.shouldTorch(TFMaze.java:393)
 *   at TFMaze.copyToStructure → MinotaurMazeComponent
 *   疑似模组：C2ME 0.2.0+alpha.13 ＋ Twilight Forest 4.3.2508
 * </pre>
 * ⚠ 报告里 {@code tinkersnewlife} 只出现在"模组列表/维度列表"里 ✓，**没有任何一帧调用栈** ✓
 * ⇒ **这不是我们模组的崩溃** ✗，是 C2ME 的多线程世界生成撞上 TF 的非线程安全随机源 ✗。
 *
 * <h2>修法：把构造器传进来的随机源换成"带锁包装" ✓</h2>
 * {@code TFMaze} 的成员是 {@code public final RandomSource rand} ✓（**每个迷宫实例共用** ✓），
 * 且**只在构造器里赋值一次** ✓ ⇒ 只要在赋值前把参数包一层 {@link SynchronizedRandomSource} ✓，
 * 之后**所有**使用者（TF 自己的方法与别的类 ✓）拿到的都是有锁的实例 ✓ ⇒ 竞态从根上消失 ✓。
 *
 * <p>⚠ 描述符与取参都写明确 ✓：{@code TFMaze(int, int, RandomSource)} ✓ 第三个参数是**唯一**的
 * {@code RandomSource} 类型参数 ⇒ {@code argsOnly = true, ordinal = 0} ✓（§767 就是描述符写错导致静默失效 ✗）；
 * 并且 {@code require = 1} ✓ —— 匹配不上就**响亮报错** ✓，绝不静默 ✗。
 *
 * <p>⚠ 开关：{@code twilight_maze_thread_safe_random}（默认 <b>true</b> ✓）——
 * 关掉即恢复 TF 原样（届时若再撞竞态，C2ME 仍会照旧崩 ✗）。
 */
@Mixin(targets = "twilightforest.world.components.structures.TFMaze", remap = false)
public class TwilightMazeRandomMixin {

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 1)
    private static RandomSource tinkersnewlife$threadSafeMazeRandom(RandomSource original) {
        try {
            if (!ModConfig.twilightMazeThreadSafeRandom()) return original;
            return original == null ? null : new SynchronizedRandomSource(original);
        } catch (Throwable ignored) {
            // 兼容补丁绝不许把别人的模组搞崩 ✗（配置取不到就按"不包"处理 ✓）
            return original;
        }
    }
}
