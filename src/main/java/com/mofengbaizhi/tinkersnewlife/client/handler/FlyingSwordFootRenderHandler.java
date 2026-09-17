package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.FlyingSwordCuriosHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 脚部飞剑的<b>客户端渲染</b>：飞起来时把飞剑画在玩家脚下。
 *
 * <h2>为什么不再用"脚下实体"</h2>
 * 旧做法是一个真实体 {@code FlyingSwordFootEntity}：服务端**每 tick** {@code setPos} 跟着玩家跑、
 * 再把位置同步给所有客户端 ✗ —— 纯视觉的东西却持续占用 tick 与带宽（用户："持续瞬移很影响游戏tick" ✗）。
 * 现在：服务端**完全不生成**那个实体（登录时还会清理旧存档里的残留 ✓），客户端直接画 ✓
 * —— 位置永远跟人严格一致（无插值延迟 ✓），服务端零开销 ✓。
 *
 * <h2>⭐ 为什么走"世界渲染阶段"而不是玩家渲染事件（YSM 问题）</h2>
 * 本模组环境里装了 <b>YSM（是，史蒂夫模型）</b>：它的 {@code LivingRendererMixin} 直接**替换
 * {@code LivingEntityRenderer#render}**，玩家模型根本不走原版那条链 ✗。
 * 如果再挂在 {@code RenderPlayerEvent}（Forge 是在 {@code PlayerRenderer} 的补丁里发的 ✓，
 * 目前**能**触发 ✓）上，就永远要看"接管渲染的模组有没有顺带把这条链断掉" ✗ —— 太脆。
 * 改成在 {@link RenderLevelStageEvent}（{@code AFTER_ENTITIES}）里按**玩家位置**画：
 * 与"谁画的玩家模型"完全无关 ✓，第一人称（原版不渲染自己 ✗）也天然覆盖 ✓✓，
 * 因此**原版 / YSM / 任何接管玩家渲染的模组下都必然可见** ✓。
 *
 * <h2>观感保持完全一致</h2>
 * 方向数学是从旧的 {@code FlyingSwordFootRenderer} 原样搬的（世界空间：模型默认前向
 * {@code (0.707,0.707,0)} → 立起 → 平躺 → 水平转到玩家朝向，缩放 1.2 ✓），
 * 只把"实体位置 {@code owner.y - 0.1}"换成"玩家原点下移 0.1" ✓；
 * {@code RenderLevelStageEvent} 这一帧与实体渲染帧同类（平移按世界轴 ✓、朝向组合一致 ✓），
 * 所以就是同一套数学 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlyingSwordFootRenderHandler {

    private FlyingSwordFootRenderHandler() {}

    /** 模型默认前向：Y偏X 45°，即 (0.707, 0.707, 0)（与旧渲染器一致 ✓） */
    private static final Vector3f DEFAULT_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    /** 飞剑离脚底多高（旧实体就生成在 owner.y - 0.1 ✓） */
    private static final double FOOT_OFFSET_Y = -0.1;

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        // 只在"飞起来"时画；自己的 abilities 精确 ✓，别人的不同步 → 用是否离地判断 ✓
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = null;
        for (Player player : level.players()) {
            if (!player.getAbilities().flying && player.onGround()) continue;
            ItemStack stack = FlyingSwordCuriosHandler.feetSwordOf(player);
            if (stack.isEmpty()) continue;
            if (buffer == null) buffer = mc.renderBuffers().bufferSource();

            poseStack.pushPose();
            poseStack.translate(player.getX() - cam.x,
                    player.getY() + FOOT_OFFSET_Y - cam.y,
                    player.getZ() - cam.z);
            applyOrientation(poseStack, player);
            int light = LevelRenderer.getLightColor(level, player.blockPosition());
            mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light,
                    OverlayTexture.NO_OVERLAY, poseStack, buffer, level, player.getId());
            poseStack.popPose();
        }
    }

    /** 旧 {@code FlyingSwordFootRenderer} 的朝向数学：立起 → 平躺 → 水平转到玩家朝向 + 缩放 1.2 ✓ */
    private static void applyOrientation(PoseStack poseStack, Player player) {
        Vec3 lookAngle = player.getLookAngle();
        Vector3f horizontalDir = new Vector3f((float) lookAngle.x, 0, (float) lookAngle.z);
        if (horizontalDir.lengthSquared() < 0.0001f) {
            horizontalDir.set(0, 0, -1);   // 备选朝北
        }
        horizontalDir.normalize();

        Quaternionf fixQuat = new Quaternionf().rotateTo(DEFAULT_FORWARD, new Vector3f(0, 1, 0));
        Quaternionf layQuat = new Quaternionf().rotateTo(new Vector3f(0, 1, 0), new Vector3f(0, 0, 1));
        Quaternionf horizontalQuat = new Quaternionf().rotateTo(new Vector3f(0, 0, 1), horizontalDir);
        poseStack.mulPose(new Quaternionf(horizontalQuat).mul(layQuat).mul(fixQuat));
        poseStack.scale(1.2f, 1.2f, 1.2f);
    }
}
