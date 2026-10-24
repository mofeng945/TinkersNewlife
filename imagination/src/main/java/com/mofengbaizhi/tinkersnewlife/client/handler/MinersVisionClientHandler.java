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
 * TNL841 矿工视野：★矿石**贴图高亮**（★透墙 ✓ 仅本人可见 ✓）
 *
 * <h2>为何不是线框（★用户口径 ✓）</h2>
 * 用户：“**不是线框，我要纹理高亮**” ✗
 * ⇒ ★取每个矿石方块的**粒子贴图**（★即它自己的贴图 ✓），
 * 把它当作**发光方块**重画一遍 ✓ 并关掉深度测试 ⇒ **透墙看到矿石贴图** ✓✓
 *
 * <h2>实现要点</h2>
 * <ul>
 *   <li>★每 10 tick 扫一次并缓存命中的矿石坐标 ✓（★不每帧扫 ✗）；</li>
 *   <li>★贴图取 {@code BlockModelShaper#getParticleIcon} ✓ 缓存到方块状态 ✓；</li>
 *   <li>★全亮（{@code 0xF000F0} ✓）+ 半透明 ✓ + 关深度测试 ✓ ⇒ 透墙可见 ✓；</li>
 *   <li>★全在**客户端** ✓ ⇒ 只有自己看得到 ✓ 服务器不参与 ✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MinersVisionClientHandler {

    private static final int RANGE_PER_LEVEL = 5;
    private static final int SCAN_INTERVAL = 10;
    /** ★用户口径：“不要改色调，就纯粹把亮度提高” ✓ ⇒ **纯白 + 全亮** ✓ */
    private static final float R = 1.0F, G = 1.0F, B = 1.0F, A = 0.92F;

    private static final TagKey<Block> ORE_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("forge", "ores"));
    private static final TagKey<Block> ORE_TAG_ALT =
            TagKey.create(net.minecraft.core.registries.Registries.BLOCK, new ResourceLocation("forge", "ore"));

    /**
     * ★★★ 自定义 RenderType：**自带 {@code NO_DEPTH_TEST}** ✓
     *
     * <p>为什么之前透墙失效：{@code RenderType} 在**真正 draw 的时候**会重新
     * 设置自己的渲染状态（★包括深度测试 ✗）⇒ 外面调的
     * {@code RenderSystem.disableDepthTest()} **会被覆盖掉** ✗ ⇒ 无法透墙 ✗✗
     * ★所以必须自己造一个**深度测试关闭**的类型 ✓。
     */
    private static RenderType glowTypeCache = null;
    private static boolean glowTypeTried = false;

    /** ★★ 懒加载：静态初始化一旦抛异常 ✗ 整个类都会失效 ⇒ 什么都不画 ✗ */
    private static RenderType glowType() {
        if (glowTypeTried) {
            return glowTypeCache;
        }
        glowTypeTried = true;
        try {
            glowTypeCache = RenderType.create(
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
                            .setDepthTestState(TnlState.NO_DEPTH)
                            .setCullState(TnlState.NO_CULL_)
                            .setLightmapState(TnlState.LIGHTMAP_)
                            .setOverlayState(TnlState.OVERLAY_)
                            .createCompositeState(false));
        } catch (Throwable tt) {
            glowTypeCache = RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS);
        }
        return glowTypeCache;
    }

    /**
     * ★★ 专用状态子类：1.20.1 里 {@code RenderStateShard} 的这些常量是
     * **protected** ✗ 外部类拿不到 ⇒ ★在子类里取出来 ✓。
     */
    private static class TnlState extends net.minecraft.client.renderer.RenderStateShard {
        private TnlState() {
            super("tnl_state", () -> {
            }, () -> {
            });
        }

        static final DepthTestStateShard NO_DEPTH = NO_DEPTH_TEST;                  // ★★ 透墙关键 ✓
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
            // TNL844 ★★ 最后一招：帧末渲染 + **先清深度缓冲** + 关深度测试 ✓
            //   （★之前 drawn>0 但仍被遮 ⇒ 说明单靠 RenderType 的 NO_DEPTH 没真正奏效 ✗）
            // TNL845 ★★ 真正的透墙做法：**绕开 RenderType** ✓
            //   ★RenderType 在 draw 时会重新设置深度状态 ✗ ⇒ 改成直接用 BufferBuilder 手绘 ✓
            //   ★此时只有我们自己设的 RenderSystem 状态生效 ✓ ⇒ disableDepthTest 真正生效 ✓
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
            com.mojang.blaze3d.systems.RenderSystem.setShader(
                    net.minecraft.client.renderer.GameRenderer::getRendertypeEntityTranslucentShader);
            com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
            com.mojang.blaze3d.vertex.BufferBuilder bb =
                    com.mojang.blaze3d.vertex.Tesselator.getInstance().getBuilder();
            bb.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                    com.mojang.blaze3d.vertex.DefaultVertexFormat.NEW_ENTITY);
            VertexConsumer vc = bb;
            PoseStack.Pose last = pose.last();
            int drawn = 0;
            for (Hit hit : CACHE) {
                try {
                    if (hit.sprite() == null) {
                        continue;
                    }
                    tnl$cube(vc, last, hit.pos(), hit.sprite());
                    drawn++;
                } catch (Throwable ignoredOne) {
                    // TNL842 ★单块异常不影响其它 ✓
                }
            }
            com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(bb.end());
            com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
            com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
            pose.popPose();
        } catch (Throwable ignored) {
        }
    }

    /** ★把一个坐标画成用该贴图的发光方块 ✓（★六面同贴图 ✓） */
    private static void tnl$cube(VertexConsumer vc, PoseStack.Pose pose, BlockPos pos, TextureAtlasSprite s) {
        float x0 = pos.getX() - 0.002F, y0 = pos.getY() - 0.002F, z0 = pos.getZ() - 0.002F;
        float x1 = pos.getX() + 1.002F, y1 = pos.getY() + 1.002F, z1 = pos.getZ() + 1.002F;
        float u0 = s.getU0(), u1 = s.getU1(), v0 = s.getV0(), v1 = s.getV1();
        int light = 0xF000F0;   // ★全亮 ✓
        // ★-北 z0
        tnl$quad(vc, pose, x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, u0, u1, v0, v1, light, 0, 0, -1);
        // ★+南 z1
        tnl$quad(vc, pose, x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, u0, u1, v0, v1, light, 0, 0, 1);
        // ★-西 x0
        tnl$quad(vc, pose, x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, u0, u1, v0, v1, light, -1, 0, 0);
        // ★+东 x1
        tnl$quad(vc, pose, x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, u0, u1, v0, v1, light, 1, 0, 0);
        // ★上 y1
        tnl$quad(vc, pose, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, u1, v0, v1, light, 0, 1, 0);
        // ★下 y0
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
            // TNL1305 没有 NBT 的匠魂栈不要 from()（会刷 tconstruct 的 non-modifiable 警告）
            if (!ToolStack.isInitialized(held)) {
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
                        TextureAtlasSprite sprite;
                        try {
                            sprite = spriteCache.computeIfAbsent(state.getBlock(),
                                    b -> shaper.getParticleIcon(b.defaultBlockState()));
                        } catch (Throwable ignoredSprite) {
                            sprite = null;   // TNL842 ★取不到贴图就记 null ✗ 不崩 ✓
                        }
                        CACHE.add(new Hit(pos.immutable(), sprite));
                    }
                }
            }
        }
    }
}
