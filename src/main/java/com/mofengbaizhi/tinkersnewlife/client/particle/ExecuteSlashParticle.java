package com.mofengbaizhi.tinkersnewlife.client.particle;

import com.mofengbaizhi.tinkersnewlife.content.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * ⭐ §1137 <b>处决斩击粒子</b>（用户口径 ✓「样式为**内黑外红**的**横向斩击**，用于在**处决**时应用」✓）。
 *
 * <h2>它是怎么"横"起来的（⚠ 这里没有用 rotation ✗）</h2>
 * ⭐ 粒子贴图**本身就画成横向的斩痕** ✓ ⇒ 粒子只要**朝玩家看的方向铺开一排** ✓ 自然就是横向斩击 ✓
 * （⚠ 这样比"一片竖着的贴图再转 90°"稳 ✗ —— 旋转在 {@code TextureSheetParticle} 里要靠
 * {@code roll} ✓ 那个是**绕视线轴转**✗ 控制不出"横在世界上"的效果 ✓）。
 *
 * <h2>⭐ 内黑外红怎么体现</h2>
 * ⭐ **画在贴图里** ✓：⭐ 中央一条**纯黑**的芯 ✓ 上下各一层**深红→亮红**的边 ✓
 * ⇒ 生成出来就是"黑芯红边"的斩痕 ✓（⚠ 若用户以后要"红核黑边"✗ 换贴图即可 ✓ 代码不用动 ✓）。
 *
 * <h2>⚠ 生命周期</h2>
 * ⭐ 很短（{@code lifetime = 6} ✓ 约 0.3 秒 ✓）—— 斩击是**一瞬间**的 ✗ 拖久了像烟雾 ✓；
 * ⭐ 前 2 tick **快速变宽**（{@code quadSize} 由小变大 ✓）后逐渐淡出 ✓。
 */
public class ExecuteSlashParticle extends TextureSheetParticle {

    private final float baseSize;

    /** ⭐ 本片在刀路上的**序号**（0 ＝ 起点 ＝ **最小** ✓ 越大越靠终点且越大 ✓） */
    private final int index;

    /** ⭐ 延迟出现几 tick ✓（⭐ 靠它做出"一刀划过去"的时间差 ✓） */
    private final int delay;

    protected ExecuteSlashParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        // ⭐ 贴图帧（⚠ 贴图为单帧 ✓ 这里仍按本仓惯例取一次精灵 ✓）
        this.pickSprite(sprites);
        // ⚠ 惯性要**很小** ✗ —— 斩击应该"钉在原地一瞬"✓ 而不是飘走 ✓
        //   ⚠⚠ 而速度的三个分量**全被借去当"载荷"了** ✗：
        //     `(vx,vy)` ＝ 角度 ✓（见下 ✓）；⭐ `vz` ＝ **序号** ✓（决定延迟与尺寸 ✓）
        //   ⭐ 因为 {@code SimpleParticleType} **没有数据载荷** ✗ 传不了参数 ✓
        //   ⭐ 本粒子惯性只留 15% ✓ 几乎不动 ✓ ⇒ 借用速度**零副作用** ✓
        //   ⚠⚠ 但这也意味着：⭐ **谁把惯性调大，斩击就会"斜着飘"** ✗（两处注释都写明了这个耦合 ✓）。
        this.index = (int) Math.round(Math.abs(vz));
        this.xd = vx * 0.15D;
        this.yd = vy * 0.15D;
        this.zd = 0.0D;   // ⚠ `vz` 是载荷 ✗ **不能**当速度用 ✓ 否则会沿 z 乱飘 ✓
        // ⚠⚠ **延迟 2 tick / 片**（用户实测第二版反馈 ✗：「**看不出来出现过程**」✓
        //   —— ⭐ 第一版延迟只有 0~5 tick ＝ **0.25 秒** ✗ 快到看不见 ✓）
        //   ⇒ ⭐ 改成每片 2 tick ⇒ 6 片全过程 **0.5 秒** ✓ 看得清"一刀划过去" ✓。
        this.delay = this.index * 2;
        this.lifetime = 8 + this.delay;   // ⚠ 后出现的多活一会儿 ✓ 免得"尾端刚亮就没了" ✗

