package com.mofengbaizhi.tinkersnewlife.client.slashblade;

// 移植自 TiCEX (MIT): moffy.ticex.client.render.slashblade.SBToolRenderType

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;

/**
 * 刀身三段（刀身/刀鞘/手柄）的贴图定位（逐字移植 TiCEX ✓）。
 *
 * <p>它只做一件事：给定<b>材料</b>，先找"按材料细分"的贴图
 * （{@code textures/obj_tool/slashblade_tool/<部件>_<命名空间>_<材料>.png} ✓），
 * 找不到就退回**基础贴图** {@code <部件>.png} ✓。
 *
 * <p>⚠ <b>本轮只搬了基础贴图</b>（那 250+ 张按材料贴图按用户口径不搬 ✗）⇒ 现在恒落到基础贴图那一支 ✓，
 * 但"按材料细分"的查找逻辑**原样保留** ✓ —— 以后补上贴图即可自动生效 ✓，不用改代码 ✓。
 *
 * <p>⚠ <b>与 TiCEX 的唯一差异（必要适配 ✓）</b>：TiCEX 的刀部件顺序是
 * {@code [刀身, 刀鞘, 坚韧手柄]} ✗，我们的 {@code tool_definitions/katana.json} 是
 * {@code [刀鞘, 刀身, 坚韧手柄]} ✓（用户既有的定义 ✓ 不动 ✗）⇒
 * {@link PartType#byIndex(int)} 的下标映射按**我们的**顺序改 ✓
 * （0=刀鞘 SAYA ✓ 1=刀身 BLADE ✓ 2=手柄 HANDLE ✓），三段贴图的对应关系与 TiCEX 完全一致 ✓。
 */
public class SBToolRenderType {

    public enum PartType {
        BLADE(0, "blade"),
        HANDLE(2, "handle"),
        SAYA(1, "saya");

        private static final ResourceLocation BLADE_TEXTURE_LOC = new ResourceLocation(
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID,
                "textures/obj_tool/slashblade_tool/"
        );
        private static final ResourceLocation DEFAULT_BLADE_TEXTURE_LOC = new ResourceLocation(
                com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID,
                "textures/obj_tool/slashblade_tool/"
        );

        private final int index;
        private final String name;

        PartType(int index, String name) {
            this.index = index;
            this.name = name;
        }

        /**
         * 部件下标 → 三段之一。
         * <p>⚠ 下标顺序按**我们的** katana 工具定义：[0]=刀鞘(sheath) ✓ [1]=刀身(blade) ✓ [2]=坚韧手柄 ✓
         * （TiCEX 是 [0]=刀身 [1]=刀鞘 [2]=手柄 ✗ —— 见类注释 ✓）。
         */
        public static PartType byIndex(int layerIndex) {
            return switch (layerIndex) {
                case 1 -> BLADE;
                case 2 -> HANDLE;
                case 0 -> SAYA;
                default -> null;
            };
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public boolean textureExsists(ResourceLocation location) {
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            Optional<Resource> resource = resourceManager.getResource(location);
            return resource.isPresent();
        }

        public ResourceLocation tryTexture(MaterialVariantId material, Runnable whenIsDefault) {
            String suffix = "_" + material.getId().getNamespace() + "_" + material.getId().getPath();
            if (material.hasVariant()) {
                suffix += "_" + material.getVariant();
            }

            if (textureExsists(BLADE_TEXTURE_LOC.withSuffix(this.name + suffix + ".png"))) {
                return BLADE_TEXTURE_LOC.withSuffix(this.name + suffix + ".png");
            }

            if (whenIsDefault != null) {
                whenIsDefault.run();
            }
            return DEFAULT_BLADE_TEXTURE_LOC.withSuffix(this.name + ".png");
        }
    }
}
