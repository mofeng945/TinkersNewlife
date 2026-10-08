package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.LongShortBladeItem;
import com.mojang.math.Axis;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * ⭐ §1124 长短刃「<b>杀戮光环</b>」的**玩家模型旋转**（用户口径 ✓
 * 「使玩家模型双手平举刀刃持平然后开始**快速旋转**」✓ —— ⚠ 本轮只做"旋转" ✗，「双手平举」见类末 ✓）。
 *
 * <h2>⭐ 信号从哪来（⚠ 关键在于"客户端看不到服务端状态" ✗）</h2>
 * 光环的真实状态在服务端的静态集合里 ✓ ⇒ ⚠ **客户端看不到** ✗（hud-side 审查时专门提醒过 ✓）。
 * ⇒ ⭐ 改用**物品 NBT**（{@link LongShortBladeItem#TAG_ULTIMATE_END} ✓）当信号 ✓ ——
 * ⭐ 手持物品的 NBT **会同步给客户端** ✓ ⇒ 客户端读它就知道"该转起来了" ✓ ⇒ ⭐ **不需要网络包** ✓。
 * ⚠ 判断只依赖"**是否 &gt; 0**" ✗ 不依赖精确剩余值 ✓（物品 NBT 的同步不保证每 tick ✓ 会滞后 ✓）。
 *
 * <h2>⚠ 姿态栈的 push/pop 必须配对（本仓 flying sword 那套吃过多层姿态的亏 ✗）</h2>
 * ⚠ 若在 {@code Pre} 里 {@code pushPose()} 却在 {@code Post} 里**无条件** pop ✗
 * ⇒ 那些**没有信号**的实体会把**原版自己那一层**弹掉 ✗ ⇒ 整帧渲染错位 ✗。
 * ⇒ ⭐ 所以用一个**按实体记录**的标记 ✓（{@code WeakHashMap} ＋ 渲染是单线程 ✓）——
 * 只有真的 push 过才 pop ✓ 且**同一个实体**才算 ✓。
 *
 * <h2>⚠ 为什么以"身体中心"为轴 ✗</h2>
 * {@code RenderPlayerEvent.Pre} 的姿态栈原点在**实体脚底** ✓ ⇒ 直接转会让模型**绕脚底甩一圈** ✗
 * （看起来像飘出去 ✓）⇒ ⭐ 先抬到身体中心（0.9 格 ≈ 1.8 身高的一半 ✓）、旋转、再抬回去 ✓。
 *
 * <h2>⚠ 本轮**没做**的（如实记录 ✗ 不擅自动 ✓）</h2>
 * ⭐ 用户口径里的「**双手平举、刀刃持平**」需要动 {@code HumanoidModel} 的
 * {@code rightArm}/{@code leftArm} 骨骼 ✓ —— ⚠ 那**风险更高**（可能干扰举盾/拉弓等既有姿势 ✗）
 * ⇒ ⭐ 先只做旋转 ✓ 等用户点头再补手臂 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LongShortBladeSpinHandler {

    private LongShortBladeSpinHandler() {
    }

    /** ⭐ 每 tick 转 36° ⇒ 10 tick（0.5 秒）一圈 ✓ —— 用户口径「**快速**旋转」✓ */
    private static final float DEG_PER_TICK = 36.0F;

    /** ⭐ 旋转轴心高度（格 ✓）：约等于玩家身高 1.8 的一半 ⇒ 以身体中心为轴 ✓ */
    private static final double PIVOT_Y = 0.9D;

    /** ⭐ 已经 push 过、还等着 pop 的玩家 ✓（⚠ 只有真 push 过的才 pop ✗ 免得多弹一层 ✓） */
    private static final Map<Player, Boolean> PUSHED = new WeakHashMap<>();

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        try {
            // ⚠ RenderPlayerEvent#getEntity() 的返回类型**就是 Player** ✗
            //   ⇒ 不能写 `instanceof Player player`（"表达式类型的子类型"⇒ 非法 ✗）
            //   ⚠ 这已经是**第二次**踩同一个坑 ✓（第一次是 projectile-side 替我编译时揪出来的 ✓）
            Player player = event.getEntity();
            if (player == null || !spinning(player)) {
                return;
            }
            float angle = (player.tickCount * DEG_PER_TICK) % 360.0F;
            var pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(0.0D, PIVOT_Y, 0.0D);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(0.0D, -PIVOT_Y, 0.0D);
            PUSHED.put(player, Boolean.TRUE);
        } catch (Throwable ignored) {
            // ⭐ 渲染出错绝不能崩客户端 ✗
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        try {
            Player player = event.getEntity();
            if (player != null && Boolean.TRUE.equals(PUSHED.remove(player))) {
                event.getPoseStack().popPose();
            }
        } catch (Throwable ignored) {
            // ⭐ 同理 ✗
        }
    }

    /**
     * ⭐ 这个玩家此刻该转吗 ✓ —— 看**手持物品的 NBT 信号** ✓
     * （⚠ 主手优先 ✗ 主手没有再看副手 ✓ —— 按原版 F 换手后信号可能落在副手那把上 ✓）。
     */
    private static boolean spinning(Player player) {
        ItemStack main = player.getMainHandItem();
        if (LongShortBladeItem.isUltimateVisual(main)) {
            return true;
        }
        ItemStack off = player.getOffhandItem();
        return LongShortBladeItem.isUltimateVisual(off);
    }
}
