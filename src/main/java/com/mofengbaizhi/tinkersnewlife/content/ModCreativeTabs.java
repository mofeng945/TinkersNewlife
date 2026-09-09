package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.CurseCoreItem;
import com.mofengbaizhi.tinkersnewlife.content.item.DragonStaffItem;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mofengbaizhi.tinkersnewlife.content.item.SilentGloveItem;
import com.mofengbaizhi.tinkersnewlife.content.item.WarScytheItem;
import com.mofengbaizhi.tinkersnewlife.content.item.YoYoItem;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Collection;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TinkersNewlife.MOD_ID);

    private static final ToolDefinition DRAGON_STAFF_DEFINITION = DragonStaffItem.DRAGON_STAFF_DEFINITION;
    private static final ToolDefinition SILENT_GLOVE_DEFINITION = SilentGloveItem.SILENT_GLOVE_DEFINITION;

    public static final RegistryObject<CreativeModeTab> TINKERS_NEW_LIFE_TAB =
            CREATIVE_MODE_TABS.register("tinkersnewlife",
                    () -> CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.tinkersnewlife"))
                            .icon(() -> new ItemStack(ModItems.GHELOTH_REMAINS.get()))
                            .displayItems((parameters, output) -> {
                                // ⭐ 已安装帕秋莉时，创造物品栏首位显示本模组手册
                                // （纯注册表操作 + NBT 指定书 id，不引用任何帕秋莉类，未安装则跳过）
                                if (ModList.get().isLoaded("patchouli")) {
                                    Item guideBook = ForgeRegistries.ITEMS.getValue(
                                            new ResourceLocation("patchouli", "guide_book"));
                                    if (guideBook != null) {
                                        ItemStack guide = new ItemStack(guideBook);
                                        guide.getOrCreateTag().putString("patchouli:book", TinkersNewlife.MOD_ID + ":guide");
                                        output.accept(guide);
                                    }
                                }

                                // ----- 基础材料 -----
                                output.accept(ModItems.GHELOTH_REMAINS.get());
                                output.accept(ModItems.NICHOLAS_BLESSING.get());
                                output.accept(ModItems.YELLOW_KING_REMNANT.get());
                                // ⭐ 神灵金锭为诡厄启示录联动材料，未装 goety 则不在创造栏显示
                                if (anyLoaded("goety")) {
                                    output.accept(ModItems.DIVINE_GOLD.get());
                                }
                                output.accept(ModItems.RLYEH_CALL.get());
                                output.accept(ModItems.ECHO_OF_THE_VOID.get());
                                output.accept(ModItems.ASTRAL_ANCHOR.get());
                                output.accept(ModItems.NEXUS_OF_SPACETIME.get());
                                output.accept(ModItems.YOG_SOTHOTH_GATE_KEY.get());
                                output.accept(ModItems.NYARLATHOTEP_DESIRE.get());
                                output.accept(ModItems.DURANDAL_SHARD.get());

                                // ----- 矿石 -----
                                output.accept(ModItems.GHELOTH_ORE.get());

                                // ----- 墨默刷怪蛋（位于所有旧日材料之后、匠魂部件之前） -----
                                output.accept(ModItems.MOMO_SPAWN_EGG.get());

                                // ----- 所有流体桶（联动来源流体仅对应 mod 加载时显示） -----
                                output.accept(ModFluids.GHELOTH_BLOOD.bucket.get());
                                output.accept(ModFluids.MOLTEN_NICHOLAS_BLESSING.bucket.get());
                                output.accept(ModFluids.HASTUR_MALICE.bucket.get());
                                output.accept(ModFluids.ASHEN_INK.bucket.get());
                                output.accept(ModFluids.MOLTEN_DURANDAL.bucket.get());
                                if (anyLoaded("iceandfire")) {
                                    output.accept(ModFluids.MOLTEN_DRAGONSTEEL_FIRE.bucket.get());
                                    output.accept(ModFluids.MOLTEN_DRAGONSTEEL_ICE.bucket.get());
                                    output.accept(ModFluids.MOLTEN_DRAGONSTEEL_LIGHTNING.bucket.get());
                                    output.accept(ModFluids.FIRE_BLOOD.bucket.get());
                                    output.accept(ModFluids.ICE_BLOOD.bucket.get());
                                    output.accept(ModFluids.LIGHTNING_BLOOD.bucket.get());
                                    output.accept(ModFluids.MOLTEN_DREAD.bucket.get());
                                    output.accept(ModFluids.MOLTEN_DREADSTEEL.bucket.get());
                                }
                                if (anyLoaded("goety")) {
                                    output.accept(ModFluids.MOLTEN_CURSED_METAL.bucket.get());
                                    output.accept(ModFluids.MOLTEN_DARK_METAL.bucket.get());
                                }

                                // ----- 铸模（联动工具的铸模仅在对应 mod 加载时显示） -----
                                if (anyLoaded("iceandfire")) {
                                    output.accept(ModItems.DRAGON_CORE_CAST.get());
                                    output.accept(ModItems.DRAGON_CORE_SAND_CAST.get());
                                    output.accept(ModItems.DRAGON_CORE_RED_SAND_CAST.get());
                                }
                                if (anyLoaded("irons_spellbooks", "goety")) {
                                    output.accept(ModItems.SPELL_CORE_CAST.get());
                                    output.accept(ModItems.SPELL_CORE_SAND_CAST.get());
                                    output.accept(ModItems.SPELL_CORE_RED_SAND_CAST.get());
                                }
                                output.accept(ModItems.YO_YO_WHEEL_CAST.get());
                                output.accept(ModItems.YO_YO_WHEEL_SAND_CAST.get());
                                output.accept(ModItems.YO_YO_WHEEL_RED_SAND_CAST.get());
                                output.accept(ModItems.YO_YO_SPOOL_CAST.get());
                                output.accept(ModItems.YO_YO_SPOOL_SAND_CAST.get());
                                output.accept(ModItems.YO_YO_SPOOL_RED_SAND_CAST.get());
                                // 部件材质变体（联动工具的部件同样受来源 mod 门控）
                                if (anyLoaded("iceandfire")) {
                                    addAllPartVariants(output, ModItems.DRAGON_CORE.get());
                                }
                                if (anyLoaded("irons_spellbooks", "goety")) {
                                    addAllPartVariants(output, ModItems.SPELL_CORE.get());
                                }
                                addAllPartVariants(output, ModItems.YO_YO_WHEEL.get());
                                addAllPartVariants(output, ModItems.YO_YO_SPOOL.get());
                                // ⭐ 咒力核心部件已从创造物品栏移除（易与成品咒力核心混淆，需用核心请取成品变体）

                                if (anyLoaded("iceandfire")) {
                                    addAllToolVariants(output, DRAGON_STAFF_DEFINITION, ModItems.DRAGON_STAFF.get(), 3);
                                }

                                addAllToolVariants(output, SILENT_GLOVE_DEFINITION, ModItems.SILENT_GLOVE.get(), 2);

                                addAllToolVariants(output, WarScytheItem.WAR_SCYTHE_DEFINITION, ModItems.WAR_SCYTHE.get(), 5);

                                if (anyLoaded("irons_spellbooks", "goety")) {
                                    addAllToolVariants(output, ModularStaffItem.MODULAR_STAFF_DEFINITION, ModItems.MODULAR_STAFF.get(), 4);
                                }

                                addAllToolVariants(output, FlyingSwordItem.FLYING_SWORD_DEFINITION, ModItems.FLYING_SWORD.get(), 5);

                                addAllToolVariants(output, YoYoItem.YO_YO_DEFINITION, ModItems.YO_YO.get(), 4);

                                addAllToolVariants(output, CurseCoreItem.CURSE_CORE_DEFINITION, ModItems.CURSE_CORE.get(), 1);

                                output.accept(ModItems.DURANDAL_SWORD.get());

                                // ----- 咒具 -----
                                output.accept(ModItems.TIAN_NI_HUO.get());
                                output.accept(ModItems.YOU_YUN.get());
                                output.accept(ModItems.GOURD_JAIL.get());
                                output.accept(ModItems.BOUNDARY_FRAGMENT.get());

                                // ----- 咒言术残卷（学习咒言词条）-----
                                output.accept(ModItems.ANCIENT_CURSED_SCROLL.get());
                            })
                            .build()
            );

    // ============================================================
    //  辅助方法：添加所有部件材质变体
    // ============================================================

    private static void addAllPartVariants(CreativeModeTab.Output output, ToolPartItem partItem) {
        Collection<IMaterial> materials = MaterialRegistry.getInstance().getAllMaterials();
        for (IMaterial material : materials) {
            MaterialId materialId = material.getIdentifier();
            if (!materialId.getNamespace().equals(TinkersNewlife.MOD_ID)) continue;
            if (!isMaterialDependencyLoaded(materialId)) continue;   // 联动来源 mod 未装 → 创造栏不显示

            ItemStack stack = new ItemStack(partItem);
            partItem.setMaterial(stack, materialId);

            if (!stack.isEmpty()) {
                output.accept(stack);
            }
        }
    }

    // ============================================================
    //  联动内容过滤：工具/部件/铸模/流体桶等依赖其它 mod 的条目，对应 mod 未加载则不显示
    // ============================================================

    /** 任一列出的 mod 已加载（联动工具需任一施法/龙 mod 在场才显示） */
    private static boolean anyLoaded(String... mods) {
        try {
            net.minecraftforge.fml.ModList list = net.minecraftforge.fml.ModList.get();
            if (list == null) return false;
            for (String m : mods) {
                if (list.isLoaded(m)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================
    //  联动材料过滤：材料原料来自其它 mod（诡厄/冰火）时，该 mod 未加载则不显示在创造栏
    // ============================================================

    /** 材料 id（不含命名空间）→ 原料来源 mod；无映射 = 本 mod 原生材料 */
    private static final java.util.Map<String, String> MATERIAL_SOURCE_MOD = new java.util.HashMap<>();
    static {
        MATERIAL_SOURCE_MOD.put("cursed_metal", "goety");        // 诡厄诅咒金属锭
        MATERIAL_SOURCE_MOD.put("dark_metal", "goety");          // 诡厄黑暗金属锭
        MATERIAL_SOURCE_MOD.put("divine_gold", "goety");         // 神灵金锭（诡厄启示录联动材料）
        MATERIAL_SOURCE_MOD.put("dragonsteel_fire", "iceandfire");
        MATERIAL_SOURCE_MOD.put("dragonsteel_ice", "iceandfire");
        MATERIAL_SOURCE_MOD.put("dragonsteel_lightning", "iceandfire");
        MATERIAL_SOURCE_MOD.put("dreadsteel", "iceandfire");     // 悚怖碎片来自冰火
        MATERIAL_SOURCE_MOD.put("dragonbone", "iceandfire");     // 龙骨
    }

    /** 材料是否可用：原生材料恒可用；联动材料仅其来源 mod 已加载时可用 */
    private static boolean isMaterialDependencyLoaded(MaterialId materialId) {
        String sourceMod = MATERIAL_SOURCE_MOD.get(materialId.getPath());
        if (sourceMod == null) return true;
        try {
            return net.minecraftforge.fml.ModList.get() != null
                    && net.minecraftforge.fml.ModList.get().isLoaded(sourceMod);
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================
    //  辅助方法：添加所有工具材质变体（手动传入部件数量）
    // ============================================================

    private static void addAllToolVariants(CreativeModeTab.Output output, ToolDefinition definition, Item toolItem, int partCount) {
        if (definition == null || toolItem == null) return;

        Collection<IMaterial> materials = MaterialRegistry.getInstance().getAllMaterials();

        for (IMaterial material : materials) {
            MaterialId materialId = material.getIdentifier();
            if (!materialId.getNamespace().equals(TinkersNewlife.MOD_ID)) continue;
            if (!isMaterialDependencyLoaded(materialId)) continue;   // 联动来源 mod 未装 → 创造栏不显示

            ItemStack stack = new ItemStack(toolItem);
            ToolStack tool = ToolStack.from(stack);
            if (tool == null) continue;

            // 根据传入的部件数量构建材质列表（⭐ 消除 2/3/4/5 魔法数字分支）
            MaterialVariant[] variants = new MaterialVariant[partCount];
            for (int i = 0; i < variants.length; i++) {
                variants[i] = MaterialVariant.of(material);
            }
            MaterialNBT materialNBT = MaterialNBT.of(variants);

            tool.setMaterials(materialNBT);
            tool.rebuildStats();

            ItemStack result = tool.createStack();
            if (!result.isEmpty()) {
                // ⭐ 噤默手套：生成时即确定额外戒指槽数量（1~6，服务端固定写入持久数据，之后不再变化）
                if (result.getItem() instanceof SilentGloveItem) {
                    SilentGloveItem.getOrCreateExtraRings(result);
                }
                output.accept(result);
            }
        }
    }
}