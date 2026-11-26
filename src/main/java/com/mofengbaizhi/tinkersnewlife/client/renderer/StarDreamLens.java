package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

/** A camera-facing annulus remaps a snapshot of the scene. Never sample the active render target. */
final class StarDreamLens {
    private static int texture, width, height;
    static void draw(Matrix4f pose, float radius) {
        if (radius < .04f) return;
        var target = Minecraft.getInstance().getMainRenderTarget();
        Matrix4f clip = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(pose);
        if (clip.m33() < .1f) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int oldBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int oldSampler = RenderSystem.getShaderTexture(0);
        var oldShader = RenderSystem.getShader();
        boolean oldDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try {
            if (texture == 0) texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (width != target.width || height != target.height) {
                width = target.width; height = target.height;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.depthMask(false);
            var b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            for (int i=0; i<64; i++) for (int j=0; j<6; j++) {
                double a = i*Math.PI/32, end = (i+1)*Math.PI/32;
                vertex(b,pose,clip,radius,a,j); vertex(b,pose,clip,radius,a,j+1);
                vertex(b,pose,clip,radius,end,j+1); vertex(b,pose,clip,radius,end,j);
            }
            BufferUploader.drawWithShader(b.end());
        } finally {
            RenderSystem.depthMask(oldDepthMask);
            RenderSystem.setShaderTexture(0,oldSampler);
            RenderSystem.setShader(() -> oldShader);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,oldBinding);
        }
    }
    private static void vertex(BufferBuilder b, Matrix4f pose, Matrix4f clip, float radius, double angle, int band) {
        float t = band/6f, r = radius*(.77f+.51f*t);
        float dx=(float)Math.cos(angle), dy=(float)Math.sin(angle);
        float sampleR = r - radius*.38f*(1-t)*(1-t);
        Vector4f v = clip.transform(new Vector4f(dx*sampleR,dy*sampleR,0,1));
        b.vertex(pose,dx*r,dy*r,0).uv(.5f+.5f*v.x/v.w,.5f+.5f*v.y/v.w).endVertex();
    }
}
