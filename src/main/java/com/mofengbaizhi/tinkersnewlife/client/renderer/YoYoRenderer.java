package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 悠悠球实体渲染器：
 * <p>
 * 立体模型——自绘 3D 圆柱几何（两个轮盘 + 中间线轴），每个部件使用其
 * 工具部件材质对应的贴图（base 贴图 × 材质颜色，与匠魂部件材质生成原理一致），
 * 从而正确显示各个部分的材质颜色；飞行/飞回时绕 Z 轴快速旋转模拟滚动，
 * 停滞时缓慢自转；同时绘制玩家手到球体的连线（弓弦材质颜色）。
 */
public class YoYoRenderer extends EntityRenderer<YoYoEntity> {

    /** 部件槽位贴图路径（与工具模型 textures 一致） */
    private static final ResourceLocation TEX_WHEEL1 =
            new ResourceLocation(TinkersNewlife.MOD_ID, "item/tool/yo_yo/wheel1");
    private static final ResourceLocation TEX_WHEEL2 =
            new ResourceLocation(TinkersNewlife.MOD_ID, "item/tool/yo_yo/wheel2");
    private static final ResourceLocation TEX_SPOOL =
            new ResourceLocation(TinkersNewlife.MOD_ID, "item/tool/yo_yo/spool");

