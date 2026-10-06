package com.mofengbaizhi.tinkersnewlife.content;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * <b>一只 fufu 的全部公共行为</b>（§1079 用户口径：「提取我的 fumo 为基类」✓）——
 * <b>每个皮肤一个物品实例</b> ✓（同一个方块 {@code tinkersnewlife:fumo_mo} ＋ 不同皮肤名 ✓）。
 *
 * <p>本类就是从原来的 {@code FumoMoItem} 原样搬过来的那一份（行为零变化 ✓）：
 * <ul>
 *   <li>挂**自定义物品渲染器**（Forge 1.20.1 没有 {@code RegisterClientExtensionsEvent} ✗ ⇒ 用官方
 *       {@link Item#initializeClient} ✓ 它只在客户端被调用 ✓）；</li>
 *   <li>实现 {@link ICurioItem} ⇒ **能戴在头上** ✓（Curios 的 {@code head} 槽 ✓）；</li>
 *   <li>{@link #getEquipmentSlot} 返回 {@code HEAD} ⇒ 也能放进**原版头盔槽** ✓；</li>
 *   <li>{@link #getArmorTexture} 给"护甲层"那条路一张贴图 ✓（按皮肤给 ✓）。</li>
 * </ul>
 *
 * <p>⚠ 皮肤从这里往下走：<b>物品名</b>是翻译键 {@code item.tinkersnewlife.fumo_<皮肤名>} ✓、
 * <b>贴图</b>由 {@link FumoMoSkins#texture(String)} 决定 ✓、世界/头顶的皮肤由方块实体
 * （{@link FumoMoBlockEntity#getSkin()}）与物品栈各自携带 ✓。
 * <p>📌 默认皮肤（{@link FumoMoSkins#DEFAULT_SKIN}）＝原来的 {@code fumo_mo}：
 * 贴图仍是 {@code textures/entity/momo_common.png} ✓、名字仍是「墨封白织fufu」✓（只是翻译键从
 * {@code block.*} 改成 {@code item.*} 指向同一段文案 ✓）⇒ 存档/创造栏位置/行为都不变 ✓。
 */
public class FumoMoBaseItem extends BlockItem implements ICurioItem {

    /** 这只 fumo 的皮肤名（对应 {@code textures/fumo/<皮肤名>.png} ✓；默认皮肤是内置贴图 ✓） */
    private final String skin;

    public FumoMoBaseItem(net.minecraft.world.level.block.Block block, Item.Properties props, String skin) {
        super(block, props);
        this.skin = (skin == null || skin.isEmpty()) ? FumoMoSkins.DEFAULT_SKIN : skin;
    }

    /** 皮肤名（世界里的玩偶/头顶渲染都按它选贴图 ✓） */
    public String skin() {
        return this.skin;
    }

    /** 这只 fumo 的贴图（默认皮肤 ⇒ 原来那张 ✓） */
    public ResourceLocation texture() {
        return FumoMoSkins.texture(this.skin);
    }

    /**
     * §1079 用户口径：「物品名称在翻译键里面更改」✓ ⇒ <b>物品名一律走翻译键</b>
     * {@code item.tinkersnewlife.fumo_<皮肤名>} ✓（<b>不</b>在 Java 里写死名字 ✗）。
     * <p>⚠ {@link BlockItem} 默认会把名字指到 {@code block.tinkersnewlife.fumo_mo} ✗
     * ⇒ 这里显式改成 {@code item.*} ✓（语言文件里那条原文一字未动 ⇒ 显示名不变 ✓）。
     */
    @Override
    public String getDescriptionId() {
        return "item." + TinkersNewlife.MOD_ID + "." + FumoMoSkins.itemPath(this.skin);
    }

    /**
     * §890 用户口径：「应该能够戴在头上**和**头部饰品栏上」——
     * Forge 的 {@code IForgeItem#getEquipmentSlot} 返回 {@code HEAD} ⇒
     * 这一件就能**放进原版头盔槽** ✓（和南瓜/骷髅头同一机制 ✓）；
     * curios 头部槽则靠物品标签 {@code curios:head} ＋ §1079 新加的 curio 谓词
     * {@code tinkersnewlife:fumo_skin}（见 {@code ModCurios} ✓ ⇒ 以后新皮肤不用再改标签 ✓）。
     */
    @Override
    public EquipmentSlot getEquipmentSlot(ItemStack stack) {
        return EquipmentSlot.HEAD;
    }

    /**
     * §896：**护甲渲染必须给贴图** —— Forge 的护甲层通过 {@code IForgeItem#getArmorTexture}
     * 取贴图（返回的是**路径字符串** ✓ 不是 ResourceLocation ✗）；不给就绑到缺失贴图 ⇒
     * 模型画了也看不见 ✗（这就是之前"头顶空着"的真因 ✓）。
     * <p>§1079：改成**按皮肤给** ✓（默认皮肤返回的还是原来那串 ✓ 零变化 ✓）。
     */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return FumoMoSkins.texturePath(this.skin);
    }

    /** §892 探针：只打一次，用来判断护甲渲染路径到底有没有被调用 */
    private static volatile boolean tnl$armorLogged = false;

    @Override
    public void initializeClient(java.util.function.Consumer<
            net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.mofengbaizhi.tinkersnewlife.client.renderer.FumoMoItemRenderer.INSTANCE;
            }

            /** §891：**戴在头上能看见** —— Forge 的自定义护甲模型入口 ✓（只含 fufu ✓ 挂在 head 下 ✓） */
            @Override
            public net.minecraft.client.model.HumanoidModel<?> getHumanoidArmorModel(
                    net.minecraft.world.entity.LivingEntity entity,
                    net.minecraft.world.item.ItemStack stack,
                    net.minecraft.world.entity.EquipmentSlot slot,
                    net.minecraft.client.model.HumanoidModel<?> original) {
                if (!tnl$armorLogged) {
                    tnl$armorLogged = true;
                    org.slf4j.LoggerFactory.getLogger("TinkersNewlife/FumoMo")
                            .info("[fufu] 护甲模型被调用 slot={}（说明头盔格那条渲染路确实走了 ✓）", slot);
                }
                return com.mofengbaizhi.tinkersnewlife.client.model.FumoMoHeadModelHolder.get();
            }

        });
    }
}
