package com.mofengbaizhi.tinkersnewlife.client.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.renderer.WizardArmorLayer;
import com.mofengbaizhi.tinkersnewlife.content.item.WizardArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ⭐ 小体型 / 模组人形生物兼容（用户 2026-09-19 要求"所有会穿盔甲的人形生物" ✓）：
 * 玩家与盔甲架由 {@link WizardArmorLayer} 负责 ✓，其余"模型是 HumanoidModel + 戴着本模组盔甲"的实体在这里补画 ✓。
 *
 * <p>尺寸**跟随实体自身缩放** ✓（宝宝在渲染器里已缩 0.5 ✓、模组小体型生物自带 scale ✓），此处不再缩放 ✓；
 * 也不挂实体名单 ✓ ⇒ 原版 / 模组 / 宝宝一体覆盖 ✓（符合"软依赖铁律"：不引用任何模组类 ✓）。
 *
 * <p>⚠⚠ <b>总线别搞错</b>：{@link RenderLivingEvent} 是 <b>FORGE 总线</b>事件 ✗，
 * 放进 {@code bus = Bus.MOD} 的订阅类里会在**游戏启动时直接崩** ✗
 * （实测 2026-09-19：<i>"has @SubscribeEvent annotation, but takes an argument that is not a subtype of IModBusEvent"</i> ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class WizardArmorRenderHandler {

    private WizardArmorRenderHandler() {}

    /** 参数用**原始类型** RenderLivingEvent.Post ✓ —— 泛型写太细容易被事件总线的类型校验挑刺 ✗ */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity instanceof ArmorStand) {
            return;   // 图层已处理 ✓ 这里再画就双画了 ✗
        }
        if (!(event.getRenderer().getModel() instanceof HumanoidModel<?> parent)) return;
        boolean wears = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (entity.getItemBySlot(slot).getItem() instanceof WizardArmorItem) { wears = true; break; }
        }
        if (!wears) return;
        WizardArmorLayer.renderArmor(entity, parent, event.getPoseStack(),
                event.getMultiBufferSource(), event.getPackedLight());
    }
}