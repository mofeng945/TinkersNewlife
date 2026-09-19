package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.WizardArmorTextures;
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

    /** 四件共用一张**灰阶**贴图：颜色由模型逐组用材料色顶点着色 ✓（见 WizardArmorModel） */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        // ⚠ 只有"本模组的渲染层真的会逐组画"的实体才可以给透明图 ✗
        //   之前是**全局**判断（layerOk 一 true 就给透明图）⇒ 你穿上法师套之后，
        //   **假人 / 其它实体**身上的这套盔甲也被原版层画成透明 ⇒ 整件消失 ✗（用户实测 ✓）。
        // ⭐ 逐实体判断（玩家/盔甲架看图层 ✓；其余人形生物看 RenderLivingEvent 那条路 ✓）——
        //   否则仆从身上原版层会用原版模型再画一遍 ✗（"多一条/多一件" ✓ 用户实测 ✓）
        boolean layerDraws = WizardArmorTextures.drawsWizardLayer(entity);
        if (layerDraws) {
            return TinkersNewlife.MOD_ID + ":textures/armor/wizard/transparent.png";
        }
        // 本层不管的实体（僵尸、其它 mod 的假人…）⇒ 给一张**真贴图**：优先该材料生成的图 ✓，否则灰阶图 ✓
        return WizardArmorTextures.fallbackArmorTexture(stack, slot);
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
                model.setCurrent(stack, slot);   // ⭐ 告诉模型"这一件 + 哪个槽"⇒ 只画该件并逐组染材料色 ✓
            // ⭐ 原版盔甲层（非本模组自绘路径的实体）拿到的也是这个模型 ✗
            //   ⇒ 必须先告诉它"这是哪一件"，否则它按默认槽（帽子）画 ⇒ 出现"裤在躯干、衣在腿上" ✗（用户实测 ✓）
            model.setCurrent(stack, slot);
                return model;
            }        });
    }
}
