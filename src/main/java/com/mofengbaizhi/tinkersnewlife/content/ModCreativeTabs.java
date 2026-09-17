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
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolMaterialHook;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;

import java.util.Collection;
import java.util.List;

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

                                // ----- 所有流体桶 -----
                                // 原生流体：直接取字段；联动流体桶：按注册名取（联动模组不在场 → 那一组流体/桶根本没注册 → 取不到即跳过）
                                output.accept(ModFluids.GHELOTH_BLOOD.bucket.get());
                                output.accept(ModFluids.MOLTEN_NICHOLAS_BLESSING.bucket.get());
                                output.accept(ModFluids.HASTUR_MALICE.bucket.get());
                                output.accept(ModFluids.ASHEN_INK.bucket.get());
                                output.accept(ModFluids.MOLTEN_DURANDAL.bucket.get());
                                output.accept(ModFluids.CURSE_RESIDUE.bucket.get());
                                // 冰火传说组：熔融龙钢×3 + 龙血×3 + 悚怖×2
                                acceptItemIfPresent(output, "molten_dragonsteel_fire_bucket");
                                acceptItemIfPresent(output, "molten_dragonsteel_ice_bucket");
                                acceptItemIfPresent(output, "molten_dragonsteel_lightning_bucket");
                                acceptItemIfPresent(output, "fire_blood_bucket");
                                acceptItemIfPresent(output, "ice_blood_bucket");
                                acceptItemIfPresent(output, "lightning_blood_bucket");
                                acceptItemIfPresent(output, "molten_dread_bucket");
                                acceptItemIfPresent(output, "molten_dreadsteel_bucket");
                                // 诡厄巫法组：熔融诅咒金属 + 熔融黑暗金属 + 不洁之血 + 永燃圣火
                                acceptItemIfPresent(output, "molten_cursed_metal_bucket");
                                acceptItemIfPresent(output, "molten_dark_metal_bucket");
                                acceptItemIfPresent(output, "unholy_blood_bucket");
                                acceptItemIfPresent(output, "everburning_holy_fire_bucket");
                                // 诡厄巫法·启示录组：熔融破碎之环（神灵金原料流体）
                                acceptItemIfPresent(output, "molten_broken_ring_bucket");
        // 铁魔法（irons_spellbooks）联动流体桶：原初受火遗魂 / 熔融奥铁 / 神圣灵液 /
        // 灼热之冰 / 液态奥术 / 流体灰烬 / 熔融炽金（未安装铁魔法时这些物品不存在，自动跳过）
        acceptItemIfPresent(output, "primordial_fire_soul_bucket");
        acceptItemIfPresent(output, "molten_arcane_ingot_bucket");
        acceptItemIfPresent(output, "holy_spirit_bucket");
        acceptItemIfPresent(output, "scorching_ice_bucket");
        acceptItemIfPresent(output, "liquid_arcane_bucket");
        acceptItemIfPresent(output, "cinder_ash_bucket");
        acceptItemIfPresent(output, "molten_pyrium_bucket");
        acceptItemIfPresent(output, "molten_mithril_bucket");
        acceptItemIfPresent(output, "magic_gold_essence_bucket");
        acceptItemIfPresent(output, "origin_polymer_bucket");
        // 液态闪电（雷电瓶的流体形态，来源与去向都只有雷电瓶）
        acceptItemIfPresent(output, "liquid_lightning_bucket");
        // 液态圣光（熔炼铁魔法神圣珍珠所得，用于浇神圣符文）
        acceptItemIfPresent(output, "liquid_holy_light_bucket");
        // 纯合金流体的"物品形态"（浇铸回环的另一半）：物品本身常驻注册，但铁魔法不在场时拿不到流体，
        // 所以在创造栏里只在铁魔法加载时显示 ✓
        if (anyLoaded("irons_spellbooks")) {
            acceptItemIfPresent(output, "magic_gold_ingot");
            acceptItemIfPresent(output, "holy_spirit_ingot");
            acceptItemIfPresent(output, "origin_alloy_ingot");
            // 粒与储存块（与三个锭配对的常规形态）
            acceptItemIfPresent(output, "magic_gold_nugget");
            acceptItemIfPresent(output, "holy_spirit_nugget");
            acceptItemIfPresent(output, "origin_alloy_nugget");
            acceptItemIfPresent(output, "magic_gold_storage_block");
            acceptItemIfPresent(output, "holy_spirit_storage_block");
            acceptItemIfPresent(output, "origin_alloy_storage_block");
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
                                    addAllToolVariants(output, DRAGON_STAFF_DEFINITION, ModItems.DRAGON_STAFF.get());
                                }

                                addAllToolVariants(output, SILENT_GLOVE_DEFINITION, ModItems.SILENT_GLOVE.get());

                                addAllToolVariants(output, WarScytheItem.WAR_SCYTHE_DEFINITION, ModItems.WAR_SCYTHE.get());

                                if (anyLoaded("irons_spellbooks", "goety")) {
                                    addAllToolVariants(output, ModularStaffItem.MODULAR_STAFF_DEFINITION, ModItems.MODULAR_STAFF.get());
                                }

                                addAllToolVariants(output, FlyingSwordItem.FLYING_SWORD_DEFINITION, ModItems.FLYING_SWORD.get());

                                addAllToolVariants(output, YoYoItem.YO_YO_DEFINITION, ModItems.YO_YO.get());

                                addAllToolVariants(output, CurseCoreItem.CURSE_CORE_DEFINITION, ModItems.CURSE_CORE.get());

                                output.accept(ModItems.DURANDAL_SWORD.get());

                                // ----- 咒具 -----
                                output.accept(ModItems.TIAN_NI_HUO.get());
                                output.accept(ModItems.YOU_YUN.get());
                                output.accept(ModItems.GOURD_JAIL.get());
                                output.accept(ModItems.BOUNDARY_FRAGMENT.get());
                                output.accept(ModItems.LIFE_LAMP_RING.get());   // 命灯指轮（戒指槽饰品）

                                // ----- 咒言术残卷（学习咒言词条）-----
                                output.accept(ModItems.ANCIENT_CURSED_SCROLL.get());

                                // ----- 封呪瓶（咒力容器饰品）+ 其专属流体「咒力残秽」的桶 -----
                                output.accept(ModItems.CURSE_BOTTLE.get());
                                output.accept(ModItems.CURSE_VAULT.get());
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
            // ⭐ 与匠魂本体同款：材料用不了这个部件就跳过（IMaterialItem#setMaterial 遇到不可用材料会
            //    原样返回，结果就是塞进一个"没有材料"的空部件 —— 之前创造栏出问题正是这里）
            if (!partItem.canUseMaterial(materialId)) continue;

            try {
                ItemStack stack = new ItemStack(partItem);
                partItem.setMaterial(stack, materialId);

                if (!stack.isEmpty()) {
                    output.accept(stack);
                }
            } catch (Throwable t) {
                // 单个材料出问题（缺统计等）只跳过它，绝不让整个创造栏构建失败
                TinkersNewlife.LOGGER.warn("[创造栏] 部件 {} 的材料变体 {} 生成失败，已跳过：{}",
                        partItem, materialId, t.toString());
            }
        }
    }

    // ============================================================
    //  联动内容过滤：工具/部件/铸模/流体桶等依赖其它 mod 的条目，对应 mod 未加载则不显示
    // ============================================================

    /** 任一列出的 mod 已加载（联动工具需任一施法/龙 mod 在场才显示） */
    private static boolean anyLoaded(String... mods) {
        for (String m : mods) {
            if (com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.isLoaded(m)) return true;
        }
        return false;
    }

    /**
     * 按注册名把物品加入创造栏；物品不存在则跳过。
     * <p>联动内容的注册点都在 {@code integration/<modid>/} 里（模组不在场 → 整组不注册），
     * 所以公共代码只能按注册名取用，绝不引用联动类的静态字段。
     * <p>⚠ {@link com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader#item(String)} 已处理
     * "Forge 对不存在的 id 返回默认值 AIR"的陷阱（AIR 的 ItemStack 计数是 0，塞进创造栏会抛
     * {@code The stack count must be 1}）；这里再兜一道，AIR 一律不加。
     */
    private static void acceptItemIfPresent(CreativeModeTab.Output output, String itemPath) {
        net.minecraft.world.item.Item item =
                com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.item(itemPath);
        if (item != null && item != net.minecraft.world.item.Items.AIR) output.accept(item);
    }

    // ============================================================
    //  联动材料过滤：材料原料来自其它 mod（诡厄/冰火）时，该 mod 未加载则不显示在创造栏
    // ============================================================

    /** 材料 id（不含命名空间）→ 原料来源 mod；无映射 = 本 mod 原生材料 */
    private static final java.util.Map<String, String> MATERIAL_SOURCE_MOD = new java.util.HashMap<>();
    static {
        MATERIAL_SOURCE_MOD.put("cursed_metal", "goety");        // 诡厄诅咒金属锭
        MATERIAL_SOURCE_MOD.put("dark_metal", "goety");          // 诡厄黑暗金属锭
        MATERIAL_SOURCE_MOD.put("divine_gold", "goety_revelation");   // 神灵金（诡厄启示录联动材料：broken_halo/ascension_halo）
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
        return com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader.isLoaded(sourceMod);
    }

    // ============================================================
    //  辅助方法：添加所有工具材质变体（部件清单取自 ToolDefinition，与匠魂本体一致）
    // ============================================================

    private static void addAllToolVariants(CreativeModeTab.Output output, ToolDefinition definition, Item toolItem) {
        if (definition == null || toolItem == null) return;

        // ⭐ 与匠魂本体同款逻辑（ToolBuildHandler#createSingleMaterial）：
        //    · 首选材料能用的部件 → 用该材料；
        //    · 用不了的部件 → **自动换成"该部件统计类型的第一个可用材料"**（不因此丢掉整个变体）；
        //    · 该材料在所有部件上都用不了 → 整个变体跳过（绝不生成"没有材料"的工具）。
        //    于是 tall_skull（只有 tconstruct:skull 统计）这类材料会被自动跳过，无需硬编码名单。
        if (!(toolItem instanceof IModifiable modifiable)) return;
        if (!definition.isDataLoaded() || ToolMaterialHook.stats(definition).isEmpty()) {
            output.accept(new ItemStack(toolItem));   // 定义/部件没读到：与匠魂一致，只放本体
            return;
        }

        Collection<IMaterial> materials = MaterialRegistry.getInstance().getAllMaterials();

        for (IMaterial material : materials) {
            MaterialId materialId = material.getIdentifier();
            if (!materialId.getNamespace().equals(TinkersNewlife.MOD_ID)) continue;
            if (!isMaterialDependencyLoaded(materialId)) continue;   // 联动来源 mod 未装 → 创造栏不显示

            try {
                // 匠魂本体语义：可用的部件用该材料，不可用的部件自动用该类型的第一个可用材料
                ItemStack result = ToolBuildHandler.createSingleMaterial(modifiable, MaterialVariant.of(material));
                if (result.isEmpty()) continue;   // 该材料在所有部件上都用不了 → 跳过这个变体

                // ⭐ 噤默手套：生成时即确定额外戒指槽数量（1~6，服务端固定写入持久数据，之后不再变化）
                if (result.getItem() instanceof SilentGloveItem) {
                    SilentGloveItem.getOrCreateExtraRings(result);
                }
                output.accept(result);
            } catch (Throwable t) {
                // 单个材料出问题（缺部件统计等）只跳过它，绝不让整个创造栏构建失败
                TinkersNewlife.LOGGER.warn("[创造栏] 工具 {} 的材料变体 {} 生成失败，已跳过：{}",
                        toolItem, materialId, t.toString());
            }
        }
    }
}