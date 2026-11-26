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
 * TNL942 ★★ 星陨悲叹的**黑弓身星空** ✓
 *
 * <p>★用户口径：★"八荣八耻记住了吗？有现成成功案例为什么不查清楚再做" ✓
 *
 * <p>★所以这次**把参考实现读完了** ✓：
 * ``goeticlegacy`` 的 ``client/render/AnomalyItemRenderer`` ✓ ——
 * ★它的路径是 ✓：
 * <ol>
 *   <li>★``extends BlockEntityWithoutLevelRenderer`` ✓（★**不是**混入
 *       ``ItemBlockRenderTypes`` ✗ —— ★我上一版就错在这 ✗）；</li>
 *   <li>★``IClientItemExtensions#getCustomRenderer`` 挂上它 ✓；</li>
 *   <li>★``renderByItem`` 里：★取自己的 ``ShaderInstance`` ✓
 *       ——★**null 就画兜底贴图并 return** ✓；</li>
 *   <li>★然后 ``endBatch()`` ⇒ ★喂 uniform ⇒ ★用自己的
 *       ``RenderType`` 画几何 ⇒ ★``endBatch(type)`` ✓。</li>
 * </ol>
 *
 * <p>★我们与它的差别 ✓：★它画的是一个**方块** ✓，
 * ★我们画的是**弓的 baked model 四边形** ✓，
 * ★并且着色器里**用贴图亮度做遮罩** ✓
 * ⇒ ★只有黑色部分变星空 ✓，其余原样 ✓。
 */
public final class StarBodyClient extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation SHADER_ID = new ResourceLocation(TinkersNewlife.MOD_ID, "star_body");
    // TNL966 ★★ 用户：★"拉弓动画的箭矢变透明了" ✓
    //   ★真因 ✗：★拉弓用的是另外三张贴图 ✗
    //     ★而我把 `Sampler0` 固定绑成了**基础贴图** ✗
    //     ⇒ ★按拉弓 sprite 的 UV 去采基础贴图 ⇒ ★落在透明区 ⇒ ★整把透明 ✓
    //   ★现在：★直接绑**整张图集** ✓ ⇒ ★用原始图集 UV 采样 ✓
    //     ⇒ ★基础/拉弓/任意变体都能取到**自己那张** ✓✓
    private static final ResourceLocation TEX = net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;

    private static ShaderInstance shader;
    private static RenderType renderType;
    private static StarBodyClient INSTANCE;
    private static long TNL$LOG = 0L;
    private static long TNL$LOG2 = 0L;

    private StarBodyClient() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        TinkersNewlife.LOGGER.warn("TNL944 BEWLR constructed");     // ★TNL944 探针 ✓
    }

    /** TNL947 ★供混入用：★取渲染器（★没建就建）✓ */
    public static BlockEntityWithoutLevelRenderer rendererOrNull() {
        try {
            return instance();
        } catch (Throwable t) {
            return null;
        }
    }

    /** TNL947 ★供混入用：★着色器好了没 ✓ */
    public static boolean shaderReady() {
        return shader != null;
    }

    public static StarBodyClient instance() {
        if (INSTANCE == null) {
            INSTANCE = new StarBodyClient();
        }
        return INSTANCE;
    }

    /** ★注册着色器 ✓ */
    @Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        private Shaders() {
        }

        @SubscribeEvent
        public static void onRegisterShaders(RegisterShadersEvent event) throws IOException {
            TinkersNewlife.LOGGER.warn("TNL944 shader register called");       // ★TNL944 ✓
            try {
                event.registerShader(
                        new ShaderInstance(event.getResourceProvider(), SHADER_ID, DefaultVertexFormat.NEW_ENTITY),
                        s -> {
                            shader = s;
                        });
            } catch (Throwable t) {
            }
        }
    }

    /** ★每帧喂 ``EffectTime`` ✓ */
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
            // TNL967 ★把当前相机朝向也喂进去 ✓
            try {
                var cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera();
                if (cam != null && cam.isInitialized()) {
                    setCameraAngles(cam.getYRot(), cam.getXRot());
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** TNL967 ★每帧把相机朝向喂给着色器（★透镜感的根本 ✓） */
    public static void setCameraAngles(float yaw, float pitch) {
        try {
            if (shader == null) {
                return;
            }
            var a = shader.safeGetUniform("CamYaw");
            if (a != null) {
                a.set(yaw);
            }
            var b = shader.safeGetUniform("CamPitch");
            if (b != null) {
                b.set(pitch);
            }
        } catch (Throwable ignored) {
        }
    }

    /** TNL971 ★把 sprite 在图集里的 UV 矩形喂给着色器 ✓ */
    public static void setSpriteRect(float u0, float v0, float u1, float v1) {
        try {
            if (shader == null) {
                return;
            }
            var a = shader.safeGetUniform("SpriteRect");
            if (a != null) {
                a.set(u0, v0, u1, v1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** TNL960 ★把贴图在图集里的 UV 范围告诉着色器 ✓ */
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

    /** TNL950 ★供混入用：★没好就返回 null（★退回原版）✓ */
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

    /** ★给物品用的扩展（★就像参考实现那样挂 BEWLR ✓） */
    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;      // ★延迟创建（★同参考 ✓）

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                long tnl$n = System.currentTimeMillis();
                if (tnl$n - TNL$LOG2 > 5000L) {                       // TNL946 ★打调用栈 ✓
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
        // TNL945 ★★ 无条件（★限流 1 秒）探针 ✓
        long tnl$now = System.currentTimeMillis();
        if (tnl$now - TNL$LOG > 1000L) {
            TNL$LOG = tnl$now;
            TinkersNewlife.LOGGER.warn("TNL945 renderByItem ENTER ctx={} shader={}",
                    context, shader != null);
        }
        Minecraft mc = Minecraft.getInstance();
        RenderType star = starBody();
        if (star == null) {
            // ★兜底：★着色器没好 ⇒ ★用原版类型画 ✓（★不会变空 ✗）
            renderModel(stack, pose, buffers, light, overlay,
                    ItemBlockRenderTypes.getRenderType(stack, true));
            return;
        }
        MultiBufferSource.BufferSource source = buffers instanceof MultiBufferSource.BufferSource bs ? bs : null;
        // TNL948 ★★ 用户反馈：★"弓不见了" ✓
        //   ★保底 ✓：★**先用原版类型画一遍** ✓
        //     ⇒ ★哪怕星空那一层出问题，★弓也不会消失 ✓
        try {
            renderModel(stack, pose, buffers, light, overlay,
                    ItemBlockRenderTypes.getRenderType(stack, true));
        } catch (Throwable t) {
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
            TinkersNewlife.LOGGER.warn("TNL945 renderByItem FAILED: {}", t.toString());   // ★不再吞异常 ✓
        } finally {
            try {
                if (source != null) {
                    source.endBatch(star);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** ★把弓的 baked model 四边形写到指定类型里 ✓ */
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
                vc.normal(n, 0.0F, 1.0F, 0.0F);      // TNL948 ★不再乱解法线 ✓
                vc.endVertex();
            }
        }
    }
}
