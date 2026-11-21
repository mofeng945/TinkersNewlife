package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.model.MomoGeoModel;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Vertex-tinted emissive second pass, plus an upright seal in body-yaw space. */
final class MomoPhaseGlow extends GeoRenderLayer<KtuluMomo> {
    MomoPhaseGlow(GeoRenderer<KtuluMomo> renderer) { super(renderer); }
    private static float grow(float t){ t=Mth.clamp(t,0,1); return t*t*(3-2*t); }
    @Override public void render(PoseStack poses,KtuluMomo boss,BakedGeoModel model,RenderType base,MultiBufferSource buffers,VertexConsumer original,float partial,int light,int overlay){
        if(boss.visualPhase()!=3 || !boss.isAlive() || MomoGeoModel.GHOST_PASS)return;
        float age=boss.phaseAge(partial), growth=grow(age/20f);
        if(growth<=0)return;
        int rgb=Mth.hsvToRgb(((boss.tickCount%140)+partial)/140f,.78f,1f);
        var type=RenderType.entityTranslucentEmissive(MomoGeoModel.MERCHANT_SKIN);
        poses.pushPose();
        try {
            // A shell that blooms out of the silhouette, without changing the boss or its hitbox.
            float s=1.001f+.048f*growth; poses.scale(s,s,s);
            getRenderer().reRender(model,poses,buffers,boss,type,buffers.getBuffer(type),partial,LightTexture.FULL_BRIGHT,overlay,
                ((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,.53f*growth);
        } finally {poses.popPose();}
    }
    static void drawSigil(KtuluMomo boss,float partial,PoseStack poses,MultiBufferSource buffers){
        float age=boss.phaseAge(partial);
        boolean finalPhase=boss.visualPhase()==3;
        // A short expanding quartz-coloured flash announces the second-phase skin swap.
        if(!finalPhase && age>=24)return;
        float inner=grow((age-(finalPhase?14:0))/20f),outer=grow((age-23)/26f);
        if(inner<=0)return;
        poses.pushPose();
        try {
            poses.translate(0,boss.getBbHeight()*.64,0);
            poses.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,boss.yBodyRotO,boss.yBodyRot)));
            poses.translate(0,0,-.54);
            Matrix4f m=poses.last().pose();var v=buffers.getBuffer(RenderType.debugQuads());
            double time=(boss.tickCount%7200)+partial;
            if(!finalPhase){ring(v,m,inner*.98,0,.035,0,64,0xe6deee,1-grow(age/24));return;}
            double spin=time*Math.PI/120,back=-time*Math.PI/180;
            for(int i=0;i<6;i++)line(v,m,.78*inner,spin+i*Math.PI/3,spin+(i+2)*Math.PI/3,.055,.028*inner,0xffefc2,inner);
            ring(v,m,.92*inner,spin,.018*inner,.04,64,0xfcbd68,inner);
            if(outer<=0)return;
            for(int i=0;i<6;i++)line(v,m,1.32*outer,back+i*Math.PI/3,back+(i+2)*Math.PI/3,-.09,.018*outer,0xd6a7ff,outer*.85f);
            ring(v,m,1.43*outer,back,.016*outer,-.13,80,0xb698ed,outer*.9f);
            for(int i=0;i<12;i++){double a=back+i*Math.PI/6;line(v,m,1.52*outer,a-.08,a+.08,-.18,.036*outer,i%2==0?0xffdc91:0x91d9ff,outer);}
        } finally {poses.popPose();}
    }
    private static void ring(VertexConsumer v,Matrix4f m,double r,double angle,double w,double z,int n,int rgb,float alpha){for(int i=0;i<n;i++)line(v,m,r,angle+i*2*Math.PI/n,angle+(i+1)*2*Math.PI/n,z,w,rgb,alpha);}
    private static void line(VertexConsumer v,Matrix4f m,double r,double a,double b,double z,double w,int rgb,float alpha){
        double x=r*Math.cos(a),y=r*Math.sin(a),u=r*Math.cos(b),t=r*Math.sin(b),len=Math.hypot(u-x,t-y);if(len<1e-5)return;
        double nx=(y-t)*w/len,ny=(u-x)*w/len;
        point(v,m,x+nx,y+ny,z,rgb,alpha);point(v,m,u+nx,t+ny,z,rgb,alpha);point(v,m,u-nx,t-ny,z,rgb,alpha);point(v,m,x-nx,y-ny,z,rgb,alpha);
    }
    private static void point(VertexConsumer v,Matrix4f m,double x,double y,double z,int rgb,float a){v.vertex(m,(float)x,(float)y,(float)z).color(rgb>>16,(rgb>>8)&255,rgb&255,(int)(255*a)).endVertex();}
}
