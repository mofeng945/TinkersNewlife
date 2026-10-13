package com.mofengbaizhi.tinkersnewlife.client.render;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;

/**
 * TNL942 \u2605\u2605 \u661f\u9668\u60b2\u53f9\u7684**\u9ed1\u5f13\u8eab\u661f\u7a7a** \u2713
 *
 * <p>\u2605\u7528\u6237\u53e3\u5f84\uff1a\u2605"\u516b\u8363\u516b\u803b\u8bb0\u4f4f\u4e86\u5417\uff1f\u6709\u73b0\u6210\u6210\u529f\u6848\u4f8b\u4e3a\u4ec0\u4e48\u4e0d\u67e5\u6e05\u695a\u518d\u505a" \u2713
 *
 * <p>\u2605\u6240\u4ee5\u8fd9\u6b21**\u628a\u53c2\u8003\u5b9e\u73b0\u8bfb\u5b8c\u4e86** \u2713\uff1a
 * ``goeticlegacy`` \u7684 ``client/render/AnomalyItemRenderer`` \u2713 \u2014\u2014
 * \u2605\u5b83\u7684\u8def\u5f84\u662f \u2713\uff1a
 * <ol>
 *   <li>\u2605``extends BlockEntityWithoutLevelRenderer`` \u2713\uff08\u2605**\u4e0d\u662f**\u6df7\u5165
 *       ``ItemBlockRenderTypes`` \u2717 \u2014\u2014 \u2605\u6211\u4e0a\u4e00\u7248\u5c31\u9519\u5728\u8fd9 \u2717\uff09\uff1b</li>
 *   <li>\u2605``IClientItemExtensions#getCustomRenderer`` \u6302\u4e0a\u5b83 \u2713\uff1b</li>
 *   <li>\u2605``renderByItem`` \u91cc\uff1a\u2605\u53d6\u81ea\u5df1\u7684 ``ShaderInstance`` \u2713
 *       \u2014\u2014\u2605**null \u5c31\u753b\u515c\u5e95\u8d34\u56fe\u5e76 return** \u2713\uff1b</li>
 *   <li>\u2605\u7136\u540e ``endBatch()`` \u21d2 \u2605\u5582 uniform \u21d2 \u2605\u7528\u81ea\u5df1\u7684
 *       ``RenderType`` \u753b\u51e0\u4f55 \u21d2 \u2605``endBatch(type)`` \u2713\u3002</li>
 * </ol>
 *
 * <p>\u2605\u6211\u4eec\u4e0e\u5b83\u7684\u5dee\u522b \u2713\uff1a\u2605\u5b83\u753b\u7684\u662f\u4e00\u4e2a**\u65b9\u5757** \u2713\uff0c
 * \u2605\u6211\u4eec\u753b\u7684\u662f**\u5f13\u7684 baked model \u56db\u8fb9\u5f62** \u2713\uff0c
 * \u2605\u5e76\u4e14\u7740\u8272\u5668\u91cc**\u7528\u8d34\u56fe\u4eae\u5ea6\u505a\u906e\u7f69** \u2713
 * \u21d2 \u2605\u53ea\u6709\u9ed1\u8272\u90e8\u5206\u53d8\u661f\u7a7a \u2713\uff0c\u5176\u4f59\u539f\u6837 \u2713\u3002
 */
