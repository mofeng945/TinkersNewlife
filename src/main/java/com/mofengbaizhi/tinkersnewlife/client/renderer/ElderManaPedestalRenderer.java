package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.block.ElderManaPedestalBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * <b>魔力台座的方块实体渲染器</b>：把台座上那颗水晶<b>悬浮 + 自转 + 上下轻浮</b>地画在台面上方 ✓。
 *
 * <h2>为什么选「BER + ItemRenderer」这条路（而不是 ItemDisplay 实体）</h2>
 * 用户要求"选一种简单可靠的" ✓。两种做法的对比：
 * <ul>
 *   <li><b>ItemDisplay 实体</b>（原版展示实体）：要把一个实体 spawn 在方块上方、还要跟着方块被拆/被推/区块卸载
 *       去同步与回收 —— 多一份"实体 ↔ 方块"的生命周期要自己管 ✗（一旦漏回收就会留下看不见的悬浮实体 ✗）；</li>
 *   <li><b>BER + {@code ItemRenderer#renderStatic}</b>（本节选的）：台座方块实体里本来就存着那颗水晶的
 *       {@code ItemStack}（服务端权威 ✓ 改了才发包 ✓）⇒ 渲染器只是<b>把它画出来</b>，没有第二个对象、
 *       没有额外生命周期 ✓。而"在世界上画一个物品栈"这件事本模组早就在用
 *       （{@code FlyingSwordRenderer} / {@code YoYoRenderer} 都是 `ItemRenderer#renderStatic` ✓ 走过验证 ✓）。</li>
 * </ul>
 *
 * <h2>画法与观感</h2>
 * <ul>
 *   <li>高度 = 台面之上一点（{@link #BASE_HEIGHT} = 1.1 格），再叠一点正弦上下浮动 ✓；</li>
 *   <li>绕 Y 轴匀速自转（{@value #SPIN_DEGREES_PER_TICK} 度/tick ⇒ 约 5 秒一圈 ✓ 慢到看得清、不晃眼 ✓）；</li>
 *   <li>{@link LightTexture#FULL_BRIGHT} 画：水晶本身是发光的 ✓ 不该被环境光照暗 ✓
 *       （与 {@code CurseVaultRenderer} 画能量团同一口径 ✓）；</li>
 *   <li>{@link ItemDisplayContext#NONE} + 自己缩放：不用原版那几套 display 变换 ⇒ 观感只由本类决定 ✓
 *       （本模组现有两处 `renderStatic` 也都是 NONE ✓）。</li>
 * </ul>
 *
 * <p>水晶为空 ⇒ 直接不画（{@code return}）✓ 没有任何常驻开销 ✗。
 * <p>⚠ 时间取 {@code level.getGameTime() + partialTick}：平滑且与服务端无关 ✓（抖动由 partialTick 抹平 ✓）。
 */
public class ElderManaPedestalRenderer implements BlockEntityRenderer<ElderManaPedestalBlockEntity> {

    /** 悬浮高度（格）：台面模型顶在 13/16 ≈ 0.81 格 ⇒ 1.1 刚好悬在台面之上一点 ✓ */
    private static final float BASE_HEIGHT = 1.1F;

    /** 自转速度（度/tick）：1.2 ⇒ 约 5 秒一圈 ✓ */
    private static final float SPIN_DEGREES_PER_TICK = 1.2F;

    /** 上下浮动幅度（格）与周期（tick）—— 很轻，只是"活着"的感觉 ✓ */
    private static final float BOB_AMPLITUDE = 0.06F;
    private static final float BOB_PERIOD_TICKS = 60.0F;

    /** 缩放：水晶物品贴图 16×16，0.7 倍看着像"一枚指节大的晶体" ✓ */
    private static final float SCALE = 0.7F;

    public ElderManaPedestalRenderer(BlockEntityRendererProvider.Context context) {
        // 不需要从 context 取任何东西（不画方块模型、不画文字 ✓）
    }

    @Override
    public void render(ElderManaPedestalBlockEntity pedestal, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ItemStack crystal = pedestal.getCrystal();
        if (crystal.isEmpty()) return;   // 空台座 ⇒ 什么都不画 ✓

        Level level = pedestal.getLevel();
        float time = (level == null ? 0.0F : (float) level.getGameTime()) + partialTick;
        // 正弦浮动（2π/周期 ⇒ 用 BOB_PERIOD_TICKS 换算成弧度 ✓ 不写裸 2π 之外的东西 ✗）
        float bob = Mth.sin(time / BOB_PERIOD_TICKS * ((float) Math.PI * 2.0F)) * BOB_AMPLITUDE;

        poseStack.pushPose();
        // 以方块中心为轴、悬到台面之上 ✓
        poseStack.translate(0.5F, BASE_HEIGHT + bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * SPIN_DEGREES_PER_TICK));
        // §555 水晶方块（3D 方块模型）比水晶物品大得多 ⇒ 单独缩到 0.45 ✓ 悬浮观感一致 ✓
        float scale = crystal.is(com.mofengbaizhi.tinkersnewlife.content.ModItems.ELDER_CRYSTAL_BLOCK.get())
                ? SCALE * 0.64F : SCALE;
        poseStack.scale(scale, scale, scale);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                crystal,
                ItemDisplayContext.NONE,
                LightTexture.FULL_BRIGHT,
                packedOverlay,
                poseStack,
                buffer,
                level,
                // seed：用方块坐标哈希 ⇒ 同一个台座每帧一致（不会闪烁 ✓），不同台座之间又互不相同 ✓
                (int) pedestal.getBlockPos().asLong());

        poseStack.popPose();
    }
}
