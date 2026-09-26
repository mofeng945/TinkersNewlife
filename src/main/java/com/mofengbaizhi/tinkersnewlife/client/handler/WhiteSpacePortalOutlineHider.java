package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * <b>把「伟大白色空间之门」的选中轮廓藏起来</b>（§668，用户口径：
 * 「反正我要让选中框基本看不出来，但是用天逆鉾右键门可以敲碎」）。
 *
 * <h2>为什么不能用 {@code getShape} 去做这件事</h2>
 * 十字准星的"选中轮廓"和"能不能右键到这个方块"用的是<b>同一个</b>形状 ——
 * {@code ClipContext.Block.OUTLINE} ⇒ {@code BlockState#getShape(...)}
 * （{@code Entity#pick} → {@code BlockGetter#clip}）。
 * 所以把 {@code getShape} 改小/改薄，轮廓确实淡了，但<b>右键也会一起变难点中</b> ✗
 * —— 而用户要求的是"看不出来"＋"天逆鉾右键能敲碎"，两件事都要 ✓。
 *
 * <h2>走 Forge 事件：轮廓与拾取彻底解耦</h2>
 * 反编译源码里这条链是确证的：
 * {@code LevelRenderer#renderLevel} 第 1328 行
 * <pre>if (!net.minecraftforge.client.ForgeHooksClient.onDrawHighlight(this, camera, hitResult, ...))
 *    ... this.renderHitOutline(...)</pre>
 * 而 {@code ForgeHooksClient#onDrawHighlight} 对 BLOCK 就是
 * {@code MinecraftForge.EVENT_BUS.post(new RenderHighlightEvent.Block(...))}
 * —— {@code MinecraftForge.EVENT_BUS.post(...)} 返回"是否被取消"，
 * 于是 <b>取消这个事件 ⇒ 描边整段不画</b> ✓（事件类自己的 javadoc 也写着
 * "If the event is cancelled, then the selection highlight will not be rendered."）。
 *
 * <p>⇒ 本处理器只在客户端取消"目标是我们的门"的那次描边：
 * <b>轮廓完全不画</b>（比"基本看不出来"还干净），而 {@code getShape} 依旧是整格
 * ⇒ 准星照常锁得住、天逆鉾右键照常敲得碎 ✓。
 *
 * <p>⚠ 代价（如实说明）：轮廓没了以后，玩家只能靠别的方式知道"我正对着门"——
 * 装玉时靠玉的两行提示、没装玉时靠 {@link com.mofengbaizhi.tinkersnewlife.client.hud.WhiteSpacePortalHud}
 * 的兜底提示 ✓（两条都还在）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WhiteSpacePortalOutlineHider {

    private WhiteSpacePortalOutlineHider() {
    }

    /** 每帧对着方块时都会走一次（没对着方块就不触发）—— 只做一次方块状态查询，可忽略不计 ✓ */
    @SubscribeEvent
    public static void onRenderHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos pos = event.getTarget().getBlockPos();
        if (mc.level.getBlockState(pos).is(ModBlocks.WHITE_SPACE_PORTAL.get())) {
            event.setCanceled(true);
        }
    }
}
