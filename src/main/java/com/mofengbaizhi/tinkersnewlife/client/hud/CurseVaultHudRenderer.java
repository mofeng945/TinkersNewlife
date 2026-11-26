package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlock;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseVaultData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * 十字光标对准呪蔵时，在准星下方显示它的咒力量。
 *
 * <p>数据来自方块实体每 20 tick 广播一次的值（见
 * {@link CurseVaultBlockEntity#serverTick}），因此不需要每次查询服务器。
 */
public final class CurseVaultHudRenderer {

    private CurseVaultHudRenderer() {
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.screen != null || mc.options.hideGui) return;
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        BlockPos pos = hit.getBlockPos();
        Level level = mc.level;
        if (!(level.getBlockState(pos).getBlock() instanceof CurseVaultBlock)) return;

        // 真值在服务端 world data：按需询问 + 客户端缓存（见 ClientVaultData）
        com.mofengbaizhi.tinkersnewlife.client.data.ClientVaultData.requestIfStale(pos);
        Double cached = com.mofengbaizhi.tinkersnewlife.client.data.ClientVaultData.get(pos);
        double power = cached == null ? 0 : cached;
        Component text = (cached == null
                ? Component.translatable("hud.tinkersnewlife.curse_vault.unknown")
                : Component.translatable("hud.tinkersnewlife.curse_vault",
                        CursePowerHelper.formatAmount(power),
                        CursePowerHelper.formatAmount(CurseVaultData.CAPACITY)))
                .withStyle(ChatFormatting.LIGHT_PURPLE);

        int w = mc.font.width(text);
        int x = (width - w) / 2;
        int y = height / 2 + 14;   // 准星正下方
        graphics.drawString(mc.font, text, x, y, 0xFFFFFFFF, true);

        // 容量条（细，一眼看出还剩多少空间）
        int barW = Math.max(w, 60);
        int barX = (width - barW) / 2;
        int barY = y + 10;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, 0x80000000);
        int filled = cached == null ? 0
                : (int) Math.round(barW * Math.max(0.0, Math.min(1.0, power / CurseVaultData.CAPACITY)));
        if (filled > 0) {
            graphics.fill(barX, barY, barX + filled, barY + 3, 0xFFA855F7);
        }
    }
}