public final class StarBodyClient extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation SHADER_ID = new ResourceLocation(TinkersNewlife.MOD_ID, "star_body");
    // TNL966 \u2605\u2605 \u7528\u6237\uff1a\u2605"\u62c9\u5f13\u52a8\u753b\u7684\u7bad\u77e2\u53d8\u900f\u660e\u4e86" \u2713
    //   \u2605\u771f\u56e0 \u2717\uff1a\u2605\u62c9\u5f13\u7528\u7684\u662f\u53e6\u5916\u4e09\u5f20\u8d34\u56fe \u2717
    //     \u2605\u800c\u6211\u628a `Sampler0` \u56fa\u5b9a\u7ed1\u6210\u4e86**\u57fa\u7840\u8d34\u56fe** \u2717
    //     \u21d2 \u2605\u6309\u62c9\u5f13 sprite \u7684 UV \u53bb\u91c7\u57fa\u7840\u8d34\u56fe \u21d2 \u2605\u843d\u5728\u900f\u660e\u533a \u21d2 \u2605\u6574\u628a\u900f\u660e \u2713
    //   \u2605\u73b0\u5728\uff1a\u2605\u76f4\u63a5\u7ed1**\u6574\u5f20\u56fe\u96c6** \u2713 \u21d2 \u2605\u7528\u539f\u59cb\u56fe\u96c6 UV \u91c7\u6837 \u2713
    //     \u21d2 \u2605\u57fa\u7840/\u62c9\u5f13/\u4efb\u610f\u53d8\u4f53\u90fd\u80fd\u53d6\u5230**\u81ea\u5df1\u90a3\u5f20** \u2713\u2713
    private static final ResourceLocation TEX = net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;

    private static ShaderInstance shader;
    private static RenderType renderType;
    private static StarBodyClient INSTANCE;
    private static long TNL$LOG = 0L;
    private static long TNL$LOG2 = 0L;

    private StarBodyClient() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        TinkersNewlife.LOGGER.warn("TNL944 BEWLR constructed");     // ★TNL944 \u63a2\u9488 \u2713
    }

    /** TNL947 \u2605\u4f9b\u6df7\u5165\u7528\uff1a\u2605\u53d6\u6e32\u67d3\u5668\uff08\u2605\u6ca1\u5efa\u5c31\u5efa\uff09\u2713 */
    public static BlockEntityWithoutLevelRenderer rendererOrNull() {
        try {
            return instance();
        } catch (Throwable t) {
            return null;
        }
    }

    /** TNL947 \u2605\u4f9b\u6df7\u5165\u7528\uff1a\u2605\u7740\u8272\u5668\u597d\u4e86\u6ca1 \u2713 */
    public static boolean shaderReady() {
        return shader != null;
    }

    public static StarBodyClient instance() {
        if (INSTANCE == null) {
            INSTANCE = new StarBodyClient();
        }
        return INSTANCE;
    }

    /** \u2605\u6ce8\u518c\u7740\u8272\u5668 \u2713 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        private Shaders() {
        }

        @SubscribeEvent
        public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
            TinkersNewlife.LOGGER.warn("TNL944 shader register called");       // ★TNL944 \u2713
            try {
                event.registerShader(
                        new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.NEW_ENTITY),
                        s -> {
                            shader = s;
                            TinkersNewlife.LOGGER.warn("TNL944 star_body shader compiled ok");
                        });
            } catch (Throwable t) {
                TinkersNewlife.LOGGER.warn("TNL944 star_body shader FAILED: {}", t.toString());
            }
        }
    }

    /** \u2605\u6bcf\u5e27\u5582 ``EffectTime`` \u2713 */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || shader == null) {
                return;
            }
            var u = shader.safeGetUniform("EffectTime");
            if (u != null) {
                u.set((float) (System.currentTimeMillis() % 600000L) / 1000.0F);
            }
        }
    }

    /** TNL960 \u2605\u628a\u8d34\u56fe\u5728\u56fe\u96c6\u91cc\u7684 UV \u8303\u56f4\u544a\u8bc9\u7740\u8272\u5668 \u2713 */
    public static void setUvRemap(float u0, float v0, float su, float sv) {
        try {
            if (shader == null) {
                return;
            }
            var a = shader.safeGetUniform("UvOffset");
            if (a != null) {
                a.set(u0, v0);
            }
            var b = shader.safeGetUniform("UvScale");
            if (b != null) {
                b.set(su, sv);
            }
        } catch (Throwable ignored) {
        }
    }

    /** TNL950 \u2605\u4f9b\u6df7\u5165\u7528\uff1a\u2605\u6ca1\u597d\u5c31\u8fd4\u56de null\uff08\u2605\u9000\u56de\u539f\u7248\uff09\u2713 */
    public static RenderType starBodyOrNull() {
        try {
            return starBody();
        } catch (Throwable t) {
            return null;
        }
    }

    private static RenderType starBody() {
        if (shader == null) {
            return null;
        }
        if (renderType == null) {
            renderType = RenderType.create("tnl_star_body", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
                    256, false, true,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                            .setTextureState(new RenderStateShard.TextureStateShard(TEX, false, false))
                            .createCompositeState(true));
        }
        return renderType;
    }

    /** \u2605\u7ed9\u7269\u54c1\u7528\u7684\u6269\u5c55\uff08\u2605\u5c31\u50cf\u53c2\u8003\u5b9e\u73b0\u90a3\u6837\u6302 BEWLR \u2713\uff09 */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;      // \u2605\u5ef6\u8fdf\u521b\u5efa\uff08\u2605\u540c\u53c2\u8003 \u2713\uff09

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                long tnl$n = System.currentTimeMillis();
                if (tnl$n - TNL$LOG2 > 5000L) {                       // TNL946 \u2605\u6253\u8c03\u7528\u6808 \u2713
                    TNL$LOG2 = tnl$n;
                    StackTraceElement[] tnl$st = new Throwable().getStackTrace();
                    StringBuilder tnl$sb = new StringBuilder("TNL946 getCustomRenderer caller:");
                    for (int tnl$i = 1; tnl$i < Math.min(tnl$st.length, 8); tnl$i++) {
                        tnl$sb.append(" | ").append(tnl$st[tnl$i].getClassName()).append('.')
                             .append(tnl$st[tnl$i].getMethodName()).append(':').append(tnl$st[tnl$i].getLineNumber());
                    }
                    TinkersNewlife.LOGGER.warn(tnl$sb.toString());
                }
                if (this.renderer == null) {
                    this.renderer = StarBodyClient.instance();
                }
                return this.renderer;
            }
        };
    }

    @Override
    public void renderByItem(ItemStack stack, net.minecraft.world.item.ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        // TNL945 \u2605\u2605 \u65e0\u6761\u4ef6\uff08\u2605\u9650\u6d41 1 \u79d2\uff09\u63a2\u9488 \u2713
        long tnl$now = System.currentTimeMillis();
        if (tnl$now - TNL$LOG > 1000L) {
            TNL$LOG = tnl$now;
            TinkersNewlife.LOGGER.warn("TNL945 renderByItem ENTER ctx={} shader={}",
                    context, shader != null);
        }
        Minecraft mc = Minecraft.getInstance();
        RenderType star = starBody();
        if (star == null) {
            // \u2605\u515c\u5e95\uff1a\u2605\u7740\u8272\u5668\u6ca1\u597d \u21d2 \u2605\u7528\u539f\u7248\u7c7b\u578b\u753b \u2713\uff08\u2605\u4e0d\u4f1a\u53d8\u7a7a \u2717\uff09
            renderModel(stack, pose, buffers, light, overlay,
                    ItemBlockRenderTypes.getRenderType(stack, true));
            return;
        }
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource bs ? bs : null;
        // TNL948 \u2605\u2605 \u7528\u6237\u53cd\u9988\uff1a\u2605"\u5f13\u4e0d\u89c1\u4e86" \u2713
        //   \u2605\u4fdd\u5e95 \u2713\uff1a\u2605**\u5148\u7528\u539f\u7248\u7c7b\u578b\u753b\u4e00\u904d** \u2713
        //     \u21d2 \u2605\u54ea\u6015\u661f\u7a7a\u90a3\u4e00\u5c42\u51fa\u95ee\u9898\uff0c\u2605\u5f13\u4e5f\u4e0d\u4f1a\u6d88\u5931 \u2713
        try {
            renderModel(stack, pose, buffers, light, overlay,
                    ItemBlockRenderTypes.getRenderType(stack, true));
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL948 base layer FAILED: {}", t.toString());
        }
        try {
            if (source != null) {
                source.endBatch();
            }
            var u = shader.safeGetUniform("MaskThreshold");
            if (u != null) {
                u.set(0.26F);
            }
            renderModel(stack, pose, buffers, light, overlay, star);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("TNL945 renderByItem FAILED: {}", t.toString());   // \u2605\u4e0d\u518d\u541e\u5f02\u5e38 \u2713
        } finally {
            try {
                if (source != null) {
                    source.endBatch(star);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** \u2605\u628a\u5f13\u7684 baked model \u56db\u8fb9\u5f62\u5199\u5230\u6307\u5b9a\u7c7b\u578b\u91cc \u2713 */
    private static void renderModel(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                    RenderType type) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer ir = mc.getItemRenderer();
        BakedModel model = ir.getModel(stack, mc.level, mc.player, 0);
        VertexConsumer vc = buffers.getBuffer(type);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        RandomSource rnd = RandomSource.create(42L);
        for (Direction dir : Direction.values()) {
            emit(model.getQuads(null, dir, rnd), m, n, vc, light, overlay);
        }
        emit(model.getQuads(null, null, rnd), m, n, vc, light, overlay);
    }

    private static void emit(List<BakedQuad> quads, Matrix4f m, Matrix3f n, VertexConsumer vc, int light, int overlay) {
        for (BakedQuad q : quads) {
            int[] v = q.getVertices();
            int stride = v.length / 4;
            for (int i = 0; i < 4; i++) {
                int o = i * stride;
                vc.vertex(m, Float.intBitsToFloat(v[o]), Float.intBitsToFloat(v[o + 1]), Float.intBitsToFloat(v[o + 2]));
                vc.color(255, 255, 255, 255);
                vc.uv(Float.intBitsToFloat(v[o + 4]), Float.intBitsToFloat(v[o + 5]));
                vc.overlayCoords(overlay);
                vc.uv2(light);
                vc.normal(n, 0.0F, 1.0F, 0.0F);      // TNL948 \u2605\u4e0d\u518d\u4e71\u89e3\u6cd5\u7ebf \u2713
                vc.endVertex();
            }
        }
    }
}
