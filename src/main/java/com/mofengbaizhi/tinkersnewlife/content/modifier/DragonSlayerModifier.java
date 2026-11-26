package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierId;

@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class DragonSlayerModifier extends Modifier {
    public static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife","dragon_slayer"));
    @Override protected void registerHooks(slimeknights.tconstruct.library.module.ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(DragonSlayerShield.STATS, slimeknights.tconstruct.library.modifiers.ModifierHooks.TOOL_STATS);
    }
    private static int armor(LivingEntity wearer,EquipmentSlot slot) {
        ItemStack stack=wearer.getItemBySlot(slot);
        if(!stack.is(TinkerTags.Items.WORN_ARMOR) || LivingEntity.getEquipmentSlotForItem(stack)!=slot)return 0;
        return ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack),ID);
    }
    private static int shield(ItemStack stack) {
        return stack.is(TinkerTags.Items.SHIELDS)?ToolHelper.getActiveModifierLevel(ToolHelper.getToolStack(stack),ID):0;
    }
    @SubscribeEvent(priority=EventPriority.LOW) public static void onDamage(LivingDamageEvent event) {
        var e=event.getEntity();if(e.level().isClientSide || event.getAmount()<=0 || !IntegrationLoader.isIceAndFire())return;
        int h=armor(e,EquipmentSlot.HEAD),c=armor(e,EquipmentSlot.CHEST),l=armor(e,EquipmentSlot.LEGS),f=armor(e,EquipmentSlot.FEET);
        if((h|c|l|f)==0 || DragonSlayerShield.isExcluded(event.getSource()))return;
        event.setAmount(event.getAmount()*DragonSlayerProtection.multiplier(h,c,l,f,DragonSlayerShield.isDragonBreath(event.getSource())));
    }
    @SubscribeEvent public static void onWearerTick(LivingEvent.LivingTickEvent event) {
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer e))return;
        if(!e.isAlive() || e.isSpectator() || (e.tickCount+e.getId())%10!=0 || !IntegrationLoader.isIceAndFire())return;
        long levels=(long)armor(e,EquipmentSlot.HEAD)+armor(e,EquipmentSlot.CHEST)+armor(e,EquipmentSlot.LEGS)+armor(e,EquipmentSlot.FEET)
                +shield(e.getMainHandItem())+shield(e.getOffhandItem());
        if(levels>=4) DragonSlayerFear.refresh(e,levels);
    }
}
