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

    protected ExecuteSlashParticle(ClientLevel level, double x, double y, double z,
                                   double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        // ⭐ 贴图帧（⚠ 贴图为单帧 ✓ 这里仍按本仓惯例取一次精灵 ✓）
        this.pickSprite(sprites);
        // ⚠ 惯性要**很小** ✗ —— 斩击应该"钉在原地一瞬"✓ 而不是飘走 ✓
        //   ⚠⚠ 而且速度的三个分量**同时被当作"倾斜角"的载体** ✗（⭐ 见下 ✓）——
        //   ⭐ 因为 {@code SimpleParticleType} 没有数据载荷 ❌ 传不了角度 ✓
        //   ⭐ 而本粒子惯性只留 15% ✓ 几乎不动 ✓ ⇒ 借用速度当载体**零副作用** ✓。
        this.xd = vx * 0.15D;
        this.yd = vy * 0.15D;
        this.zd = vz * 0.15D;
        this.lifetime = 6;
        this.baseSize = 0.75F + this.random.nextFloat() * 0.35F;
        this.quadSize = this.baseSize * 0.55F;
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
        // ⭐ 前 2 tick 迅速张开（斩击"刷"地一下 ✓）之后开始收细 ✓
        float t = (float) this.age / (float) this.lifetime;
        if (t < 0.34F) {
            this.quadSize = this.baseSize * (0.55F + t * 1.35F);
        } else {
            this.quadSize = this.baseSize * (1.45F - (t - 0.34F) * 1.1F);
        }
        // ⭐ 同时淡出 ✓（末段更明显 ✓）
        this.alpha = Math.max(0.0F, 0.95F * (1.0F - t * t));
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
