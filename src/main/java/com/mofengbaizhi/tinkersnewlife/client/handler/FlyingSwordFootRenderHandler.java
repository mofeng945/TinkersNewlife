package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.FlyingSwordCuriosHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 脚部飞剑的<b>客户端渲染</b>：飞起来时把飞剑画在玩家脚下（跟玩家模型同一趟渲染 ✓）。
 *
 * <h2>为什么不再用"脚下实体"</h2>
 * 旧做法是一个真实体 {@code FlyingSwordFootEntity}：服务端**每 tick** {@code setPos} 跟着玩家跑、
 * 再把位置同步给所有客户端 ✗ —— 纯视觉的东西却持续占用 tick 与带宽（用户："持续瞬移很影响游戏tick" ✗）。
 * 现在：服务端**完全不生成**那个实体（登录时还会清理旧存档里的残留 ✓），客户端直接画 ✓
 * —— 位置永远跟模型严格一致（无插值延迟 ✓），服务端零开销 ✓。
 *
 * <h2>两条渲染路径（第一人称 / 其他）</h2>
 * <ul>
 *   <li><b>第三人称与他人视角</b>：{@link RenderPlayerEvent.Post} —— 这时 PoseStack 正停在
 *       "已经平移到该玩家、坐标轴与世界对齐"那一层 ✓，直接画 ✓（和玩家模型同一趟渲染 ✓）；</li>
 *   <li><b>第一人称</b>：⭐ 原版**不渲染自己**（该事件根本不触发）✗，所以另走
 *       {@link RenderLevelStageEvent}（{@code AFTER_ENTITIES}）补一趟：把相机位置减掉、平移到
 *       玩家脚下再画 ✓（只画**自己**、且只在第一人称，避免与上面那趟重复 ✓）。</li>
 * </ul>
 *
 * <h2>观感保持完全一致</h2>
 * 方向数学是从旧的 {@code FlyingSwordFootRenderer} 原样搬过来的（世界空间：模型默认前向
 * {@code (0.707,0.707,0)} → 立起 → 平躺 → 水平转到玩家朝向，缩放 1.2 不变 ✓），
 * 只是把"实体位置 {@code owner.y - 0.1}"换成"玩家原点下移 0.1" ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlyingSwordFootRenderHandler {

    private FlyingSwordFootRenderHandler() {}

    /** 模型默认前向：Y偏X 45°，即 (0.707, 0.707, 0)（与旧渲染器一致 ✓） */
    private static final Vector3f DEFAULT_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    /** 飞剑离脚底多高（旧实体就生成在 owner.y - 0.1 ✓） */
    private static final double FOOT_OFFSET_Y = -0.1;

    /** 第三人称 / 他人视角：跟玩家模型同一趟渲染（不画自己第一人称 —— 那时本事件不触发 ✓） */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Post event) {
        Player player = event.getEntity();
        if (!airborne(player)) return;
        ItemStack stack = FlyingSwordCuriosHandler.feetSwordOf(player);
        if (stack.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, FOOT_OFFSET_Y, 0.0);       // 玩家原点 = 脚底 ✓
        applyOrientation(poseStack, player);
        drawSword(poseStack, event.getMultiBufferSource(), event.getPackedLight(), stack, player);
        poseStack.popPose();
    }

    /**
     * 第一人称：原版不渲染自己 → 在世界渲染阶段补一趟（只画自己，且只在第一人称 ✓）。
     * <p>这一帧与实体渲染帧同类（平移按世界轴 ✓、朝向组合一致 ✓），所以同一套数学照用 ✓：
     * 先把相机位置减掉（得到"玩家在镜头里的位置"），再下移 0.1 到脚下 ✓。
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson()) return;   // 第三人称交给上面那趟 ✓
        Player player = mc.player;
        if (player == null || !airborne(player)) return;
        ItemStack stack = FlyingSwordCuriosHandler.feetSwordOf(player);
        if (stack.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(player.getX() - cam.x,
                player.getY() + FOOT_OFFSET_Y - cam.y,
                player.getZ() - cam.z);
        applyOrientation(poseStack, player);
        int light = LevelRenderer.getLightColor(player.level(), player.blockPosition());
        drawSword(poseStack, mc.renderBuffers().bufferSource(), light, stack, player);
        poseStack.popPose();
    }

    /** 只在"飞起来"时画（自己的 abilities 精确 ✓；别人的 abilities 不同步 → 用是否离地判断 ✓） */
    private static boolean airborne(Player player) {
        return player.getAbilities().flying || !player.onGround();
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

    private static void drawSword(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                  ItemStack stack, Player player) {
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.NONE, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, player.level(), player.getId());
    }
}
