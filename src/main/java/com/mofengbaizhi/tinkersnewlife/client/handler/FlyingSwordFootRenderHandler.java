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
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
 * 脚部飞剑的<b>客户端渲染</b>：飞起来时把飞剑画在玩家脚下、跟着玩家走。
 *
 * <h2>为什么不再用"脚下实体"</h2>
 * 旧做法是一个真实体 {@code FlyingSwordFootEntity}：服务端**每 tick** {@code setPos} 跟着玩家跑、
 * 再把位置同步给所有客户端 ✗ —— 纯视觉的东西却持续占用 tick 与带宽（"持续瞬移很影响游戏tick" ✗）。
 * 现在服务端不生成它（登录时还会清理旧存档残留 ✓），客户端自己画 ✓。
 *
 * <h2>⭐ 为什么画在"世界渲染阶段"这一层（YSM 兼容的关键）</h2>
 * <ul>
 *   <li>本模组的无为转变伪装渲染之所以能在<b>史蒂夫模型（YSM）开启时照常显示</b>，
 *       根本原因不是它做了什么特别的事，而是它挂在 {@code EntityRenderDispatcher#render} 这一层 ——
 *       <b>比"谁来画玩家模型"更高</b>。挂在更下层的 {@code RenderPlayerEvent.Post}（在
 *       {@code PlayerRenderer} 里）会被 YSM 整条绕开 ✗（YSM 自己 mixin 了调度器来接管玩家渲染，
 *       原版 {@code PlayerRenderer} 这条链根本走不到 ✗）。</li>
 *   <li>飞剑与伪装<b>需求不同</b>：伪装是"<b>替换</b>玩家外观"，必须去调度器那里 {@code cancel}；
 *       飞剑是"<b>额外叠加</b>一个物品"，所以挂在比调度器<b>再高一层</b>最干净 ——
 *       {@link RenderLevelStageEvent}（{@code AFTER_ENTITIES}）里直接遍历玩家自己画 ✓。
 *       这一层连"谁在画玩家模型"都不经过 ⇒ <b>装什么模型模组都拦不住</b> ✓，
 *       也天然保证"每帧每个玩家只画一次"（不会像调度器层那样因双注入或被 cancel 而重复或漏画 ✓）。</li>
 *   <li>实体在 {@code AFTER_ENTITIES} 之前已经画完 ⇒ 深度缓冲里已有玩家模型 ✓，
 *       剑与腿脚的前后遮挡关系仍然正确 ✓；这一阶段写入的顶点仍会随本趟 {@code BufferSource} 一起刷出 ✓。</li>
 * </ul>
 *
 * <h2>⭐ 必须用"插值"位置与朝向（"一顿一顿"的根因）</h2>
 * 相机与玩家模型都是按 {@code partialTick} 插值渲染的；如果直接用 {@code getX()/getY()/getZ()}
 * （整 tick 的位置），剑最多会落后一 tick 的距离 ⇒ 快速飞行时看起来一跳一跳 ✗（用户实测）。
 * 所以这里位置用 {@code Mth.lerp(partialTick, xOld, x)}、朝向用
 * {@link Player#getViewVector(float)}（插值视向量）—— 与模型完全同口径 ✓。
 * 别人的位置同理（{@code xOld} 由位置包维护 ✓），所以多人下也不会抖 ✓。
 *
 * <p>朝向数学是从旧的 {@code FlyingSwordFootRenderer} 原样搬的（世界空间：模型默认前向
 * {@code (0.707,0.707,0)} → 立起 → 平躺 → 水平转到玩家朝向，缩放 1.2 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FlyingSwordFootRenderHandler {

    private FlyingSwordFootRenderHandler() {}

    /** 模型默认前向：Y偏X 45°，即 (0.707, 0.707, 0)（与旧渲染器一致 ✓） */
    private static final Vector3f DEFAULT_FORWARD = new Vector3f(0.707f, 0.707f, 0f);

    /** 飞剑离脚底多高（旧实体就生成在 owner.y - 0.1 ✓） */
    private static final double FOOT_OFFSET_Y = -0.1;

    /**
     * 统一渲染路径：{@code AFTER_ENTITIES} 阶段遍历可见玩家，各自在脚下画一把飞剑 ✓。
     * <p>自己（第一人称/第三人称）与他人都在 {@code level.players()} 里 ⇒ 一个循环全覆盖 ✓；
     * 第一人称也照画（用户要求"第一人称可见"，所以<b>不</b>跳过自己 ✓）。
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        float partialTick = event.getPartialTick();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource buffer = mc.renderBuffers().bufferSource();

        for (Player player : level.players()) {
            if (!airborne(player)) continue;
            ItemStack stack = FlyingSwordCuriosHandler.feetSwordOf(player);
            if (stack.isEmpty()) continue;

            // ⭐ 插值位置：相机是插值的，这里不插值就会"一顿一顿" ✗（与模型同口径 ✓）
            double x = Mth.lerp(partialTick, player.xOld, player.getX());
            double y = Mth.lerp(partialTick, player.yOld, player.getY());
            double z = Mth.lerp(partialTick, player.zOld, player.getZ());

            poseStack.pushPose();
            poseStack.translate(x - cam.x, y + FOOT_OFFSET_Y - cam.y, z - cam.z);
            applyOrientation(poseStack, player, partialTick);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            drawSword(poseStack, buffer, light, stack, player);
            poseStack.popPose();
        }
    }

    /** 只在"飞起来"时画（自己的 abilities 精确 ✓；别人的 abilities 不同步 → 用是否离地判断 ✓） */
    private static boolean airborne(Player player) {
        return player.getAbilities().flying || !player.onGround();
    }

    /**
     * 旧 {@code FlyingSwordFootRenderer} 的朝向数学：立起 → 平躺 → 水平转到玩家朝向 + 缩放 1.2 ✓。
     * <p>朝向取**插值**视向量 —— 否则转身时剑会跟着"跳" ✗。
     */
    private static void applyOrientation(PoseStack poseStack, Player player, float partialTick) {
        Vec3 lookAngle = player.getViewVector(partialTick);
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
