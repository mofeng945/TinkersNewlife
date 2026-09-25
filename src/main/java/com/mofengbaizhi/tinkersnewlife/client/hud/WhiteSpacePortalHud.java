package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlockEntity;
import com.mofengbaizhi.tinkersnewlife.content.portal.WhiteSpaceDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 「伟大白色空间之门」的准星提示（§661 用户口径：「当鼠标指针指向传送门时，要让门能够显示对面落点」）。
 *
 * <h2>为什么还要自己画一份（玉不是已经能显示了吗）</h2>
 * 玉（Jade）确实能显示，而且仓库里已经加了联动
 * （{@code integration/jade/WhiteSpacePortalJadePlugin}）✓。但玉是<b>第三方模组</b>：
 * 玩家可能没装、可能把它关了、也可能在配置里只留了自己想看的那几项。
 * 这里做一个<b>零依赖的兜底</b>：装玉时自动让位（见 {@link #onRenderGui} 第一句），
 * 没装玉时才在准星下面画一小块 —— 两种情况下都能看到对面落点 ✓。
 *
 * <p>提示内容是<b>客户端自己的方块实体</b>里读的（目的地靠
 * {@link WhiteSpacePortalBlockEntity#getUpdatePacket()} 同步过来）⇒ 不占额外带宽、不卡顿 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WhiteSpacePortalHud {

    private WhiteSpacePortalHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        // 装了玉 ⇒ 交给玉显示，别在这儿重复画一份（会重叠成两块）
        if (ModList.get() != null && ModList.get().isLoaded("jade")) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof WhiteSpacePortalBlockEntity portal)) return;
        ResourceKey<Level> dimension = portal.getDestinationDimension();
        if (dimension == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        BlockPos dest = portal.getDestinationPos();

        Component title = Component.translatable("hud.tinkersnewlife.white_space_portal.title");
        Component line = Component.translatable("hud.tinkersnewlife.white_space_portal.line",
                WhiteSpaceDimensions.displayName(dimension), dest.getX(), dest.getY(), dest.getZ());

        int cx = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 24;
        int half = Math.max(font.width(title), font.width(line)) / 2 + 5;
        graphics.fill(cx - half, y - 4, cx + half, y + 22, 0xC0101018);
        graphics.drawCenteredString(font, title, cx, y, 0xBFE8FF);
        graphics.drawCenteredString(font, line, cx, y + 11, 0xFFFFFF);
    }
}
