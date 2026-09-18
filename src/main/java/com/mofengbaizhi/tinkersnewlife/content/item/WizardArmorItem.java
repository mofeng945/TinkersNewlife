package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;

import java.util.function.Consumer;

/**
 * 巫师套装的盔甲物品：在匠魂可改造盔甲的基础上，换成<b>自绘模型</b>（尖顶法帽 + 法袍 ✓）。
 *
 * <p>为什么需要这个子类：匠魂盔甲默认走它自己的盔甲模型（板甲形 ✗），
 * 而用户要的是"尖顶法帽 + 法袍"的轮廓 ⇒ 必须按步骤文档 §6.5 <b>路线 B</b>：
 * 实现 {@link IClientItemExtensions#getHumanoidArmorModel} 交出自己烘焙的 {@link HumanoidModel} ✓。
 *
 * <p>⚠ 路线 B 的已知代价（步骤文档也写了）：**匠魂的"按材质自动上色"不会自动生效** ✗ ——
 * 所以这里直接给一张由脚本生成的贴图（{@code textures/armor/wizard_armor/robe.png} ✓，
 * 见 {@code tools/gen-wizard-armor-texture.ps1} ✓）；将来若要"每材料一色"，
 * 在这个方法里按物品材料换贴图路径即可 ✓（已留好单一入口 ✓）。
 *
 * <p>客户端类只在这个方法体里出现 ⇒ 服务端不会加载它们 ✓（Forge 的标准写法 ✓）。
 */
public class WizardArmorItem extends ModifiableArmorItem {

    /** 自绘模型用贴图（1.20.1 的盔甲贴图钩子返回 String ✓） */
    private static final String ROBE_TEXTURE_PATH = TinkersNewlife.MOD_ID + ":textures/armor/wizard_armor/robe.png";

    public WizardArmorItem(ModifiableArmorMaterial material, ArmorItem.Type type, Properties properties) {
        super(material, type, properties);
    }

    /** 按**装备槽**选贴图 ⇒ 四件各自显示自己的造型 ✓（四张贴图只画自己那组 UV 区域、其余透明 ✓） */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        String path = switch (slot) {
            case HEAD -> com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel.TEX_HAT;
            case CHEST -> com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel.TEX_ROBE;
            case LEGS -> com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel.TEX_LEGGINGS;
            default -> com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel.TEX_BOOTS;
        };
        return TinkersNewlife.MOD_ID + ":" + path;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel model;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                          EquipmentSlot slot, HumanoidModel<?> original) {
                if (model == null) {
                    model = new com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel(
                            net.minecraft.client.Minecraft.getInstance().getEntityModels()
                                    .bakeLayer(com.mofengbaizhi.tinkersnewlife.client.model.WizardArmorModel.LAYER));
                }
                return model;
            }        });
    }
}