    /** 渲染类型：方块图集 + 实体裁剪（部件贴图都在方块图集内） */
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS);

    public YoYoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(YoYoEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 绘制玩家手到球体的连线（弓弦材质颜色）
        renderLine(entity, partialTicks, poseStack, buffer);

        // 解析三个部件（轮1/轮2/线轴）的材质颜色
        int[] colors = getPartColors(entity);
        if (colors == null) return;

        // 获取部件 base 贴图（用户绘制的贴图，将被材质颜色染色）
        var atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        TextureAtlasSprite wheel1Sprite = atlas.getSprite(TEX_WHEEL1);
        TextureAtlasSprite wheel2Sprite = atlas.getSprite(TEX_WHEEL2);
        TextureAtlasSprite spoolSprite = atlas.getSprite(TEX_SPOOL);

        poseStack.pushPose();

        // 旋转：停滞缓慢自转，飞行/飞回快速滚动（绕 Z 轴，轮面朝向玩家）
        float rotation;
        if (entity.getPhase() == YoYoEntity.PHASE_STALLED) {
            rotation = (entity.tickCount + partialTicks) * 0.5f;
        } else {
            rotation = (entity.tickCount + partialTicks) * 1.5f;
        }
        poseStack.mulPose(new Quaternionf().rotationZ(rotation));

        // 整体缩放
        poseStack.scale(0.7f, 0.7f, 0.7f);

        VertexConsumer consumer = buffer.getBuffer(RENDER_TYPE);
        Matrix4f mat = poseStack.last().pose();

        // 三个部件：左轮 / 线轴 / 右轮（圆柱轴向为 Z，轮面朝玩家）
        addCylinder(consumer, mat, 0f, 0f, -0.32f, 0.46f, 0.14f, 14, wheel1Sprite, colors[0], packedLight);
        addCylinder(consumer, mat, 0f, 0f, 0.32f, 0.46f, 0.14f, 14, wheel2Sprite, colors[1], packedLight);
        addCylinder(consumer, mat, 0f, 0f, 0f, 0.16f, 0.5f, 12, spoolSprite, colors[2], packedLight);

        poseStack.popPose();
    }

    /**
     * 从球实体携带的工具栈解析三个部件（轮1/轮2/线轴）的材质颜色。
     *
     * @return ARGB 颜色数组 [wheel1, wheel2, spool]，工具无效时为 null
     */
    private static int[] getPartColors(YoYoEntity entity) {
        ItemStack stack = entity.getReturnStack();
        if (stack.isEmpty()) return null;
        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return null;
        MaterialNBT materials = tool.getMaterials();
        if (materials == null || materials.size() < 3) return null;

        int[] colors = new int[]{0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF};
        int[] partIndices = {0, 1, 2};
        for (int i = 0; i < partIndices.length; i++) {
            MaterialVariant variant = materials.get(partIndices[i]);
            if (variant == null || variant.isUnknown()) continue;
            MaterialVariantId variantId = variant.getVariant();
            if (variantId == null) continue;
            TextColor textColor = MaterialTooltipCache.getColor(variantId);
            if (textColor != null) {
                colors[i] = textColor.getValue();
            }
        }
        return colors;
    }

    /**
     * 绘制玩家手到悠悠球实体的连线（弓弦材质颜色，与工具模型 bowstring 槽位一致）。
     */
    private void renderLine(YoYoEntity entity, float partialTicks,
                            PoseStack poseStack, MultiBufferSource buffer) {
        UUID ownerUuid = entity.getOwnerUUID();
        if (ownerUuid == null) return;

        Player player = entity.level().getPlayerByUUID(ownerUuid);
        if (player == null) return;

        Vec3 entityPos = entity.getPosition(partialTicks);
        Vec3 playerPos = player.getPosition(partialTicks).add(0, player.getEyeHeight() - 0.2, 0);

        float dx = (float) (playerPos.x - entityPos.x);
        float dy = (float) (playerPos.y - entityPos.y);
        float dz = (float) (playerPos.z - entityPos.z);

        // 弓弦颜色：从弓弦部件（索引 3）材质解析
        float r, g, b, a;
        TextColor textColor = getBowstringColor(entity);
        if (textColor != null) {
            int rgb = textColor.getValue();
            r = ((rgb >> 16) & 0xFF) / 255.0f;
            g = ((rgb >> 8) & 0xFF) / 255.0f;
            b = (rgb & 0xFF) / 255.0f;
            a = 1.0f;
        } else {
            r = 1.0f; g = 1.0f; b = 1.0f; a = 1.0f;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.lines());
        Matrix4f mat = poseStack.last().pose();

        consumer.vertex(mat, dx, dy, dz).color(r, g, b, a).normal(0, 1, 0).endVertex();
        consumer.vertex(mat, 0, 0, 0).color(r, g, b, a).normal(0, 1, 0).endVertex();
    }

    /** 获取弓弦部件（索引 3）的材质颜色 */
    private static TextColor getBowstringColor(YoYoEntity entity) {
        ItemStack stack = entity.getReturnStack();
        if (stack.isEmpty()) return null;
        ToolStack tool = ToolStack.from(stack);
        if (tool == null) return null;
        MaterialNBT materials = tool.getMaterials();
        if (materials == null || materials.size() < 4) return null;
        MaterialVariant variant = materials.get(3);
        if (variant == null || variant.isUnknown()) return null;
        MaterialVariantId variantId = variant.getVariant();
        if (variantId == null) return null;
        return MaterialTooltipCache.getColor(variantId);
    }

    /**
     * 绘制一个轴向为 Z 的圆柱（圆盘）。
     *
     * @param consumer  顶点消费者
     * @param mat       模型矩阵
     * @param cx/cy/cz  圆柱中心坐标
     * @param radius    半径
     * @param height    厚度（沿 Z）
     * @param segments  圆周分段数（越大越圆）
     * @param sprite    部件贴图
     * @param argb      材质颜色（ARGB）
     * @param light     光照
     */
    private static void addCylinder(VertexConsumer consumer, Matrix4f mat,
                                    float cx, float cy, float cz,
                                    float radius, float height, int segments,
                                    TextureAtlasSprite sprite, int argb, int light) {
        float r = ((argb >> 16) & 0xFF) / 255.0f;
        float g = ((argb >> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        float half = height / 2.0f;
        float zTop = cz + half;
        float zBottom = cz - half;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments;
            double a1 = (i + 1) * Math.PI * 2 / segments;
            float x0 = (float) (cx + Math.cos(a0) * radius);
            float z0 = (float) (cy + Math.sin(a0) * radius);
            float x1 = (float) (cx + Math.cos(a1) * radius);
            float z1 = (float) (cy + Math.sin(a1) * radius);

            // 顶面（法线 +Z）
            float cu = u0 + (u1 - u0) * 0.5f;
            float cv = v0 + (v1 - v0) * 0.5f;
            addVertex(consumer, mat, cx, cy, zTop, r, g, b, cu, cv, light);
            addVertex(consumer, mat, x0, z0, zTop, r, g, b, u0, v0, light);
            addVertex(consumer, mat, x1, z1, zTop, r, g, b, u1, v0, light);

            // 底面（法线 -Z）
            addVertex(consumer, mat, cx, cy, zBottom, r, g, b, cu, cv, light);
            addVertex(consumer, mat, x1, z1, zBottom, r, g, b, u1, v0, light);
            addVertex(consumer, mat, x0, z0, zBottom, r, g, b, u0, v0, light);

            // 侧面（法线径向，无法线数据，用明暗渐变模拟立体感）
            float nx0 = (float) Math.cos(a0);
            float nx1 = (float) Math.cos(a1);
            float va = v0 + (v1 - v0) * 0.15f;
            float vb = v0 + (v1 - v0) * 0.85f;
            float shade0 = 0.75f + 0.25f * (nx0 + 1) / 2;
            float shade1 = 0.75f + 0.25f * (nx1 + 1) / 2;
            float s0r = r * shade0, s0g = g * shade0, s0b = b * shade0;
            float s1r = r * shade1, s1g = g * shade1, s1b = b * shade1;
            addVertex(consumer, mat, x0, z0, zBottom, s0r, s0g, s0b, u0, vb, light);
            addVertex(consumer, mat, x1, z1, zBottom, s1r, s1g, s1b, u1, vb, light);
            addVertex(consumer, mat, x1, z1, zTop, s1r, s1g, s1b, u1, va, light);
            addVertex(consumer, mat, x0, z0, zTop, s0r, s0g, s0b, u0, va, light);
        }
    }

    /** 写一个顶点（entityCutout 格式：POSITION_COLOR_TEX_LIGHTMAP） */
    private static void addVertex(VertexConsumer consumer, Matrix4f mat,
                                  float x, float y, float z,
                                  float r, float g, float b,
                                  float u, float v, int light) {
        consumer.vertex(mat, x, y, z)
                .color(r, g, b, 1.0f)
                .uv(u, v)
                .uv2(light)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(YoYoEntity entity) {
        return null;
    }
}
