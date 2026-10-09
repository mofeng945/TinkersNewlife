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

    /** ⭐ 延迟出现几 tick ✓（⭐ 目前恒为 0 ✓ 保留字段以备将来做"逐片"效果 ✓） */
    private final int delay;

    /** ⭐ 长大用多少 tick ✓（⭐ 前 `GROW_TICKS` 长大 ✓ 之后淡出 ✓） */
    private static final int GROW_TICKS = 8;

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
        // ⭐⭐ **真的会走**（用户口径 ✓：「**就不能从左上往右下滑动吗，类似原版横扫特性？**」✓）
        //   ⚠ 前三版我都把这些分量只当"载荷" ✗ 粒子**钉在原地** ✓ ⇒ ⚠ 用户看到的就只是"闪一下" ✗
        //   ⇒ ⭐ 现在 `(vx,vy,vz)` 是 ⭐ **真实的滑动速度** ✓（⭐ 由生成方指向"终点" ✓）
        //     ⭐ 只留很轻的阻尼 ✓ ⇒ ⭐ 滑过去、微微减速 ✓（`move()` 自己还会再加一点摩擦 ✓）。
        this.xd = vx * 0.85D;
        this.yd = vy * 0.85D;
        this.zd = vz * 0.85D;
        this.delay = 0;
        this.lifetime = 14;   // ⭐ 前 8 tick 长大 ✓ 后 6 tick 淡出 ✓（⭐ 全程 0.7 秒 ✓）

        // ⭐⭐ **贴着滑动方向倾斜**（⭐ 处理"↙ / ↘"两向 ✓）
        //   ⭐ 贴图里画的是 **↘** ✓ ⇒ ⚠ 若这一刀是**往上**滑（`vy > 0` ✓）就得**镜像 90°** ✓。
        this.roll = vy > 0.0D ? (float) (Math.PI * 0.5D) : 0.0F;
        this.oRoll = this.roll;

        // ⭐ 尺寸：⭐ 出生很小 ✓ 一路长大 ✓（⭐ `quadSize` 单位是**格** ✗ 见 §1141 ✓）
        this.baseSize = 3.0F;
        this.quadSize = 0.30F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        // ⭐ 不额外染色（颜色都在贴图里 ✓ 染了会把"内黑"毁掉 ✗）
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.alpha = 0.95F;
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
        // ⭐⭐ **慢慢出现**（用户口径 ✓：「**就不能只用一道刀光慢慢出现吗**」✓）
        //   ⭐ 前 8 tick **长大** ✓ 后 6 tick **淡出** ✓（⭐ 全程 0.7 秒 ✓ 看得清 ✓）
        //   ⚠ 用**缓出**曲线（`1-(1-g)²` ✓）⇒ 前段快、后段慢 ⇒ ⭐ 像刀光"铺开"而不是"弹开" ✓。
        float t = (float) this.age / (float) this.lifetime;
        if (this.age <= GROW_TICKS) {
            float g = (float) this.age / (float) GROW_TICKS;
            float eased = 1.0F - (1.0F - g) * (1.0F - g);
            this.quadSize = 0.30F + (this.baseSize - 0.30F) * eased;
            this.alpha = 0.95F;
        } else {
            float f = (float) (this.age - GROW_TICKS)
                    / (float) Math.max(1, this.lifetime - GROW_TICKS);
            this.alpha = Math.max(0.0F, 0.95F * (1.0F - f));
        }
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