        // ⭐⭐ **挥砍轨迹**（用户口径 ✓：「**从左上开始斩，就在左上出现最小，然后一直往右下拉动尺寸**」✓）
        //   ⚠⚠ 实测第二版仍被否 ✗（「**还是多道，而且很小**」✓）——
        //   ⭐ 关键是 `quadSize` 的单位是**格** ✓ 而我起点只给 `0.30`
        //   ⇒ 视觉尺寸才约 0.2 格 ✗ 而片距 0.3 格 ⇒ ⭐ **片与片没搭上** ⇒ 看着"好几道" ✗。
        //   ⇒ ⭐ 现在 **1.0 → 3.5 格** ✓ ⇒ 相邻两片**大幅重叠** ✓
        //     ⇒ ⭐ 合成为**一道**由细到粗的刀光 ✓（⭐ 而不是"多道" ✓）。
        this.quadSize = 1.0F + this.index * 0.5F;   // ⭐ 1.0 → 3.5 ✓
        this.baseSize = this.quadSize;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        // ⭐ 不额外染色（颜色都在贴图里 ✓ 染了会把"内黑"毁掉 ✗）
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 0.95F;
        // ⚠ 朝向：粒子**不随玩家朝向** ✗ ⇒ 由生成时给的随机 roll 制造"每一击角度略有不同"✓
        //   （⚠ 真正的"横"是靠贴图 ✓ 见类注释 ✓）
        // ⭐⭐ **倾斜角**（用户口径 ✓ 2026-10-09：「处决粒子应该有一定的旋转角度，
        //   比如右上到左下／左上到右下之类的」✓）
        //   ⭐ 角度由**生成方**经"速度三元组"传来 ✓（⭐ `(vx,vy)` 就是 `(cos,sin)` ✓ 见 `LongShortBladeHandler` ✓）
        //   ⭐ 用 `roll` 把它落到屏幕上 ✓ —— ⚠ `roll` 是**绕视线轴**转 ✗
        //     所以它转出来的正好是"屏幕上的倾斜" ✓ 正是斜斩要的效果 ✓。
        double angle = Math.atan2(vy, vx);
        this.roll = (float) (-angle);
        this.oRoll = this.roll;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        // ⭐⭐ **延迟出现**（用户口径 ✓：起点最小 ✓ 一路"拉"到终点 ✓ ⇒ 一刀划过去 ✓）
        //   ⚠ 没轮到自己的片子 **完全不显示** ✗（`alpha = 0` ✓）⇒ 于是看起来是**逐片亮起** ✓。
        if (this.age <= this.delay) {
            this.alpha = 0.0F;
            return;
        }
        // ⭐ **只淡出，不改尺寸** ✓（⚠ 第一版这里"前 1/3 张开后收细"✗
        //   用户实测反馈：「**为什么斩击是从中心放大的**」✓ ⇒ ⭐ 尺寸动画已整段删除 ✓
        //   ⭐ 现在的"变大"是**沿刀路逐片更大** ✓ 不是单片的从中心放大 ✗）。
        float life = (float) (this.age - this.delay) / (float) Math.max(1, this.lifetime - this.delay);
        this.alpha = Math.max(0.0F, 0.95F * (1.0F - life * life));
        this.move(this.xd, this.yd, this.zd);
    }

    /** ⭐ 用**半透明**渲染类型 ✓ —— 斩击的边缘要能淡出 ✓（不透明会让边缘发黑方块 ✗） */
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** ⭐ 粒子注册用的工厂 ✓（{@code RegisterParticleProvidersEvent} 里挂 ✓） */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public net.minecraft.client.particle.Particle createParticle(SimpleParticleType type,
                                                                    ClientLevel level, double x, double y, double z,
                                                                    double vx, double vy, double vz) {
            return new ExecuteSlashParticle(level, x, y, z, vx, vy, vz, this.sprites);
        }
    }

    /** ⭐ 供外部确认"这个类确实被粒子系统用到"（⚠ 防呆 ✓ 免得重命名后忘了改注册 ✓） */
    public static net.minecraft.core.particles.ParticleType<?> type() {
        return ModParticles.EXECUTE_SLASH.get();
    }
}
