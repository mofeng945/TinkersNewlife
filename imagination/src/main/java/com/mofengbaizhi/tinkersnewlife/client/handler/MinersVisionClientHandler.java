package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.List;

/**
 * TNL841 \u77ff\u5de5\u89c6\u91ce\uff1a\u2605\u77ff\u77f3**\u8d34\u56fe\u9ad8\u4eae**\uff08\u2605\u900f\u5899 \u2713 \u4ec5\u672c\u4eba\u53ef\u89c1 \u2713\uff09
 *
 * <h2>\u4e3a\u4f55\u4e0d\u662f\u7ebf\u6846\uff08\u2605\u7528\u6237\u53e3\u5f84 \u2713\uff09</h2>
 * \u7528\u6237\uff1a\u201c**\u4e0d\u662f\u7ebf\u6846\uff0c\u6211\u8981\u7eb9\u7406\u9ad8\u4eae**\u201d \u2717
 * \u21d2 \u2605\u53d6\u6bcf\u4e2a\u77ff\u77f3\u65b9\u5757\u7684**\u7c92\u5b50\u8d34\u56fe**\uff08\u2605\u5373\u5b83\u81ea\u5df1\u7684\u8d34\u56fe \u2713\uff09\uff0c
 * \u628a\u5b83\u5f53\u4f5c**\u53d1\u5149\u65b9\u5757**\u91cd\u753b\u4e00\u904d \u2713 \u5e76\u5173\u6389\u6df1\u5ea6\u6d4b\u8bd5 \u21d2 **\u900f\u5899\u770b\u5230\u77ff\u77f3\u8d34\u56fe** \u2713\u2713
 *
 * <h2>\u5b9e\u73b0\u8981\u70b9</h2>
 * <ul>
 *   <li>\u2605\u6bcf 10 tick \u626b\u4e00\u6b21\u5e76\u7f13\u5b58\u547d\u4e2d\u7684\u77ff\u77f3\u5750\u6807 \u2713\uff08\u2605\u4e0d\u6bcf\u5e27\u626b \u2717\uff09\uff1b</li>
 *   <li>\u2605\u8d34\u56fe\u53d6 {@code BlockModelShaper#getParticleIcon} \u2713 \u7f13\u5b58\u5230\u65b9\u5757\u72b6\u6001 \u2713\uff1b</li>
 *   <li>\u2605\u5168\u4eae\uff08{@code 0xF000F0} \u2713\uff09\u002b \u534a\u900f\u660e \u2713 \u002b \u5173\u6df1\u5ea6\u6d4b\u8bd5 \u2713 \u21d2 \u900f\u5899\u53ef\u89c1 \u2713\uff1b</li>
 *   <li>\u2605\u5168\u5728**\u5ba2\u6237\u7aef** \u2713 \u21d2 \u53ea\u6709\u81ea\u5df1\u770b\u5f97\u5230 \u2713 \u670d\u52a1\u5668\u4e0d\u53c2\u4e0e \u2713\u3002</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MinersVisionClientHandler {

    private static final int RANGE_PER_LEVEL = 5;
    private static final int SCAN_INTERVAL = 10;
    /** \u2605\u7528\u6237\u53e3\u5f84\uff1a\u201c\u4e0d\u8981\u6539\u8272\u8c03\uff0c\u5c31\u7eaf\u7cb9\u628a\u4eae\u5ea6\u63d0\u9ad8\u201d \u2713 \u21d2 **\u7eaf\u767d \u002b \u5168\u4eae** \u2713 */
    private static final float R = 1.0F, G = 1.0F, B = 1.0F, A = 0.92F;

    private static final TagKey<Block> ORE_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("forge", "ores"));
    private static final TagKey<Block> ORE_TAG_ALT =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("forge", "ore"));

    /**
     * \u2605\u2605\u2605 \u81ea\u5b9a\u4e49 RenderType\uff1a**\u81ea\u5e26 {@code NO_DEPTH_TEST}** \u2713
     *
     * <p>\u4e3a\u4ec0\u4e48\u4e4b\u524d\u900f\u5899\u5931\u6548\uff1a{@code RenderType} \u5728**\u771f\u6b63 draw \u7684\u65f6\u5019**\u4f1a\u91cd\u65b0
     * \u8bbe\u7f6e\u81ea\u5df1\u7684\u6e32\u67d3\u72b6\u6001\uff08\u2605\u5305\u62ec\u6df1\u5ea6\u6d4b\u8bd5 \u2717\uff09\u21d2 \u5916\u9762\u8c03\u7684
     * {@code RenderSystem.disableDepthTest()} **\u4f1a\u88ab\u8986\u76d6\u6389** \u2717 \u21d2 \u65e0\u6cd5\u900f\u5899 \u2717\u2717
     * \u2605\u6240\u4ee5\u5fc5\u987b\u81ea\u5df1\u9020\u4e00\u4e2a**\u6df1\u5ea6\u6d4b\u8bd5\u5173\u95ed**\u7684\u7c7b\u578b \u2713\u3002
     */
    private static final RenderType ORE_GLOW = RenderType.create(
            "tnl_ore_glow",
            com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY,
            com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
            256, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(new net.minecraft.client.renderer.RenderStateShard.ShaderStateShard(
                            net.minecraft.client.renderer.GameRenderer::getRendertypeEntityTranslucentShader))
                    .setTextureState(new net.minecraft.client.renderer.RenderStateShard.TextureStateShard(
                            TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TnlState.TRANSLUCENT)
                    .setDepthTestState(TnlState.NO_DEPTH)   // \u2605\u2605 \u900f\u5899\u5173\u952e \u2713
                    .setCullState(TnlState.NO_CULL_)
                    .setLightmapState(TnlState.LIGHTMAP_)
                    .setOverlayState(TnlState.OVERLAY_)
                    .createCompositeState(false));

    /**
     * \u2605\u2605 \u4e13\u7528\u72b6\u6001\u5b50\u7c7b\uff1a1.20.1 \u91cc {@code RenderStateShard} \u7684\u8fd9\u4e9b\u5e38\u91cf\u662f
     * **protected** \u2717 \u5916\u90e8\u7c7b\u62ff\u4e0d\u5230 \u21d2 \u2605\u5728\u5b50\u7c7b\u91cc\u53d6\u51fa\u6765 \u2713\u3002
     */
    private static class TnlState extends net.minecraft.client.renderer.RenderStateShard {
        private TnlState() {
            super("tnl_state", () -> {
            }, () -> {
            });
        }

        static final DepthTestStateShard NO_DEPTH = NO_DEPTH_TEST;                  // \u2605\u2605 \u900f\u5899\u5173\u952e \u2713
        static final TransparencyStateShard TRANSLUCENT = TRANSLUCENT_TRANSPARENCY;
        static final CullStateShard NO_CULL_ = NO_CULL;
        static final LightmapStateShard LIGHTMAP_ = LIGHTMAP;
        static final OverlayStateShard OVERLAY_ = OVERLAY;
    }

    private record Hit(BlockPos pos, TextureAtlasSprite sprite) {
    }

    private static final List<Hit> CACHE = new ArrayList<>();
    private static int lastScan = -1000;
    private static int lastLevel = -1;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        try {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                return;
            }
            int level = heldLevel(mc);
            if (level <= 0) {
                if (!CACHE.isEmpty()) {
                    CACHE.clear();
                }
                lastLevel = -1;
                return;
            }
            int now = mc.player.tickCount;
            if (level != lastLevel || now - lastScan >= SCAN_INTERVAL) {
                rescan(mc, level);
                lastScan = now;
                lastLevel = level;
            }
            if (CACHE.isEmpty()) {
                return;
            }
            PoseStack pose = event.getPoseStack();
            Vec3 cam = event.getCamera().getPosition();
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(ORE_GLOW);
            PoseStack.Pose last = pose.last();
            for (Hit hit : CACHE) {
                tnl$cube(vc, last, hit.pos(), hit.sprite());
            }
            buffers.endBatch(ORE_GLOW);
            pose.popPose();
        } catch (Throwable ignored) {
        }
    }

    /** \u2605\u628a\u4e00\u4e2a\u5750\u6807\u753b\u6210\u7528\u8be5\u8d34\u56fe\u7684\u53d1\u5149\u65b9\u5757 \u2713\uff08\u2605\u516d\u9762\u540c\u8d34\u56fe \u2713\uff09 */
    private static void tnl$cube(VertexConsumer vc, PoseStack.Pose pose, BlockPos pos, TextureAtlasSprite s) {
        float x0 = pos.getX() - 0.002F, y0 = pos.getY() - 0.002F, z0 = pos.getZ() - 0.002F;
        float x1 = pos.getX() + 1.002F, y1 = pos.getY() + 1.002F, z1 = pos.getZ() + 1.002F;
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        int light = 0xF000F0;   // \u2605\u5168\u4eae \u2713
        // \u2605-\u5317 z0
        tnl$quad(vc, pose, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, u0, u1, v0, v1, light, 0, 0, -1);
        // \u2605+\u5357 z1
        tnl$quad(vc, pose, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, u0, u1, v0, v1, light, 0, 0, 1);
        // \u2605-\u897f x0
        tnl$quad(vc, pose, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, u0, u1, v0, v1, light, -1, 0, 0);
        // \u2605+\u4e1c x1
        tnl$quad(vc, pose, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, u0, u1, v0, v1, light, 1, 0, 0);
        // \u2605\u4e0a y1
        tnl$quad(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, u1, v0, v1, light, 0, 1, 0);
        // \u2605\u4e0b y0
        tnl$quad(vc, pose, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, u0, u1, v0, v1, light, 0, -1, 0);
    }

    private static void tnl$quad(VertexConsumer vc, PoseStack.Pose pose,
                                 float x1, float y1, float z1, float x2, float y2, float z2,
                                 float x3, float y3, float z3, float x4, float y4, float z4,
                                 float u0, float u1, float v0, float v1, int light,
                                 float nx, float ny, float nz) {
        vc.vertex(pose.pose(), x1, y1, z1).color(R, G, B, A).uv(u0, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
        vc.vertex(pose.pose(), x2, y2, z2).color(R, G, B, A).uv(u0, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
        vc.vertex(pose.pose(), x3, y3, z3).color(R, G, B, A).uv(u1, v1)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
        vc.vertex(pose.pose(), x4, y4, z4).color(R, G, B, A).uv(u1, v0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), nx, ny, nz).endVertex();
    }

    private static int heldLevel(Minecraft mc) {
        try {
            net.minecraft.world.item.ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty()) {
                return 0;
            }
            ToolStack tool = ToolStack.from(held);
            if (tool == null) {
                return 0;
            }
            return tool.getModifierLevel(Modifiers.MINERS_VISION.getId());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void rescan(Minecraft mc, int level) {
        CACHE.clear();
        int radius = Math.min(level * RANGE_PER_LEVEL, 40);
        BlockPos center = mc.player.blockPosition();
        var shaper = mc.getBlockRenderer().getBlockModelShaper();
        java.util.Map<Block, TextureAtlasSprite> spriteCache = new java.util.HashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(ORE_TAG) || state.is(ORE_TAG_ALT)) {
                        TextureAtlasSprite sprite = spriteCache.computeIfAbsent(state.getBlock(),
                                b -> shaper.getParticleIcon(b.defaultBlockState()));
                        CACHE.add(new Hit(pos.immutable(), sprite));
                    }
                }
            }
        }
    }
}
