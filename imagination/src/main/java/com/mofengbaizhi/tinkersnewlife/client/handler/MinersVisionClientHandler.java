package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.List;

/**
 * TNL840 \u77ff\u5de5\u89c6\u91ce\uff1a\u2605\u77ff\u77f3**\u900f\u5899\u9ad8\u4eae**\uff08\u2605\u4ec5\u672c\u4eba\u53ef\u89c1 \u2713\uff09\u3002
 *
 * <h2>\u53e3\u5f84</h2>
 * \u6301\u6709\u5e26 {@code miners_vision} \u7684**\u91c7\u6398\u5de5\u5177**\u65f6 \u2713\uff0c\u628a\u8303\u56f4\u5185\u6240\u6709
 * {@code forge:ores}\uff08\u2605\u517c\u5bb9 {@code forge:ore} \u2713\uff09\u7684\u65b9\u5757\u753b\u6210\u7ebf\u6846 \u2713\uff0c
 * \u5e76\u5173\u6389\u6df1\u5ea6\u6d4b\u8bd5 \u21d2 **\u900f\u8fc7\u65b9\u5757\u4e5f\u80fd\u770b\u5230** \u2713\u2713
 *
 * <p>\u2605\u6e32\u67d3\u5168\u5728**\u5ba2\u6237\u7aef**\u2713 \u21d2 \u2605\u53ea\u6709\u81ea\u5df1\u770b\u5f97\u5230 \u2713 \u670d\u52a1\u5668\u4e0d\u53c2\u4e0e \u2713\u3002
 * <p>\u2605\u6027\u80fd\uff1a**\u6bcf 10 tick \u626b\u4e00\u6b21\u5e76\u7f13\u5b58** \u2713\uff08\u2605\u4e0d\u6bcf\u5e27\u626b \u2717\uff09\uff1b\u7b49\u7ea7\u53d8\u4e86\u7acb\u5373\u91cd\u626b \u2713\u3002
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MinersVisionClientHandler {

    /** \u2605\u8303\u56f4\uff1a\u6bcf\u7ea7 5 \u683c \u2713 */
    private static final int RANGE_PER_LEVEL = 5;
    /** \u2605\u626b\u63cf\u95f4\u9694\uff08tick\uff09\u2713 */
    private static final int SCAN_INTERVAL = 10;
    /** \u2605\u6bcf\u7ea7\u989c\u8272\uff08\u2605\u9752\u7eff \u2713\uff09 */
    private static final float R = 0.31F, G = 0.82F, B = 0.77F, A = 0.9F;

    private static final TagKey<Block> ORE_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("forge", "ores"));
    private static final TagKey<Block> ORE_TAG_ALT =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("forge", "ore"));

    private static final List<BlockPos> CACHE = new ArrayList<>();
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
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();   // \u2605\u2605 \u900f\u5899\u5173\u952e \u2713
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer vc = buffers.getBuffer(RenderType.lines());
            for (BlockPos pos : CACHE) {
                LevelRenderer.renderLineBox(pose, vc, new AABB(pos).inflate(0.002D), R, G, B, A);
            }
            buffers.endBatch(RenderType.lines());
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            pose.popPose();
        } catch (Throwable ignored) {
        }
    }

    /** \u2605\u53d6\u4e3b\u624b\u5de5\u5177\u4e0a\u8be5\u7279\u6027\u7684\u7b49\u7ea7 \u2713 */
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

    /** \u2605\u626b\u63cf\u8303\u56f4\u5185\u7684\u77ff\u77f3\u5e76\u7f13\u5b58 \u2713 */
    private static void rescan(Minecraft mc, int level) {
        CACHE.clear();
        int radius = Math.min(level * RANGE_PER_LEVEL, 40);
        BlockPos center = mc.player.blockPosition();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(ORE_TAG) || state.is(ORE_TAG_ALT)) {
                        CACHE.add(pos.immutable());
                    }
                }
            }
        }
    }
}
