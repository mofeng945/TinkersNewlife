package com.mofengbaizhi.tinkersnewlife.client.renderer;

import com.mofengbaizhi.tinkersnewlife.client.data.ClientWuWeiData;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * 无为转变·伪装期间<b>装备与手持物品</b>的渲染。
 *
 * <p>问题：伪装渲染只把"目标生物模型"画出来（替换掉玩家模型），于是变身之后
 * 手上的工具、身上的盔甲全都看不见了。
 *
 * <p>做法（为什么不是直接在 Mixin 里补画）：原版 {@code LivingEntityRenderer#render} 里的
 * "模型空间变换"是 private/protected 的（setupRotations + scale + translate(0,-1.501,0)），
 * 在外面补画要自己复刻这套变换，稍有偏差就会错位。所以这里改为<b>给原版渲染器注入一个自定义 layer</b>：
 * layer 由原版在自己的变换栈里调用，姿态必然正确；我们只需要在 layer 里
 * "用玩家作为数据源（装备/手持物品），用生物代理作为父模型（姿态）"。
 *
 * <p>安全：只对该生物代理<em>确实</em>是某个伪装玩家时生效（查客户端伪装代理表），
 * 其它同类生物完全不受影响；整个渲染包在 try/catch 里，任何异常都只是"不画装备"。
 */
public final class WuWeiDisguiseLayers {

    private WuWeiDisguiseLayers() {}

    /** 已注入过的渲染器（弱引用集合，避免持有渲染器） */
    private static final java.util.Set<LivingEntityRenderer<?, ?>> PATCHED =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static java.lang.reflect.Field layersField;

    /** 给该渲染器注入"装备/手持"layer（幂等） */
    public static void ensure(LivingEntityRenderer<?, ?> renderer) {
        if (renderer == null || PATCHED.contains(renderer)) return;
        try {
            if (!(renderer.getModel() instanceof HumanoidModel<?>)) return;   // 只有人形模型才谈得上穿甲/持物
            if (!com.mofengbaizhi.tinkersnewlife.config.ModConfig.WUWEI_EQUIPMENT_RENDER.get()) return;
            Object layersObj = layersField().get(renderer);
            if (!(layersObj instanceof java.util.List<?> layers)) return;
            @SuppressWarnings("unchecked")
            java.util.List<Object> raw = (java.util.List<Object>) layers;
            raw.add(new EquipmentLayer(renderer));
            PATCHED.add(renderer);
        } catch (Throwable ignored) {
            // 注入失败就只是"看不到装备"，不影响伪装本身
        }
    }

    private static java.lang.reflect.Field layersField() throws NoSuchFieldException {
        if (layersField == null) {
            layersField = net.minecraftforge.fml.util.ObfuscationReflectionHelper
                    .findField(LivingEntityRenderer.class, "layers");
            layersField.setAccessible(true);
        }
        return layersField;
    }

    /** 该生物代理对应的"真实玩家"（伪装中才有）；不是伪装代理返回 null */
    @Nullable
    static Player ownerOfProxy(Entity proxy) {
        try {
            java.util.UUID ownerId = ClientWuWeiData.ownerOfProxy(proxy);
            if (ownerId == null) return null;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            Player p = mc.level.getPlayerByUUID(ownerId);
            return p;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 注入用的 layer：装备（盔甲）+ 手持物品，数据取真实玩家，姿态取生物代理 */
    private static final class EquipmentLayer extends RenderLayer<LivingEntity, EntityModel<LivingEntity>> {

        private HumanoidArmorLayer<LivingEntity, HumanoidModel<LivingEntity>, HumanoidModel<LivingEntity>> armor;
        private ItemInHandLayer<LivingEntity, HumanoidModel<LivingEntity>> hands;
        private boolean initFailed;

        @SuppressWarnings("unchecked")
        EquipmentLayer(LivingEntityRenderer<?, ?> renderer) {
            super((RenderLayerParent<LivingEntity, EntityModel<LivingEntity>>) (RenderLayerParent<?, ?>) renderer);
        }

        @SuppressWarnings("unchecked")
        private void init() {
            if (armor != null || initFailed) return;
            try {
                Minecraft mc = Minecraft.getInstance();
                HumanoidModel<LivingEntity> inner = new HumanoidModel<>(
                        mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
                HumanoidModel<LivingEntity> outer = new HumanoidModel<>(
                        mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
                // 父模型 = 当前渲染器的模型（生物代理），盔甲/手持会跟着代理的姿态走
                RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> armorParent =
                        new RenderLayerParent<>() {
                            @Override
                            public HumanoidModel<LivingEntity> getModel() {
                                EntityModel<LivingEntity> m = EquipmentLayer.this.getParentModel();
                                return m instanceof HumanoidModel<?> hm
                                        ? (HumanoidModel<LivingEntity>) hm : null;
                            }

                            @Override
                            public ResourceLocation getTextureLocation(LivingEntity entity) {
                                return null;
                            }
                        };
                if (armorParent.getModel() == null) {
                    initFailed = true;
                    return;
                }
                this.armor = new HumanoidArmorLayer<>(armorParent, inner, outer, mc.getModelManager());
                this.hands = new ItemInHandLayer<>(armorParent,
                        mc.getEntityRenderDispatcher().getItemInHandRenderer());
            } catch (Throwable t) {
                initFailed = true;
            }
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, LivingEntity proxy,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            Player owner = ownerOfProxy(proxy);
            if (owner == null) return;    // 不是伪装代理 → 什么都不做
            init();
            if (armor == null) return;
            try {
                armor.render(pose, buffer, packedLight, owner, limbSwing, limbSwingAmount,
                        partialTick, ageInTicks, netHeadYaw, headPitch);
                hands.render(pose, buffer, packedLight, owner, limbSwing, limbSwingAmount,
                        partialTick, ageInTicks, netHeadYaw, headPitch);
            } catch (Throwable ignored) {
                // 个别装备模型异常不影响整体渲染
            }
        }
    }
}
