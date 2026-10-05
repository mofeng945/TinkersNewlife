package com.mofengbaizhi.tinkersnewlife.client.slashblade.context;

// 移植自 TiCEX (MIT): moffy.ticex.lib.context.TicEXContexts

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.Color;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * 拔刀剑渲染用的四个上下文栈（逐字移植 TiCEX 的 {@code TicEXContexts} ✓）。
 * <p>⚠ 这里只留了拔刀剑真正用得到的那四个 ✓（TiCEX 原类里也只有这四个 ✓）。
 */
public class KatanaContexts {

    /** 当前正在渲染的物品（物品栈/显示上下文/矩阵/缓冲/光照 ✓） */
    public static final ContextStack<ItemRenderContext> SB_RENDERING_CONTEXT = new ContextStack<>();
    /** 需要"换掉"的顶点缓冲（材料染色 + 花纹时，把写顶点改写到我们自己的 buffer ✓） */
    public static final ContextStack<VertexConsumer> SB_SWAP_VC = new ContextStack<>();
    /** 需要贴的花纹图集精灵（{@code Face#putVertex} 里把 UV 映射到它上面 ✓） */
    public static final ContextStack<TextureAtlasSprite> SB_FACE_SPRITE = new ContextStack<>();
    /** 颜色覆盖（按材料给刀身染色 ✓） */
    public static final ContextStack<Color> SB_COLOR_OVERRIDE = new ContextStack<>();
}
