package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具/装备的<b>破损贴图</b>接线（客户端）。
 *
 * <h2>我们用的是匠魂自己的破损机制，不是自创的</h2>
 * <ul>
 *   <li>耐久耗光 → 匠魂往物品 NBT 写 {@code tic_broken: 1b}
 *       （{@code ToolDamageUtil.breakTool} / {@code ToolStack.TAG_BROKEN}）</li>
 *   <li>匠魂自己的工具靠 {@code TinkerItemProperties.registerBrokenProperty(item)} 注册物品属性
 *       {@code tconstruct:broken}，并在**基模型 JSON 的 {@code overrides} 里**写
 *       {@code {"predicate":{"tconstruct:broken":1},"model":"tconstruct:item/tool/<名>/broken"}}
 *       （见 {@code AbstractToolItemModelProvider#tool}）</li>
 *   <li>破损模型就是基模型的一份拷贝，只把部件贴图名统一加后缀 {@code _broken}；
 *       贴图则是 {@code <部件>_broken.png} / {@code <部件>_broken_<材质>.png}
 *       （由 {@code tools\GenBrokenTextures.ps1} 从你的原图派生）</li>
 * </ul>
 *
 * <h2>为什么这里不照抄"往基模型 JSON 里加 overrides"</h2>
 * 铁律：{@code models/**} 下的**既有文件一律不许改**（那是用户手绘/Blockbench 导出的）。
 * 所以改成等价的 Java 接线，效果完全一样、且用户以后重新导出模型也不会把接线冲掉：
 * <ol>
 *   <li>{@link ModelEvent.RegisterAdditional}：把 {@code models/item/tool/<名>/broken.json}
 *       注册进烘焙队列（否则没人引用的模型根本不会被烘焙）</li>
 *   <li>{@link ModelEvent.ModifyBakingResult}：把物品的已烘焙模型外面包一层
 *       {@link BrokenAwareModel}，它的 {@code getOverrides()} 在 {@code tic_broken} 时切到破损模型</li>
 * </ol>
 * 换模型走 {@code ItemOverrides} ⇒ 背包/手上/地上/展示框/盔甲架全部自动生效
 * （它们都经过 {@code ItemRenderer#getModel} 里的 {@code getOverrides().resolve(...)}）。
 *
 * <p>注意：破损模型自己还是个 {@code tconstruct:tool} 模型，带着匠魂的
 * {@code MaterialOverrideHandler} ⇒ 材质贴图仍然按工具实际材质解析
 * （铁质破损头取 {@code head1_broken_tconstruct_iron}，缺变体才回退到 {@code head1_broken}）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BrokenToolModels {

    private static final Logger LOGGER = LoggerFactory.getLogger("TinkersNewlife/BrokenToolModels");

    /** 物品注册名 → 破损模型位置（= assets/tinkersnewlife/models/item/tool/&lt;名&gt;/broken.json 去掉 models/ 与 .json） */
    private static final Map<ResourceLocation, ResourceLocation> ITEM_TO_BROKEN = new LinkedHashMap<>();

    private static void tool(String item) {
        ITEM_TO_BROKEN.put(
                new ResourceLocation(TinkersNewlife.MOD_ID, item),
                new ResourceLocation(TinkersNewlife.MOD_ID, "item/tool/" + item + "/broken"));
    }

    static {
        // 匠魂工具（ModifiableItem / ModifiableArmorItem，模型走 tconstruct:tool 加载器）
        tool("war_scythe");
        tool("flying_sword");
        tool("modular_staff");
        tool("dragon_staff");
        tool("silent_glove");
        tool("yo_yo");
        tool("curse_core");
        // 法师套装（WizardArmorItem extends ModifiableArmorItem）
        tool("wizard_helmet");
        tool("wizard_chestplate");
        tool("wizard_leggings");
        tool("wizard_boots");
        // 杜兰达尔：物品是匠魂工具（会 tic_broken），但图标是普通 layer0 手持模型
        tool("durandal_sword");
    }

    private BrokenToolModels() {}

    /** 让 {@code models/item/tool/<名>/broken.json} 被烘焙（没被任何东西引用的模型默认不进烘焙队列） */
    @SubscribeEvent
    public static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation broken : ITEM_TO_BROKEN.values()) {
            event.register(broken);
        }
    }

    /** 给这些物品的已烘焙模型包一层"破损感知"的 overrides */
    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (Map.Entry<ResourceLocation, ResourceLocation> entry : ITEM_TO_BROKEN.entrySet()) {
            BakedModel broken = models.get(entry.getValue());
            if (broken == null) {
                LOGGER.error("[破损贴图] 找不到烘焙好的破损模型 {}（对应物品 {}），破损态将沿用原图",
                        entry.getValue(), entry.getKey());
                continue;
            }
            ModelResourceLocation itemModel = new ModelResourceLocation(entry.getKey(), "inventory");
            BakedModel base = models.get(itemModel);
            if (base == null) {
                LOGGER.error("[破损贴图] 找不到物品模型 {}（{}），已跳过", itemModel, entry.getKey());
                continue;
            }
            models.put(itemModel, new BrokenAwareModel(base, broken));
        }
    }

    /**
     * 破损感知的模型包装：不破损时完全交给原模型（含匠魂的 {@code MaterialOverrideHandler}），
     * 破损时切到破损模型（并让它自己的 overrides 解析材质贴图）。
     */
    private static final class BrokenAwareModel extends BakedModelWrapper<BakedModel> {

        private final BakedModel broken;
        private final ItemOverrides overrides;

        BrokenAwareModel(BakedModel base, BakedModel broken) {
            super(base);
            this.broken = broken;
            ItemOverrides nested = base.getOverrides();
            this.overrides = new ItemOverrides() {
                @Override
                @Nullable
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                          @Nullable LivingEntity entity, int seed) {
                    if (ToolDamageUtil.isBroken(stack)) {
                        BakedModel resolved = broken.getOverrides().resolve(broken, stack, level, entity, seed);
                        return resolved != null ? resolved : broken;
                    }
                    BakedModel resolved = nested.resolve(model, stack, level, entity, seed);
                    return resolved != null ? resolved : model;
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }
}
