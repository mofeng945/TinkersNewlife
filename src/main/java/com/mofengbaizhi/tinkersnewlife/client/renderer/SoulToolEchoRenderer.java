package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.content.entity.SoulToolEcho;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SoulToolEchoRenderer extends EntityRenderer<SoulToolEcho> {
    public SoulToolEchoRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(SoulToolEcho entity){return InventoryMenu.BLOCK_ATLAS;}
    @Override public void render(SoulToolEcho entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        if(entity.appearance().isEmpty())return;
        pose.pushPose();try{
            var v=entity.getDeltaMovement(); if(v.lengthSqr()>1e-6)pose.mulPose(new Quaternionf().rotateTo(new Vector3f(.7071f,.7071f,0),v.toVector3f().normalize()));
            var ghost=RenderType.itemEntityTranslucentCull(InventoryMenu.BLOCK_ATLAS);
            VertexConsumer tinted=new VertexConsumerWrapper(buffers.getBuffer(ghost)) {
                @Override public VertexConsumer color(int r,int g,int b,int a){super.color(r*3/4,g,b,a*3/5);return this;}
            };
            Minecraft.getInstance().getItemRenderer().renderStatic(entity.appearance(),ItemDisplayContext.NONE,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,pose,
                    type->type.format()==ghost.format()?tinted:buffers.getBuffer(type),entity.level(),entity.getId());
        }finally{pose.popPose();}
    }
}
