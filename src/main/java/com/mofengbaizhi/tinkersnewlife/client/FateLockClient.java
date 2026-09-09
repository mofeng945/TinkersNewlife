package com.mofengbaizhi.tinkersnewlife.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.FatePierceModifier;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import net.minecraft.world.item.ItemStack;

/**
 * 神灵金"命运因果贯穿之物"锁定的客户端锁定图标：蹲下持该远程武器时，
 * 在锁定的敌人上方绘制一个锁定框（仿原版锁定标识）。服务端逻辑见 FatePierceHandler。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class FateLockClient {

    private static final double RANGE = 32.0;
    private static int lockEntityId = -1;

    private FateLockClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { lockEntityId = -1; return; }
        Player p = mc.player;
        if (!p.isShiftKeyDown() || !hasFatePierce(p.getMainHandItem())) { lockEntityId = -1; return; }
        Entity target = findLock(mc, p);
        lockEntityId = target != null ? target.getId() : -1;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (lockEntityId < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(lockEntityId);
        if (!(e instanceof LivingEntity target) || target.isRemoved()) return;

        Vec3 pos = target.getBoundingBox().getCenter();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        net.minecraft.client.renderer.culling.Frustum fr = mc.levelRenderer.getFrustum();
        ps.translate(-mc.gameRenderer.getMainCamera().getPosition().x, -mc.gameRenderer.getMainCamera().getPosition().y, -mc.gameRenderer.getMainCamera().getPosition().z);
        ps.translate(pos.x, pos.y + target.getBbHeight() * 0.7, pos.z);
        float s = Math.max(target.getBbWidth() * 1.4f, 0.6f);
        ps.scale(s, s, s);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        // 四角括号：右上→右下→左下→左上
        float h = 0.5f;
        line(vc, -0.5f, -h, 0, 0.0f, -h, 0);   // left-top
        line(vc, 0.5f, -h, 0, 0.0f, -h, 0);
        line(vc, -0.5f, -h, 0, -0.5f, 0.4f, 0);
        line(vc, 0.5f, -h, 0, 0.5f, 0.4f, 0);
        buf.endBatch();
        ps.popPose();
    }

    private static void line(VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2) {
        vc.vertex(x1, y1, z1).color(1f, 0.35f, 0.55f, 0.9f).normal(0, 0, 1).endVertex();
        vc.vertex(x2, y2, z2).color(1f, 0.35f, 0.55f, 0.9f).normal(0, 0, 1).endVertex();
    }

    private static Entity findLock(Minecraft mc, Player p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        LivingEntity best = null; double bestScore = 0.94, bestDist = Double.MAX_VALUE;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE),
                x -> x != p && x.isAlive())) {
            Vec3 to = e.getEyePosition().subtract(eye); double dist = to.length();
            if (dist > RANGE || dist < 0.5) continue;
            double dot = to.normalize().dot(look);
            if (dot >= bestScore && clearSight(mc, eye, e)) { bestScore = dot; best = e; }
            else if (best == null && dist < bestDist) { best = e; bestDist = dist; }
        }
        return best;
    }

    private static boolean clearSight(Minecraft mc, Vec3 from, LivingEntity target) {
        BlockHitResult hit = mc.level.clip(new net.minecraft.world.level.ClipContext(
                from, target.getEyePosition(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation().distanceToSqr(from) >= from.distanceToSqr(target.getEyePosition()) - 1.0;
        }
        return true;
    }

    private static boolean hasFatePierce(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(FatePierceModifier.ID) > 0;
    }
}
