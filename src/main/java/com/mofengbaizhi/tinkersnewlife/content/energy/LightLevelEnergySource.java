package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * <b>默认（也是当前唯一）的环境能量来源：亮度越低越快</b>（用户口径 2026-09-21 ✓）。
 *
 * <h2>公式（唯一实现处，别在别处再抄一份 ✗）</h2>
 * <pre>
 *     light = level.getMaxLocalRawBrightness(pos.above())          // 0~15
 *     rate  = pedestal_charge_max_per_second × (light_cap − light) / light_cap
 * </pre>
 * <ul>
 *   <li>亮度 0 ⇒ <b>满速</b>（默认 5.0 EE/秒 = 100 EE/分钟 ⇒ 一颗 1000 EE 的水晶 ≈ 3.3 分钟 ✓）；</li>
 *   <li>亮度 ≥ {@code light_cap}（默认 15）⇒ <b>0</b>，完全不充 ✓；</li>
 *   <li>中间线性（例：默认参数下亮度 12 ⇒ 5.0 × 3 / 15 = 1.0 EE/秒 ✓）。</li>
 * </ul>
 *
 * <h2>⚠ 为什么是 {@code pos.above()} 而不是 {@code pos} 自己</h2>
 * ① 水晶是<b>浮在台座上方</b>的（渲染同理）⇒ 判定的应当是"水晶所在那一格的环境光"✓；
 * ② 若量台座自己脚下那格，读数会被台座自身（以及紧贴的水晶方块）的发光影响 ⇒ 出现"越充越亮、越亮越慢"的
 * 自反馈 ✗。⚠ 诚实记一笔：紧邻的<b>水晶方块</b>本身发光等级 7（{@code ElderCrystalBlock#lightLevel}）⇒
 * 它会经由 {@code pos.above()} 读到约 6 的光 ⇒ 台座旁边堆水晶方块会稍微拖慢充能 ✓
 * （这是"亮度越低越快"这条规则的<b>直接推论</b>，不是 bug ✓ 也不是"看不见天空"✗ —— 没有任何天空判定 ✓）。
 *
 * <h2>为什么用 {@code getMaxLocalRawBrightness}</h2>
 * 它就是<b>方块光 + 天光</b>综合后的那一个 0~15 的数（{@code LevelReader} 的默认方法，
 * 内部 {@code getRawBrightness(pos, getSkyDarken())}）—— 与原版<b>刷怪亮度判定同一个量</b> ✓。
 * 用户明确要求"别自己拆 BLOCK/SKY 两层"✗ ⇒ 这里只调这一个方法 ✓。
 *
 * <h2>为什么没有"夜晚/露天/雷雨"这些条件</h2>
 * 用户口径：亮度规则已经把它们覆盖了 ✓。以下都<b>只是</b>这条规则的推论，代码里<b>不写</b> ✗：
 * <pre>
 *   夜晚露天  ⇒ 天光≈0（且没有方块光）⇒ 亮度低 ⇒ 充得快 ✓
 *   白天露天  ⇒ 亮度 15            ⇒ 不充 ✗
 *   漆黑洞穴  ⇒ 亮度 0（白天也一样）⇒ 充得最快 ✓（用户点名要求"白天在漆黑洞穴里也应当能充"✓）
 *   火把旁边  ⇒ 亮度 14 左右        ⇒ 很慢（默认参数 0.33 EE/秒 ✓）
 *   雷雨天白天⇒ 天光被压低 ⇒ 比晴天白天快一点 ✓（免费得到的"天气影响"，无需额外代码 ✓）
 * </pre>
 *
 * <p>全程只读、无副作用、不分配 ⇒ 每秒每台座调用一次毫无压力 ✓。
 */
public final class LightLevelEnergySource implements AmbientEnergySource {

    /** 配置 {@code elder_crystal.pedestal_source} 里写的 id */
    public static final String ID = "light_level";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public double eePerSecond(Level level, BlockPos pos) {
        if (level == null || pos == null) return 0.0D;

        double maxPerSecond = ModConfig.pedestalChargeMaxPerSecond();
        int cap = ModConfig.pedestalLightCap();
        if (maxPerSecond <= 0.0D || cap <= 0) return 0.0D;

        // 综合亮度（方块光 + 天光，0~15）——只调这一个方法 ✓ 不自己拆两层 ✗
        int light = level.getMaxLocalRawBrightness(pos.above());
        if (light < 0) light = 0;
        if (light >= cap) return 0.0D;   // 亮到阈值 ⇒ 完全不充 ✓

        int delta = cap - light;         // 越暗越大 ⇒ 越快 ✓
        double rate = maxPerSecond * delta / (double) cap;

        // 双保险：绝不返回负数（cap>light 时 delta 必然 >0，这里只是防将来改坏 ✓）
        return Math.max(0.0D, rate);
    }
}
