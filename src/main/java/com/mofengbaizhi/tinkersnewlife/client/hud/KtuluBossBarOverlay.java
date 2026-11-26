package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TNL901 ★★ 克图露·墨默的**自绘 Boss 血条** ✓
 *
 * <p>★用户口径：★"血条材质放桌面了，你再最左侧加一个墨默的正脸拼成一个 boss 条，
 *  然后在里面用黑白灰色带代表血条：血条灰度随着血量下降逐渐变化由白色变为灰色，
 *  同时给血条色带加一些流动效果" ✓
 *
 * <ul>
 *   <li>★用 {@code CustomizeGuiOverlayEvent.BossEventProgress}：★取消原版条 + 自己画 ✓；</li>
 *   <li>★框体：★桌面那张 ``血条.png``（256×24）✓
 *       ——★拷成新文件 ``textures/gui/ktulu_bar.png`` ✓（★未覆盖任何旧资源 ✓）；</li>
 *   <li>★正脸：★直接从 ``textures/entity/momo_common.png``（★64×64 皮肤 ✓）
 *       里**裁脸部 8×8** ✓（★坐标 8,8 ✓）⇒ ★不需要额外画脸 ✓；</li>
 *   <li>★血带：★黑白灰 ✓；★灰度 = 0.35 + 0.65 × 血量比 ✓
 *       （★满血纯白 → ★残血深灰 ✓）；</li>
 *   <li>★流动：★一道横向高光不断扫过 + ★星点闪烁 ✓。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KtuluBossBarOverlay {

    private static final ResourceLocation BAR =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/gui/ktulu_bar.png");
    private static final ResourceLocation SKIN =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/entity/momo_common.png");

    private static String tnl$name = null;

    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) {
            KtuluName.clientTick();      // TNL916 ★真名概率/乱码滚动 ✓
        }
    }

    @SubscribeEvent
    public static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        try {
            BossEvent be = event.getBossEvent();
            if (be == null) {
                return;
            }
            if (tnl$name == null) {
                tnl$name = Component.translatable("entity.tinkersnewlife.ktulu_momo.bossbar").getString();
            }
            if (!tnl$name.equals(be.getName().getString())) {
                return;
            }
            event.setCanceled(true);                 // ★屏蔽原版条 ✓
            // TNL916 ★★ 用户口径：★"把血条整体下移一点然后在上方渲染 boss 的名字" ✓
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            int screenW = mc.getWindow().getGuiScaledWidth();
            int barY = event.getY() + 8;              // ★整体下移 8px ✓
            draw(event.getGuiGraphics(), event.getX(), barY, be.getProgress());
            // ★名字（★画在血条上方 ✓）
            net.minecraft.network.chat.Component name = KtuluName.build();
            int nameW = mc.font.width(name);
            event.getGuiGraphics().drawString(mc.font, name, screenW / 2 - nameW / 2,
                    event.getY() - 6, 0xFFFFFF);
            event.setIncrement(28);                   // ★告诉原版"我占多高" ✓
        } catch (Throwable ignored) {
        }
    }

    private static void draw(GuiGraphics g, int x, int y, float progress) {
        float p = Math.max(0.0F, Math.min(1.0F, progress));

        // TNL904 ★★ 层序按用户口径：
        //   ★用户原话：★"血条铺在底，然后上面罩住我的血条槽，
        //                然后在我的槽位左侧画一张带着墨默头发的正脸" ✓
        //   ⇒ ★层序：① 血条铺底 ✓ ② 你的贴图罩上去 ✓
        //             ③ 槽左侧画正脸（★脸 + 头发层 ✓）
        //   ★前提：你贴图的槽区是**透明**的 ✓
        //     （★我量过：槽内那几行确实是空白 ✓）⇒ 所以罩上去能透出底下的血条 ✓✓
        int tx = x - 30;
        int ty = y - 6;

        int fx = tx + 34;      // ★槽区（★实测 ✓）
        int fy = ty + 5;
        int fw = 174;
        int fh = 14;
        int fillW = (int) (fw * p);

        // ①-A TNL905 ★★ 槽底先铺一层**不透明深底** ✓
        //   ★用户反馈：★"我给你截图我这么细致的血条槽怎么变成条形码了" ✓
        //   ★真因 ✗：★你贴图的槽区是**完全透明**的 ✓
        //     （★实测：x34~207 / y5~18 / 透明率 100% ✓）
        //     ⇒ ★**没有血的那一半**会透出背后的背包格子线 ✗
        //     ⇒ ★看起来就像"条形码" ✓（★其实是 GUI 背景 ✗）
        g.fill(fx, fy, fx + fw, fy + fh, 0xF00A0A12);

        // ① ★血条铺底 ✓（★灰度随血量：满血白 → 残血深灰 ✓）
        if (fillW > 0) {
            int gray = (int) (255.0F * (0.35F + 0.65F * p));
            if (gray > 255) {
                gray = 255;
            }
            // TNL907 ★★ 免疫窗挡下时 ⇒ ★血条向**金色**高亮闪动 ✓
            float flash = KtuluBarFlash.strength();
            int rr = gray;
            int gg = gray;
            int bb = gray;
            if (flash > 0.0F) {
                rr = Math.min(255, (int) (gray + (255 - gray) * flash));
                gg = Math.min(255, (int) (gray + (205 - gray) * flash));
                bb = Math.min(255, (int) (gray + (70 - gray) * flash));
            }
            int col = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
            g.fill(fx, fy, fx + fillW, fy + fh, col);
            if (flash > 0.0F) {
                int glow = (int) (110 * flash);
                g.fill(fx, fy, fx + fillW, fy + 2, (glow << 24) | 0xFFD24A);
                g.fill(fx, fy + fh - 2, fx + fillW, fy + fh, (glow / 2 << 24) | 0xFFD24A);
            }
            g.fill(fx, fy, fx + fillW, fy + 2, 0x30FFFFFF);      // ★上高光 ✓
            g.fill(fx, fy + fh - 2, fx + fillW, fy + fh, 0x30000000);  // ★下暗边 ✓
        }

        // ② ★罩上你的贴图（★框 + 槽透明 ✓）
        // TNL906 ★★ 关键修正：★不能用 7 参 blit ✗
        //   ★用户反馈：★"压根没有边框的红色十字纹路和触手" ✓
        //   ★真因 ✗：``blit(atlas, x, y, u, v, uWidth, vHeight)`` 假定**整张贴图是 256×256** ✗
        //     ⇒ ★只从你的图里取最上面 24/256 （★即 2 行）再拉伸 ✗
        //     ⇒ ★那两行点阵被竖着拉长 = **条形码** ✓
        //     ⇒ ★十字（y5~18）与触手根本没被取到 ✗
        //   ★现在：★用 11 参重载**显式声明贴图尺寸 256×24** ✓
        g.blit(BAR, tx, ty, 256, 24, 0.0F, 0.0F, 256, 24, 256, 24);

        // ③ ★槽左侧：带头发的墨默正脸 ✓
        //   ★脸 = 皮肤头部正面 8×8 @(8,8) ✓
        //   ★头发 = 头部贴图层 8×8 @(40,8) ✓（★标准皮肤的 hat 层就是头发/装饰 ✓）
        int faceX = fx - 24;
        int faceY = ty + 2;
        g.blit(SKIN, faceX, faceY, 20, 20, 8.0F, 8.0F, 8, 8, 64, 64);
        g.blit(SKIN, faceX, faceY, 20, 20, 40.0F, 8.0F, 8, 8, 64, 64);

        // ★流动：高光扫过 + 星点闪烁 ✓（★画在最上层，仅限槽内 ✓）
        if (fillW > 0) {
            long t = System.currentTimeMillis();
            int span = 56;
            int off = (int) ((t / 10L) % (fw + span)) - span;
            int gx0 = Math.max(fx, fx + off);
            int gx1 = Math.min(fx + fillW, fx + off + span);
            if (gx1 > gx0) {
                g.fill(gx0, fy, gx1, fy + fh, 0x34FFFFFF);
            }
            for (int i = 0; i < 30; i++) {
                long seed = (i + 1) * 2654435761L;
                int sx = fx + (int) Math.floorMod((seed >>> 9) % Math.max(1, fillW), Math.max(1, fillW));
                int sy = fy + (int) ((seed >>> 17) % fh);
                double tw = (Math.sin(t / 460.0 + i * 1.3) + 1.0) / 2.0;
                int a = (int) (26 + 120 * tw);
                g.fill(sx, sy, sx + 1, sy + 1, (a << 24) | 0xFFFFFF);
            }
        }
    }
}
