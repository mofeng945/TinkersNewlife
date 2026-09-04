package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetIronGolem;
import com.mofengbaizhi.tinkersnewlife.content.entity.PuppetSnowGolem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * 傀儡操术 HUD：视角转移到傀儡上时，屏幕下方渲染傀儡血条（名称 + 血量条 + 数值 + 操作提示）。
 * 客户端直接从相机所绑定的傀儡实体读取血量（实体数据自动同步），无需额外网络包。
 */
public class PuppetHudRenderer {

    private static final int BAR_W = 152;
    private static final int BAR_H = 5;

    private PuppetHudRenderer() {}

    /** Forge GUI Overlay 渲染入口（registerAboveAll） */
    public static void render(Gui gui, GuiGraphics graphics, float partialTick,
                              int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        net.minecraft.world.entity.Entity cam = mc.cameraEntity;
        LivingEntity puppet;
        boolean snow;
        if (cam instanceof PuppetIronGolem iron) {
            puppet = iron;
            snow = false;
        } else if (cam instanceof PuppetSnowGolem snowGolem) {
            puppet = snowGolem;
            snow = true;
        } else {
            return;
        }
        if (!puppet.isAlive()) return;

        Font font = mc.font;
        int barX = (screenWidth - BAR_W) / 2;
        int barY = screenHeight - 62;
        int nameColor = snow ? 0xFFB8E6FF : 0xFFF2F2F2;

        // 名称（血条上方居中）
        Component name = Component.translatable(snow
                ? "entity.tinkersnewlife.puppet_snow_golem"
                : "entity.tinkersnewlife.puppet_iron_golem");
        graphics.drawString(font, name, barX + (BAR_W - font.width(name)) / 2, barY - 11, nameColor);

        // 血量条（按剩余比例变色：绿 → 黄 → 红）
        float pct = puppet.getHealth() / Math.max(1.0F, puppet.getMaxHealth());
        pct = Math.max(0.0F, Math.min(1.0F, pct));
        int hpColor = pct > 0.75F ? 0xFF54E05B : (pct > 0.35F ? 0xFFE0C44E : 0xFFE04E4E);
        graphics.fill(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, 0xCC000000);
        int fillW = pct <= 0.0F ? 0 : Math.max(1, (int) (BAR_W * pct));
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + fillW, barY + BAR_H, hpColor);
        }

        // 数值（血条右侧）
        Component hpText = Component.literal(
                (int) Math.ceil(puppet.getHealth()) + " / " + (int) puppet.getMaxHealth());
        graphics.drawString(font, hpText, barX + BAR_W + 5, barY - 1, 0xFFFFFF);

        // 操作提示（血条下方居中）
        Component hint = Component.translatable("hud.tinkersnewlife.puppet.hint");
        graphics.drawString(font, hint, barX + (BAR_W - font.width(hint)) / 2, barY + BAR_H + 4, 0x9A9A9A);
    }
}
