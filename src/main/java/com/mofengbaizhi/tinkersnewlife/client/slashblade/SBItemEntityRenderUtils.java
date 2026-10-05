package com.mofengbaizhi.tinkersnewlife.client.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.client.render.slashblade.SBItemEntityRenderUtils

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import mods.flammpfeil.slashblade.entity.BladeItemEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * "把一件工具按**掉在地上的刀**那样画出来"（逐字移植 TiCEX ✓）—— 用于界面上展示刀的立体样子 ✓。
 *
 * <p>做法：拿一个（缓存的、不进世界的）掉落刀实体当"渲染载体" ✓，交给它的渲染器画 ✓。
 * 缓存 50 条 / 闲置 30 秒过期 ✓（与 TiCEX 完全同参 ✓）。
 *
 * <p>⚠ <b>调用方现状（如实记录 ✓）</b>：TiCEX 里唯一的调用方是 {@code TicEXRenderUtils#renderTool} ✗ ——
 * 那是一个**与拔刀剑无关的通用渲染工具**（它还兼管永恒枪械工坊的枪 ✗）⇒ 不在本轮搬运清单里 ✓
 * ⇒ 本类目前**已搬好但暂无调用者** ✓（留着以备"工具展示"那条线后续搬过来 ✓）。
 */
public class SBItemEntityRenderUtils {

    private static final Cache<ItemStack, BladeItemEntity> BLADE_ENTITY_CACHE = CacheBuilder.newBuilder()
            .maximumSize(50)
            .expireAfterAccess(Duration.ofSeconds(30))
            .build();

    public static void render(EntityRenderDispatcher entityRenderDispatcher, ItemStack itemStack, Level level,
                              PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        try {
            BladeItemEntity bladeItemEntity = BLADE_ENTITY_CACHE.get(itemStack, () -> createBladeEntity(level, itemStack));
            var renderer = entityRenderDispatcher.getRenderer(bladeItemEntity);

            renderer.render(bladeItemEntity, 0.0f, 0.0f, poseStack, bufferSource, packedLight);
        } catch (ExecutionException ignored) {
            // Ignore
        }
    }

    private static BladeItemEntity createBladeEntity(Level level, ItemStack itemStack) {
        com.mofengbaizhi.tinkersnewlife.content.entity.KatanaItemEntity entity =
                com.mofengbaizhi.tinkersnewlife.content.ModEntities.KATANA_ITEM_ENTITY.get().create(level);
        if (entity != null) {
            entity.setItem(itemStack);
        }
        return entity;
    }
}
