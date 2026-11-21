package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.ModEntities;
import com.mofengbaizhi.tinkersnewlife.content.astral.AstralProjection;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Draw the actual client's skin, without changing player mode, invisibility or any server state. */
@Mod.EventBusSubscriber(modid="tinkersnewlife", value=Dist.CLIENT)
public final class AstralSpiritRenderer extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final float OPACITY = .43F;
    private static AstralSpiritRenderer slim, wide;
    private AstralSpiritRenderer(EntityRendererProvider.Context context, boolean narrow) {
        super(context, new SpiritModel(context.bakeLayer(narrow ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), narrow), 0);
    }
    private static final class SpiritModel extends PlayerModel<AbstractClientPlayer> {
        SpiritModel(ModelPart root, boolean narrow) { super(root, narrow); }
        @Override public void renderToBuffer(PoseStack pose, VertexConsumer vertices, int light, int overlay, float r, float g, float b, float a) {
            super.renderToBuffer(pose, vertices, light, overlay, r*.84F, g*.97F, b, a*OPACITY);
        }
    }
    @Mod.EventBusSubscriber(modid="tinkersnewlife", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntities.ASTRAL_BODY.get(), AstralBodyRenderer::new);
        }
        @SubscribeEvent public static void models(EntityRenderersEvent.AddLayers event) {
            wide = new AstralSpiritRenderer(event.getContext(), false);
            slim = new AstralSpiritRenderer(event.getContext(), true);
        }
    }
    @Override public ResourceLocation getTextureLocation(AbstractClientPlayer p) { return p.getSkinTextureLocation(); }
    @Override @Nullable protected RenderType getRenderType(AbstractClientPlayer p, boolean visible, boolean translucent, boolean glowing) {
        return visible || translucent ? RenderType.entityTranslucent(getTextureLocation(p)) : super.getRenderType(p, visible, translucent, glowing);
    }
    private static AstralSpiritRenderer pick(AbstractClientPlayer p) { return "slim".equals(p.getModelName()) ? slim : wide; }
    private static boolean spirit(AbstractClientPlayer p) { return !(p instanceof AstralBodyRenderer.Mannequin) && AstralProjection.active(p); }
    private void layers(AbstractClientPlayer p) {
        model.setAllVisible(true);
        model.hat.visible = p.isModelPartShown(PlayerModelPart.HAT);
        model.jacket.visible = p.isModelPartShown(PlayerModelPart.JACKET);
        model.leftSleeve.visible = p.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        model.rightSleeve.visible = p.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        model.leftPants.visible = p.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
        model.rightPants.visible = p.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
        model.crouching = p.isCrouching();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void thirdPerson(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || !spirit(player)) return;
        var renderer = pick(player);
        if (renderer == null) return;
        event.setCanceled(true);
        renderer.layers(player);
        renderer.render(player, player.getYRot(), event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
    }
    @SubscribeEvent
    public static void blockOverlay(net.minecraftforge.client.event.RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() == net.minecraftforge.client.event.RenderBlockScreenEffectEvent.OverlayType.BLOCK
                && !event.getBlockState().is(net.minecraft.world.level.block.Blocks.BEDROCK)
                && AstralProjection.active(event.getPlayer())) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void firstPerson(RenderArmEvent event) {
        var p = event.getPlayer();
        if (!spirit(p)) return;
        var renderer = pick(p);
        if (renderer == null) return;
        event.setCanceled(true);
        if (p.isInvisible()) return;
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        var model = renderer.model;
        var part = right ? model.rightArm : model.leftArm;
        var sleeve = right ? model.rightSleeve : model.leftSleeve;
        part.resetPose(); sleeve.resetPose(); part.visible = true;
        sleeve.visible = p.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
        var buffer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(p.getSkinTextureLocation()));
        part.render(event.getPoseStack(), buffer, event.getPackedLight(), OverlayTexture.NO_OVERLAY, .84F, .97F, 1, OPACITY);
        sleeve.render(event.getPoseStack(), buffer, event.getPackedLight(), OverlayTexture.NO_OVERLAY, .84F, .97F, 1, OPACITY);
    }
}
