package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.entity.YoYoEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 悠悠球实体渲染器：
 * <p>
 * 立体模型——用 ItemRenderer 渲染三个部件物品（轮1/轮2/线轴），通过 3D 摆位
 * 组合成立体悠悠球：两个轮盘分列两侧（正对玩家），线轴居中侧放。每个部件物品
 * 走匠魂 tconstruct:material 材质模型加载器，自动显示对应部件的材质贴图与颜色，
 * 与匠魂盔甲"每部件独立材质渲染"机制一致。飞行/飞回绕 Z 轴快速旋转模拟滚动，
 * 停滞时缓慢自转；同时绘制玩家手到球体的连线（弓弦材质颜色）。
 */
public class YoYoRenderer extends EntityRenderer<YoYoEntity> {

    public YoYoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(YoYoEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 绘制玩家手到球体的连线（弓弦材质颜色）
        renderLine(entity, partialTicks, poseStack, buffer);

        // 读取工具栈，获取三个部件的材质
        ItemStack toolStack = entity.getReturnStack();
        if (toolStack.isEmpty()) return;
        ToolStack tool = ToolStack.from(toolStack);
        if (tool == null) return;
        MaterialNBT materials = tool.getMaterials();
        if (materials == null || materials.size() < 3) return;

        // 构造三个部件物品栈（带材质 NBT，ItemRenderer 按材质渲染）
        ItemStack wheel1 = buildPartStack(ModItems.YO_YO_WHEEL.get(), materials.get(0));
        ItemStack wheel2 = buildPartStack(ModItems.YO_YO_WHEEL.get(), materials.get(1));
        ItemStack spool = buildPartStack(ModItems.YO_YO_SPOOL.get(), materials.get(2));
        if (wheel1.isEmpty() || wheel2.isEmpty() || spool.isEmpty()) return;

        var itemRenderer = Minecraft.getInstance().getItemRenderer();

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
        poseStack.scale(0.9f, 0.9f, 0.9f);

        // 左轮（正对玩家，Z 负侧）
        poseStack.pushPose();
        poseStack.translate(0, 0, -0.22f);
        itemRenderer.renderStatic(wheel1, ItemDisplayContext.NONE, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), 0);
        poseStack.popPose();

        // 线轴（居中，绕 X 转 90° 侧面朝玩家，形成"轴"的立体感）
        poseStack.pushPose();
        poseStack.translate(0, 0, 0);
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.PI / 2));
        itemRenderer.renderStatic(spool, ItemDisplayContext.NONE, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), 0);
        poseStack.popPose();

        // 右轮（正对玩家，Z 正侧）
        poseStack.pushPose();
        poseStack.translate(0, 0, 0.22f);
        itemRenderer.renderStatic(wheel2, ItemDisplayContext.NONE, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffer, entity.level(), 0);
        poseStack.popPose();

        poseStack.popPose();
    }

    /**
     * 构造带指定材质的部件物品栈（走匠魂 material 模型加载器渲染）。
     */
    private static ItemStack buildPartStack(net.minecraft.world.item.Item item, MaterialVariant variant) {
        ItemStack stack = new ItemStack(item);
        if (variant == null || variant.isUnknown()) return stack;
        MaterialVariantId variantId = variant.getVariant();
        if (variantId == null) return stack;
        if (variantId.hasVariant()) {
            // 带变体的材质（如 oxidized）：通过 MaterialIdNBT 设置
            if (item instanceof slimeknights.tconstruct.library.tools.part.ToolPartItem partItem) {
                partItem.setMaterial(stack, variantId.getId());
            }
            return stack;
        }
        if (item instanceof slimeknights.tconstruct.library.tools.part.ToolPartItem partItem) {
            partItem.setMaterial(stack, variantId.getId());
        }
        return stack;
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
        var mat = poseStack.last().pose();

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

    @Override
    public ResourceLocation getTextureLocation(YoYoEntity entity) {
        return null;
    }
}
