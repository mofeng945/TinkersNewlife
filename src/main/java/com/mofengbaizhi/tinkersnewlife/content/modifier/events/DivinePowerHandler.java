package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.content.modifier.DivinePowerModifier;
import com.mofengbaizhi.tinkersnewlife.util.IronSpellsReflector;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.UUID;

/**
 * 近战特性·神圣之力结算器（联动铁魔法）：
 * <ul>
 *   <li>持有时给持有者 +180 MAX_MANA（铁魔法法力上限属性）；</li>
 *   <li>武器注入预设法术容器（火墙术Lv5/天使之翼Lv5/治愈之环Lv10），模仿 ValetteinItemMixin；</li>
 *   <li>火墙术伤害无视无敌帧（命中前清零目标 invulnerableTime）。</li>
 * </ul>
 * 铁魔法未安装时所有逻辑自然失效（反射软依赖）。
 */
@Mod.EventBusSubscriber(modid = com.mofengbaizhi.tinkersnewlife.TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DivinePowerHandler {

    private static final UUID MANA_MODIFIER = UUID.fromString("7d2c9e1a-4f61-4b20-8a7d-3c5e9f0a2b64");
    private static final double MANA_BONUS = 180.0;

    private DivinePowerHandler() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity holder = event.getEntity();
        if (holder.level().isClientSide) return;
        if (!(holder instanceof Player)) return; // 法力/法术注入仅玩家持有者
        if (!hasDivinePower(holder.getMainHandItem())) return;

        // 注入法术容器（仅首次）
        if (IronSpellsReflector.hasIronSpells() && holder.tickCount % 20 == 0) {
            ItemStack stack = holder.getMainHandItem();
            IronSpellsReflector.initSpellContainer(stack);
        }

        // +180 法力上限（transient 修饰符，持续生效）
        if (IronSpellsReflector.hasIronSpells()) {
            Attribute maxMana = IronSpellsReflector.maxManaAttribute();
            if (maxMana != null) {
                AttributeInstance inst = holder.getAttribute(maxMana);
                if (inst != null) {
                    inst.removeModifier(MANA_MODIFIER);
                    inst.addTransientModifier(new AttributeModifier(
                            MANA_MODIFIER, "divine_power_mana", MANA_BONUS, AttributeModifier.Operation.ADDITION));
                }
            }
        }
    }

    /** 火墙术伤害无视无敌帧：火墙实体（类名含 WallOfFire）的施法者持有神圣之力 → 清目标无敌帧 */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        net.minecraft.world.entity.Entity direct = event.getSource().getDirectEntity();
        if (direct == null) return;
        String n = direct.getClass().getSimpleName().toLowerCase();
        if (!n.contains("walloffire")) return;
        LivingEntity caster = ownerOf(direct);
        if (caster != null && hasDivinePower(caster.getMainHandItem())) {
            target.invulnerableTime = 0;
        }
    }

    /** 反射取实体拥有者/施法者（getOwner/getCaster 任一） */
    private static LivingEntity ownerOf(net.minecraft.world.entity.Entity entity) {
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("getOwner");
            Object o = m.invoke(entity);
            if (o instanceof LivingEntity le) return le;
        } catch (Throwable ignored) {
        }
        try {
            java.lang.reflect.Method m = entity.getClass().getMethod("getCaster");
            Object o = m.invoke(entity);
            if (o instanceof LivingEntity le) return le;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean hasDivinePower(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool != null && tool.getModifierLevel(DivinePowerModifier.ID) > 0;
    }
}
