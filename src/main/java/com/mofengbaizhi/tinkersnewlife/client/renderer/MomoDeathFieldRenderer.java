package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.StarfallRenderTypes;
import com.mofengbaizhi.tinkersnewlife.content.entity.KtuluMomo;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** A translucent, two-sided ward. Center and radius refer to the server effect, not the camera. */
@Mod.EventBusSubscriber(modid=TinkersNewlife.MOD_ID,value=Dist.CLIENT)
public final class MomoDeathFieldRenderer {
    private static final int N=64, LAT=20;
    private static final double TAU=2*Math.PI;
    private static final double[] X=new double[N+1], Z=new double[N+1], Y=new double[LAT+1], R=new double[LAT+1];
    static {
        for(int i=0;i<=N;i++){X[i]=Math.cos(TAU*i/N); Z[i]=Math.sin(TAU*i/N);}
        for(int j=0;j<=LAT;j++){double a=Math.PI*(j/(double)LAT-.5);Y[j]=Math.sin(a);R[j]=Math.cos(a);}
    }
    private static float ease(float x){float t=Mth.clamp(x,0,1);return t*t*(3-2*t);}
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var cam=e.getCamera().getPosition();var poses=e.getPoseStack();var buffers=mc.renderBuffers().bufferSource();boolean drawn=false;
        for(var entity:mc.level.entitiesForRendering()){
            if(!(entity instanceof KtuluMomo boss)||boss.deathFieldAge()<=0)continue;
            float age=boss.deathFieldAge()+e.getPartialTick();if(age>=KtuluMomo.DEATH_FIELD_TICKS)continue;
            var p=boss.getPosition(e.getPartialTick()).subtract(cam);poses.pushPose();
            try {
                poses.translate(p.x,p.y,p.z);var m=poses.last().pose();var v=buffers.getBuffer(StarfallRenderTypes.ARROW_TRAIL);
                float rad=KtuluMomo.DEATH_FIELD_RADIUS*ease(age/16),fade=.5f+.5f*ease((KtuluMomo.DEATH_FIELD_TICKS-age)/8);
                for(int j=0;j<LAT;j++)for(int i=0;i<N;i++){
                    int alpha=(int)((17+10*Math.cos(j*.85-age*.12)+4*Math.sin(i*.45+j*.7))*fade);
                    vertex(v,m,rad*R[j]*X[i],rad*Y[j],rad*R[j]*Z[i],0x9755cb,alpha);
                    vertex(v,m,rad*R[j+1]*X[i],rad*Y[j+1],rad*R[j+1]*Z[i],0x9a64d6,alpha);
                    vertex(v,m,rad*R[j+1]*X[i+1],rad*Y[j+1],rad*R[j+1]*Z[i+1],0x9a64d6,alpha);
                    vertex(v,m,rad*R[j]*X[i+1],rad*Y[j],rad*R[j]*Z[i+1],0x9755cb,alpha);
                }
                ring(v,m,rad,.08,.075,0xba98ff,(int)(190*fade));
                double g=ease((age-3)/17),spin=age*.009;
                for(int k=0;k<6;k++)stroke(v,m,rad*.81*g,spin+k*TAU/6,spin+(k+2)*TAU/6,.20,.095,0xffd6a0,(int)(220*fade));
                ring(v,m,rad*.86*g,.16,.055,0xf9bc80,(int)(170*fade));
                for(int k=0;k<12;k++)stroke(v,m,rad*.92*g,-spin+k*TAU/12,-spin+k*TAU/12+.25,.28,.075,0x92d9ed,(int)(190*fade));
                drawn=true;
            } finally {poses.popPose();}
        }
        if(drawn)buffers.endBatch(StarfallRenderTypes.ARROW_TRAIL);
    }
    private static void ring(VertexConsumer v,Matrix4f m,double r,double y,double w,int color,int alpha){for(int i=0;i<N;i++)stroke(v,m,r,TAU*i/N,TAU*(i+1)/N,y,w,color,alpha);}
    private static void stroke(VertexConsumer v,Matrix4f m,double r,double a,double b,double y,double w,int color,int alpha){
        double x=r*Math.cos(a),z=r*Math.sin(a),u=r*Math.cos(b),t=r*Math.sin(b),len=Math.hypot(u-x,t-z);if(len<1e-6)return;
        double dx=(z-t)*w/len,dz=(u-x)*w/len;
        vertex(v,m,x+dx,y,z+dz,color,alpha);vertex(v,m,u+dx,y,t+dz,color,alpha);vertex(v,m,u-dx,y,t-dz,color,alpha);vertex(v,m,x-dx,y,z-dz,color,alpha);
    }
    private static void vertex(VertexConsumer v,Matrix4f m,double x,double y,double z,int c,int a){v.vertex(m,(float)x,(float)y,(float)z).color(c>>16,(c>>8)&255,c&255,a).endVertex();}
}
