package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 呪蔵（咒藏）的方块实体渲染器：笼子里那团<b>随存量变亮的紫色能量</b>。
 *
 * <h2>为什么能量不写在方块模型里</h2>
 * 原版 JSON 模型是<b>静态</b>的，做不出旋转/浮动/呼吸，所以拆成两部分：
 * <ul>
 *   <li><b>笼子</b>（铁质边框 + 笼条 + 上下盖板）是普通方块模型 {@code block/curse_vault_cage}；</li>
 *   <li><b>能量团</b>由本渲染器画：主团 + 环绕火花，各自带着自己的变换。</li>
 * </ul>
 *
 * <h2>⚠ 怎么拿到"自己画的模型"：走视觉方块，别走独立模型</h2>
 * 第一版用 {@code ModelEvent.RegisterAdditional} + {@code getModel(new ModelResourceLocation(id, "standalone"))}
 * 取独立模型 —— 实机取不到（{@code [呪蔵诊断] core 解析成功=false}），地上只有空笼子。
 * 改成<b>本模组早已验证可行</b>的做法（狱门疆 {@code GourdJailRenderer} 同款）：
 * 注册两个"视觉方块"（{@code curse_vault_core_visual} / {@code curse_vault_spark_visual}，
 * 无物品形态、无碰撞、永不真实生成），blockstate 指向能量模型，
 * 这里用 {@code BlockRenderDispatcher} 拿到 {@link BakedModel} 自己画。
 *
 * <h2>存量 → 亮度（16 档）</h2>
 * 档位在方块状态 {@link CurseVaultBlock#POWER}（0~15，满 = 10 万咒力）里，
 * 由方块实体每 tick 检查、档位变化时同步（服务端是真值，方块状态自动同步给客户端）。
 * 渲染器读它换算成：
 * <ul>
 *   <li><b>亮度</b> 0.38 → 1.0（空的时候是暗紫一小团，满了是一大团发白的能量）；</li>
 *   <li><b>体积</b> ×0.86 → ×1.14、<b>自转速度</b>略快；</li>
 *   <li><b>环绕火花数量</b> 1 → 4 颗。</li>
 * </ul>
 * 方块自身也会发光（{@code lightLevel} = 档位），所以满仓的呪蔵能当光源用。
 *
 * <p>能量用 {@link LightTexture#FULL_BRIGHT} 画：它是自发光的，不该被环境光照暗。
 * 模型元素以"方块中心为 8"（core 4~12、spark 7~9），而渲染坐标系以方块角(0,0,0)为原点、
 * 单位是格，所以"绕中心变换"要先平移到中心、变换完再平移回去。
 */
public class CurseVaultRenderer implements BlockEntityRenderer<CurseVaultBlockEntity> {

    /** 主能量团自转速度（度/tick）：1.5 ≈ 16 秒一圈，慢到能看清旋转又不晃眼（会随存量略加快） */
    private static final float CORE_SPIN = 1.5F;
    /** 环绕火花公转速度（度/tick，负号 = 反向） */
    private static final float SPARK_SPIN = -3.6F;
    /** 环绕半径（1/16 格），小于笼内壁（离中心约 6/16） */
    private static final float SPARK_RADIUS = 4.8F;
    /** 上下浮动幅度（格）与周期（tick） */
    private static final float BOB = 0.05F;
    private static final float BOB_PERIOD = 52.0F;
    /** 呼吸缩放幅度与周期（tick） */
    private static final float BREATHE = 0.07F;
    private static final float BREATHE_PERIOD = 34.0F;
    /** 环绕火花数量上限（存量满时） */
    private static final int SPARKS = 4;

    /** 模型缺失只警告一次（免得每帧刷屏）：静默 return 正是第一版查不出问题的原因 */
    private static final AtomicBoolean WARNED = new AtomicBoolean(false);

    public CurseVaultRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(CurseVaultBlockEntity vault, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        if (ModBlocks.CURSE_VAULT_CORE_VISUAL == null || ModBlocks.CURSE_VAULT_CORE_VISUAL.get() == null) {
            if (WARNED.compareAndSet(false, true)) {
                TinkersNewlife.LOGGER.warn("[呪蔵] 能量视觉方块未注册，笼内能量不会绘制");
            }
            return;
        }
        BlockState coreState = ModBlocks.CURSE_VAULT_CORE_VISUAL.get().defaultBlockState();
        BlockState sparkState = ModBlocks.CURSE_VAULT_SPARK_VISUAL.get().defaultBlockState();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        BakedModel missing = mc.getModelManager().getMissingModel();
        BakedModel coreModel = dispatcher.getBlockModel(coreState);
        if (coreModel == missing) {
            if (WARNED.compareAndSet(false, true)) {
                TinkersNewlife.LOGGER.warn("[呪蔵] 能量模型缺失（blockstates/curse_vault_core_visual.json → block/curse_vault_core），笼内能量不会绘制");
            }
            return;
        }
        BakedModel sparkModel = dispatcher.getBlockModel(sparkState);

        Level level = vault.getLevel();
        float t = (level == null ? 0.0F : level.getGameTime()) + partialTick;

        // ---- 存量档位（0~15）→ 亮度 / 体积 / 火花数量 ----
        BlockState vaultState = vault.getBlockState();
        int powerLevel = vaultState.hasProperty(CurseVaultBlock.POWER)
                ? vaultState.getValue(CurseVaultBlock.POWER) : 0;
        float ratio = powerLevel / (float) CurseVaultBlock.MAX_POWER_LEVEL;   // 0~1

        float bob = Mth.sin(t / BOB_PERIOD * Mth.TWO_PI) * BOB;
        float breathe = 1.0F + BREATHE * Mth.sin(t / BREATHE_PERIOD * Mth.TWO_PI);
        float bright = 0.38F + 0.62F * ratio;          // 空 → 满：暗紫 → 发白
        float coreSize = 0.86F + 0.28F * ratio;

        // ---- 主能量团：绕方块中心自转 + 浮动 + 呼吸 ----
        pose.pushPose();
        pose.translate(0.5D, 0.5D + bob, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(t * (CORE_SPIN + 0.9F * ratio)));
        pose.scale(breathe * coreSize, breathe * coreSize, breathe * coreSize);
        pose.translate(-0.5D, -0.5D, -0.5D);
        draw(pose, buffer, coreModel, coreState, packedOverlay, bright);
        pose.popPose();

        // ---- 环绕火花：数量随存量增加（1 → 4 颗），反向公转，相位等分 ----
        int sparks = sparkCountFor(powerLevel);
        if (sparkModel != missing) {
            for (int i = 0; i < sparks; i++) {
                float size = 1.0F + 0.28F * Mth.sin((t + i * 9.0F) / (BREATHE_PERIOD * 0.8F) * Mth.TWO_PI);
                pose.pushPose();
                pose.translate(0.5D, 0.5D + bob * 0.6D, 0.5D);
                pose.mulPose(Axis.YP.rotationDegrees(t * SPARK_SPIN + i * (360.0F / sparks)));
                pose.translate(SPARK_RADIUS / 16.0D, 0.0D, 0.0D);
                pose.scale(size, size, size);
                pose.translate(-0.5D, -0.5D, -0.5D);
                draw(pose, buffer, sparkModel, sparkState, packedOverlay, Math.min(1.0F, bright + 0.12F));
                pose.popPose();
            }
        }
    }

    /** 火花数量随档位递增：0~3 档 1 颗、4~7 档 2 颗、8~11 档 3 颗、12 档以上 4 颗 */
    private static int sparkCountFor(int powerLevel) {
        if (powerLevel <= 3) return 1;
        if (powerLevel <= 7) return 2;
        if (powerLevel <= 11) return 3;
        return SPARKS;
    }

    /** 画一个方块模型：自发光的能量 → 满亮度；{@code bright} 是存量决定的明暗倍率 */
    private static void draw(PoseStack pose, MultiBufferSource buffer, BakedModel model, BlockState state,
                             int packedOverlay, float bright) {
        ModelBlockRenderer renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        renderer.renderModel(pose.last(), buffer.getBuffer(RenderType.solid()), state, model,
                bright, bright, bright, LightTexture.FULL_BRIGHT, packedOverlay);
    }

    /** 呪蔵给大一点的可见距离（默认 64 也够，显式写上便于以后调） */
    @Override
    public int getViewDistance() {
        return 64;
    }
}
