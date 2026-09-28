package com.mofengbaizhi.tinkersnewlife.integration.twilightforest;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * <b>线程安全的随机源包装</b>（§779）—— 给暮色森林的 {@code TFMaze.rand} 套一层锁 ✓
 *
 * <h2>为什么需要它</h2>
 * 崩溃实证（NL `crash-2026-09-28_19.49.42-server.txt` ✓）：
 * <pre>
 * Description: Accessing LegacyRandomSource from multiple threads
 *   at twilightforest.world.components.structures.TFMaze.shouldTorch(TFMaze.java:393)
 *   at TFMaze.copyToStructure → MinotaurMazeComponent（牛头人迷宫）
 *   疑似模组：C2ME 0.2.0+alpha.13（多线程世界生成）＋ Twilight Forest 4.3.2508
 * </pre>
 * 原因：{@code TFMaze} 里是 {@code public final RandomSource rand} ✓（**每个迷宫实例共用一个随机源** ✓），
 * 而 C2ME 会把**同一个迷宫**的生成工作分到**多个世界生成线程**上跑 ✗
 * ⇒ 两个线程同时读写这个 {@code LegacyRandomSource} ✗
 * ⇒ 原版 {@code ThreadingDetector} 直接抛 {@code IllegalStateException} 崩游戏 ✗。
 *
 * <h2>为什么"包一层锁"是治本 ✓（而不是像 C2ME 配置那样只把崩溃改成警告 ✗）</h2>
 * 本类把**每一次**随机调用都 {@code synchronized} 起来 ✓ ⇒ 同一时刻只有一个线程能用那个随机源 ✓
 * ⇒ <b>竞态本身消失</b> ✓（检测器自然不会响 ✓）；而且**随机序列完全不变** ✓
 * （只是串行化 ✓ 不像换成 {@code createThreadSafe()} 那样会改变世界生成结果 ✗）。
 * <p>⚠ 代价：同一迷宫实例的多线程生成会被这个锁串行化 ✓（迷宫生成本来就是小段工作 ✓ 影响很小 ✓）。
 *
 * <p>⚠ 只实现了 {@link RandomSource} 的**抽象**方法 ✓；`nextIntBetweenInclusive` / `triangle` /
 * `consumeCount` / `nextInt(origin,bound)` 是接口的 **default** 方法 ✓ 它们最终都会调用下面这些 ✓
 * ⇒ 一样被锁住 ✓。
 */
public final class SynchronizedRandomSource implements RandomSource {

    private final RandomSource delegate;

    public SynchronizedRandomSource(RandomSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public synchronized RandomSource fork() {
        return new SynchronizedRandomSource(delegate.fork());
    }

    @Override
    public synchronized PositionalRandomFactory forkPositional() {
        return delegate.forkPositional();
    }

    @Override
    public synchronized void setSeed(long seed) {
        delegate.setSeed(seed);
    }

    @Override
    public synchronized int nextInt() {
        return delegate.nextInt();
    }

    @Override
    public synchronized int nextInt(int bound) {
        return delegate.nextInt(bound);
    }

    @Override
    public synchronized long nextLong() {
        return delegate.nextLong();
    }

    @Override
    public synchronized boolean nextBoolean() {
        return delegate.nextBoolean();
    }

    @Override
    public synchronized float nextFloat() {
        return delegate.nextFloat();
    }

    @Override
    public synchronized double nextDouble() {
        return delegate.nextDouble();
    }

    @Override
    public synchronized double nextGaussian() {
        return delegate.nextGaussian();
    }
}
