package com.mofengbaizhi.tinkersnewlife.content.energy;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * <b>来源 ①「亮度」：亮度越低越快</b>（用户口径 2026-09-21 定的规则 ✓ §545 保留、只把默认满速降到 0.5 ✓）。
 *
 * <h2>公式（唯一实现处，别在别处再抄一份 ✗）</h2>
 * <pre>
 *     light = level.getMaxLocalRawBrightness(pos.above())          // 0~15
 *     rate  = pedestal_charge_max_per_second × (light_cap − light) / light_cap
 * </pre>
 * <ul>
 *   <li>亮度 0 ⇒ <b>满速</b>（§545 起默认 <b>0.5 EE/秒</b> = 30 EE/分钟 ⇒ 一颗 1000 EE 的水晶约 33 分钟 ✓）；</li>
 *   <li>亮度 ≥ {@code light_cap}（默认 15）⇒ <b>0</b>，完全不充 ✓；</li>
 *   <li>中间线性（例：默认参数下亮度 12 ⇒ 0.5 × 3 / 15 = 0.1 EE/秒 ✓）。</li>
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
 *   火把旁边  ⇒ 亮度 14 左右        ⇒ 很慢（默认参数 0.033 EE/秒 ✓）
 *   雷雨天白天⇒ 天光被压低 ⇒ 比晴天白天快一点 ✓（免费得到的"天气影响"，无需额外代码 ✓）
 * </pre>
 *
 * <h2>§545：本条与"植物凋灵度"的关系（诚实记一笔）</h2>
 * 用户口径里"① 植物"写的是<b>每株 +1 点凋灵度、1 点 = 0.5 EE</b> ⇒ 那条被<b>完整</b>实现在
 * {@link PlantEnergySource} 里（每株固定 0.5 EE/秒 ✓），本条<b>不</b>再按亮度给植物加权 ✗
 * —— 否则同一株植物会被两套规则各算一次，数字就不是用户给的那个了 ✗。
 * 所以五条来源互不干涉、<b>只是相加</b> ✓。
 *
 * <p>全程只读、无副作用、不分配 ⇒ 每秒每台座调用一次毫无压力 ✓
 * （{@code simulate} 参数对它没有影响：本条<b>从不</b>改世界 ✓）。
 */
public final class LightLevelEnergySource implements AmbientEnergySource {

    /** 配置允许清单里写的 id（也是注册表里的 key ✓） */
    public static final String ID = "light_level";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String shortName() {
        return "亮度（越暗越快）";
    }

    @Override
    public String summary() {
        return "rate = pedestal_charge_max_per_second * (pedestal_light_cap - light) / pedestal_light_cap";
    }

    @Override
    public double eePerSecond(Level level, BlockPos pos, boolean simulate) {
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
