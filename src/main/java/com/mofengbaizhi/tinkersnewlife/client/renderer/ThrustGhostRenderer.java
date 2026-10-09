package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * ⭐ §1166 <b>突刺残影的渲染</b>（用户口径 ✓：
 * 「**渲染时遍历该实体的残影列表、按存活进度计算alpha做淡出，并对每个残影用同一套模型再画一遍**」✓）。
 *
 * <h2>⭐ 挂点：`RenderLevelStageEvent.AFTER_ENTITIES` ✓</h2>
 * ⭐ 残影要在 ⭐ **实体都画完、还没画粒子/云** 的时候补画 ✗ ⇒ ⭐ 挑 `AFTER_ENTITIES` ✓
 * （⭐ 这时 `poseStack` 还在世界坐标系 ✓ ⭐ 可以直接 `translate` 到残影位置 ✓）。
 *
 * <h2>⚠⚠ 关键难点：**不透明渲染类型下 alpha 不会混合** ✗</h2>
 * ⭐ 玩家模型默认走 ⭐ `entityCutoutNoCull(...)` ✗ ⭐（**带 alpha 测试但不混合** ✓）
 * ⇒ ⚠ 直接 `setShaderColor(1,1,1,alpha)` **根本不会变淡** ✗ ✓（⭐ 只是白画一遍 ✓）
 * ⇒ ⭐⭐ 解法：⭐ **把 `MultiBufferSource` 包一层** ✗ —— ⭐ 无论渲染器请求哪种类型 ✓
 * ⭐ 一律换成 ⭐ **`RenderType.entityTranslucent(皮肤贴图)`** ✓ ⇒ ⭐ 这个类型**是混合的** ✓
 * ⇒ ⭐ 这时 `setShaderColor` 的 alpha 才**真的**起作用 ✓ ✓。
 * <p>⚠ 皮肤贴图从 ⭐ `AbstractClientPlayer#getSkinTextureLocation()` 拿 ✓
 * （⭐ 拿不到就退回默认皮肤 ✓ ⭐ 绝不能让 `RenderType` 的贴图为 null ✗ 会崩 ✓）。
 *
 * <h2>⚠ 两个必须还原的东西</h2>
 * <ol>
 *   <li>⭐ `RenderSystem.setShaderColor` ✗ ⇒ ⭐ 画完必须还原成 `(1,1,1,1)` ✓
 *       ⚠ 否则**后面所有东西**都会带上残影的透明度 ✗（⭐ 这是最典型的串味事故 ✓）；</li>
 *   <li>⭐ `PoseStack` ✗ ⇒ ⭐ 每个残影 `push` 就必须 `pop` ✓。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID,
        value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ThrustGhostRenderer {

    private ThrustGhostRenderer() {
    }

    /** ⚠ **临时探针**：已经报过"画出一条"的那些残影 ✓（⭐ 按 bornTick 去重 ✗ 免得每帧刷屏 ✓） */
    private static final java.util.Set<Integer> PROBED = new java.util.HashSet<>();

    /** ⭐ 残影最高不透明度 ✓（⭐ 不能跟本体一样实 ✗ 那样看着像两个玩家 ✓） */
    private static final float MAX_ALPHA = 0.45F;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        var ghosts = ThrustGhostCache.alive();
        if (ghosts.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        try {
            float partialTick = event.getPartialTick();
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

            for (ThrustGhostCache.Ghost ghost : ghosts) {
                Entity entity = mc.level.getEntity(ghost.entityId);
                // ⚠ 实体已经不在客户端了 ⇒ ⭐ 这条残影没意义 ✓ 丢掉 ✓
                if (!(entity instanceof AbstractClientPlayer player)) {
                    continue;
                }
                float progress = ghost.progress(player.tickCount);
                if (progress >= 1.0F) {
                    continue;
                }
                // ⭐ 按存活进度淡出 ✓（⭐ 线性 ✓ 越老越淡 ✓）
                float alpha = MAX_ALPHA * (1.0F - progress);
                if (alpha <= 0.01F) {
                    continue;
                }

                // ⚠ 半透明实体类型 ⇒ ⭐ alpha 才会真的混合 ✓（见类注释 ✓）
                RenderType ghostType = RenderType.entityTranslucent(player.getSkinTextureLocation());
                MultiBufferSource ghostBuffer = type -> buffer.getBuffer(ghostType);

                poseStack.pushPose();
                try {
                    // ⚠⚠ **不要再自己 translate** ✗ —— ⭐ `EntityRenderDispatcher#render` 的
                    //   `x/y/z` 参数**就是"相机相对坐标"** ✗（⭐ 它内部自己会 `translate` ✓）
                    //   ⚠ 我第一版既 `translate` 又传 `(0,0,0)` ✗ ⇒ ⭐ **双重偏移** ✓
                    //   ⇒ ⭐ 现在改成 ⭐ **不 translate ＋ 直接传"残影位置 − 相机位置"** ✓
                    //     （⭐ 与 {@code LevelRenderer} 调用渲染器的写法完全一致 ✓）。
                    var cam = event.getCamera().getPosition();

                    int light = LevelRenderer.getLightColor(mc.level,
                            net.minecraft.core.BlockPos.containing(ghost.x, ghost.y, ghost.z));

                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                    try {
                        // ⭐⭐ **用同一套模型再画一遍**（用户口径 ✓）✗
                        //   ⭐ yaw 仍传广播来的值 ✓ —— ⚠ 上一版我传 0 是因为自己转了 ✗ 现在不转了 ✓
                        dispatcher.render(player,
                                ghost.x - cam.x, ghost.y - cam.y, ghost.z - cam.z,
                                ghost.yaw, partialTick, poseStack, ghostBuffer, light);
                    } finally {
                        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                        RenderSystem.disableBlend();
                    }
                } finally {
                    poseStack.popPose();
                }
                // ⚠ **临时探针**（⭐ 定位完删 ✗）：⭐ 每个残影最多记一次 ✓ 免得刷屏 ✓
                if (com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.isDebugEnabled()
                        || !PROBED.contains(ghost.bornTick)) {
                    PROBED.add(ghost.bornTick);
                    if (PROBED.size() > 64) {
                        PROBED.clear();
                    }
                    com.mofengbaizhi.tinkersnewlife.TinkersNewlife.LOGGER.info(
                            "[突刺残影·探针] 画出一条 进度={} alpha={} 位置=({},{},{})",
                            String.format(java.util.Locale.ROOT, "%.2f", progress),
                            String.format(java.util.Locale.ROOT, "%.2f", alpha),
                            String.format(java.util.Locale.ROOT, "%.1f", ghost.x),
                            String.format(java.util.Locale.ROOT, "%.1f", ghost.y),
                            String.format(java.util.Locale.ROOT, "%.1f", ghost.z));
                }
            }
            // ⭐ 残影用的 buffer 收个尾 ✓（⭐ 不然半透明批次可能不落地 ✓）
            buffer.endBatch();
        } catch (Throwable ignored) {
            // ⭐ 渲染出错绝不能崩客户端 ✗
        }
    }
}
