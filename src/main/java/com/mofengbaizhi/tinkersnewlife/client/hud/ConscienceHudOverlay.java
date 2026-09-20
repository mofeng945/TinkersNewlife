package com.mofengbaizhi.tinkersnewlife.client.hud;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler;
import com.mofengbaizhi.tinkersnewlife.content.item.ConscienceItem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.io.InputStream;

/**
 * 「心」的 HUD 指示：**物品栏上方那个心的物品图标**本身随善恶值变色 ✓（用户口径 ✓ 不是色条 ✗）。
 *
 * <h2>颜色口径（用户口径 ✓）</h2>
 * <b>善恶 0 ⇒ 灰（底图原样 ✓）／ 满善 +50% ⇒ 白 ✓ ／ 满恶 −50% ⇒ 黑 ✓</b>，中间线性过渡 ✓。
 *
 * <h2>怎么做到"变色"又不碰用户的画 ✗</h2>
 * 只**读**物品纹理 `textures/item/conscience.png` ✓ 在内存里逐像素按亮度系数重染 ✓
 * 上传成一张<b>运行时动态纹理</b>（{@code tinkersnewlife:dynamic/conscience_hud} ✓），HUD 画的是它 ✓。
 * ⇒ 既不改 PNG ✗ 也不改 model ✗（模型那份只有 `layer0` ✓ 用不上 tintindex ✓）；
 * 系数按**底图非透明像素的平均亮度**归一化 ✓ 所以"0 = 原样"✓"满善 = 打到全白"✓ 都能落到位 ✓。
 *
 * <h2>善恶值从哪来（客户端 ✓）</h2>
 * 读**自己槽里那枚「心」的物品 NBT 镜像** `tn_alignment` ✓（权威值在服务端玩家持久数据 ✓
 * 每 20 tick 刷镜像 ✓ Curios 会把饰品槽同步到客户端 ✓）⇒ 客户端不碰服务端数据也能画 ✓
 * ⇒ 延迟最多 1 秒 ✓ 够用 ✓。
 *
 * <h2>位置</h2>
 * 挂在 {@link VanillaGuiOverlay#HOTBAR} 的渲染**之后** ✓；坐标：热键栏 y = h−22 ✓ 经验条 y = h−29 ✓
 * ⇒ 图标放在 <b>y = h−47</b>（16×16 ✓ 正好落在经验条上方、不压任何东西 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ConscienceHudOverlay {

    private ConscienceHudOverlay() {}

    /** 只读的底图（✗ 不写回 ✓） */
    private static final ResourceLocation SOURCE =
            new ResourceLocation(TinkersNewlife.MOD_ID, "textures/item/conscience.png");
    /** 运行时染色出来的动态纹理注册名 */
    private static final ResourceLocation DYNAMIC =
            new ResourceLocation(TinkersNewlife.MOD_ID, "dynamic/conscience_hud");

    /** 满善 / 满恶的目标亮度 ✓（中性那一档 = 底图自身平均亮度 ✓ 载入时算 ✓） */
    private static final float LUMA_MAX_GOOD = 1.0F;
    private static final float LUMA_MAX_EVIL = 0.08F;
    private static final float FALLBACK_LUMA = 0.62F;

    private static final int NO_HEART = Integer.MIN_VALUE;

    private static NativeImage baseImage;
    private static float baseLuma = FALLBACK_LUMA;
    private static boolean baseTried;
    private static DynamicTexture texture;
    private static float builtShade = Float.NaN;

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (!VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) return;

        int alignment = readAlignment(player);
        if (alignment == NO_HEART) return;                 // 没拿到「心」⇒ 不画 ✓（不同步时宁可空着 ✗）

        ensureTexture(shadeFor(alignment));
        if (texture == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int x = mc.getWindow().getGuiScaledWidth() / 2 - 8;
        // ⚠ 原先是 h−47（16 高 ⇒ h−47 ~ h−31）✗ 实机里和经验条顶边（h−29 起）看着还是黏在一起 ✗
        // ⇒ 用户要求抬一抬 ✓ 改成 **h−55**（占 h−55 ~ h−39 ✓ 与经验条留 10px 空隙 ✓）
        //   注意：h−59 附近是原版"切物品时显示的名字"那一行 ✓ 那是**一闪而过**的 ✓ 只可能短暂压到图标上边 ✓
        int y = mc.getWindow().getGuiScaledHeight() - 55;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(DYNAMIC, x, y, 0.0F, 0.0F, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }

    // ============================================================
    //  善恶值（客户端：读物品 NBT 镜像 ✓）
    // ============================================================

    private static int readAlignment(LocalPlayer player) {
        try {
            ItemStack heart = CuriosApi.getCuriosInventory(player).resolve()
                    .map(h -> h.getCurios().get(ConscienceHandler.SLOT_ID))
                    .map(h -> h.getStacks().getSlots() > 0 ? h.getStacks().getStackInSlot(0) : ItemStack.EMPTY)
                    .orElse(ItemStack.EMPTY);
            if (!(heart.getItem() instanceof ConscienceItem)) return NO_HEART;
            return ConscienceHandler.mirrorOf(heart, ConscienceHandler.BAR_ZERO) - ConscienceHandler.BAR_ZERO;
        } catch (Throwable ignored) {
            return NO_HEART;
        }
    }

    /** 善恶 ⇒ 目标平均亮度（0 = 底图原样灰 ✓ 正 ⇒ 向白 ✓ 负 ⇒ 向黑 ✓） */
    private static float shadeFor(int alignment) {
        float t = Math.min(1.0F, Math.abs(alignment) / (float) ConscienceHandler.ALIGNMENT_MAX);
        float target = alignment >= 0 ? LUMA_MAX_GOOD : LUMA_MAX_EVIL;
        return baseLuma + (target - baseLuma) * t;
    }

    // ============================================================
    //  运行时染色纹理
    // ============================================================

    private static void ensureTexture(float shade) {
        try {
            if (!baseTried) loadBase();
            if (baseImage == null) return;
            if (texture == null) {
                texture = new DynamicTexture(baseImage.getWidth(), baseImage.getHeight(), false);
                texture.setFilter(false, false);                     // 像素画 ⇒ 最近邻 ✓
                Minecraft.getInstance().getTextureManager().register(DYNAMIC, texture);
                builtShade = Float.NaN;
            }
            if (!Float.isNaN(builtShade) && Math.abs(builtShade - shade) < 0.004F) return;   // 没变就不重染 ✓
            recolor(shade);
            builtShade = shade;
        } catch (Throwable ignored) {
        }
    }

    private static void loadBase() {
        baseTried = true;
        try {
            // ⚠ 1.20.1 的 ResourceManager#getResource 返回 **Optional<Resource>** ✗（不是 Resource ✓）
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(SOURCE).orElse(null);
            if (resource == null) {
                baseImage = null;
                return;
            }
            try (InputStream in = resource.open()) {
                baseImage = NativeImage.read(in);
                baseLuma = averageLuma(baseImage);
            }
        } catch (Throwable ignored) {
            baseImage = null;
        }
    }

    /** 底图非透明像素的平均亮度（0~1 ✓ 用来把"中性"锚在底图原样上 ✓） */
    private static float averageLuma(NativeImage img) {
        long sum = 0L;
        int n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getPixelRGBA(x, y);
                int a = (p >>> 24) & 0xFF;
                if (a < 8) continue;
                int r = p & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int b = (p >>> 16) & 0xFF;
                sum += (r * 299 + g * 587 + b * 114) / 1000;
                n++;
            }
        }
        return n == 0 ? FALLBACK_LUMA : (float) sum / (float) n / 255.0F;
    }

    /** 逐像素乘系数重染 ✓（底图内部的明暗层次保留 ✓；⚠ 1.20.1 的 NativeImage 取出来是 ABGR 打包 ✓） */
    private static void recolor(float targetLuma) {
        NativeImage dst = texture.getPixels();
        if (dst == null || baseImage == null) return;
        float k = baseLuma <= 0.001F ? 1.0F : targetLuma / baseLuma;
        for (int y = 0; y < dst.getHeight(); y++) {
            for (int x = 0; x < dst.getWidth(); x++) {
                int p = baseImage.getPixelRGBA(x, y);
                int a = (p >>> 24) & 0xFF;
                int b = (p >>> 16) & 0xFF;
                int g = (p >>> 8) & 0xFF;
                int r = p & 0xFF;
                r = Math.min(255, Math.round(r * k));
                g = Math.min(255, Math.round(g * k));
                b = Math.min(255, Math.round(b * k));
                dst.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        texture.upload();
    }
}
