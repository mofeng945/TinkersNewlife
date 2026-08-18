package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.combat.MeleeDamageModifierHook;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.context.ToolAttackContext;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID)
public class ModularStaffModifier extends Modifier implements MeleeDamageModifierHook {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModularStaffModifier.class);

    // 近战附加伤害：固定 1.5（不再受等级影响）
    private static final float MELEE_BONUS = 1.5f;

    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        super.registerHooks(hookBuilder);
        hookBuilder.addHook(this, ModifierHooks.MELEE_DAMAGE);
    }

    // ============================================================
    //  功能1：近战附加固定伤害（不受等级影响）
    // ============================================================
    @Override
    public float getMeleeDamage(IToolStackView tool, ModifierEntry modifier,
                                ToolAttackContext context, float baseDamage, float damage) {
        // 直接附加固定值 1.5
        return damage + MELEE_BONUS;
    }

    // ============================================================
    //  功能2：法术增伤（附加伤害 + 法术强度增幅）
    // ============================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        float originalDamage = event.getAmount();

        // 1. 判断是否为玩家造成的伤害
        if (!(source.getEntity() instanceof Player player)) return;

        // 2. 判断是否为法术伤害
        if (!isSpellDamage(source)) return;

        // 3. 检查主手或副手是否持有模块化魔杖
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        ItemStack staffStack = ItemStack.EMPTY;

        if (mainHand.getItem() instanceof ModularStaffItem) {
            staffStack = mainHand;
        } else if (offHand.getItem() instanceof ModularStaffItem) {
            staffStack = offHand;
        } else {
            return;
        }

        // 4. 获取魔杖上的特性等级（通常为1）
        ToolStack tool = ToolStack.from(staffStack);
        if (tool == null) return;

        int level = tool.getModifierLevel(Modifiers.MODULAR_STAFF_MODIFIER.getId());
        if (level <= 0) return;

        // ============================================================
        //  5. 获取法杖近战伤害和玩家基础近战伤害
        // ============================================================

        // 法杖近战伤害（来自匠魂工具统计）
        float staffDamage = tool.getStats().get(ToolStats.ATTACK_DAMAGE);

        // 玩家主手近战伤害（即玩家的攻击力属性）
        AttributeInstance attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        float basePlayerDamage = attackAttr != null ? (float) attackAttr.getValue() : 1.0f;

        // ============================================================
        //  6. 计算加成
        // ============================================================

        // 附加伤害 = 法杖伤害 * 50% + 玩家基础伤害 * 20%
        float bonusDamage = staffDamage * 0.5f + basePlayerDamage * 0.2f;

        // 法术强度增幅（百分比）= (法杖伤害 + 玩家基础伤害) / 10 * 0.01
        float powerMultiplier = (staffDamage + basePlayerDamage) / 10.0f * 0.01f;

        // 最终伤害 = 原伤害 * (1 + 增幅) + 附加伤害
        float newDamage = originalDamage * (1.0f + powerMultiplier) + bonusDamage;
        event.setAmount(newDamage);

        // 7. 粒子特效
        if (player.level() instanceof ServerLevel server) {
            LivingEntity target = event.getEntity();
            server.sendParticles(ParticleTypes.ENCHANT,
                    target.getX(),
                    target.getY() + target.getBbHeight() / 2,
                    target.getZ(),
                    8, 0.3, 0.3, 0.3, 0.1);
        }
    }

    // ============================================================
    //  法术伤害判断
    // ============================================================
    private static boolean isSpellDamage(DamageSource source) {
        String msgId = source.getMsgId();
        if (msgId == null) return false;

        // 铁魔法学派
        if ("blood_magic".equals(msgId)) return true;
        if ("fire_magic".equals(msgId)) return true;
        if ("ice_magic".equals(msgId)) return true;
        if ("lightning_magic".equals(msgId)) return true;
        if ("holy_magic".equals(msgId)) return true;
        if ("ender_magic".equals(msgId)) return true;
        if ("evocation_magic".equals(msgId)) return true;
        if ("nature_magic".equals(msgId)) return true;
        if ("eldritch_magic".equals(msgId)) return true;

        // 其他模组
        if ("geo_magic".equals(msgId)) return true;
        if ("magic".equals(msgId)) return true;
        if ("indirectMagic".equals(msgId)) return true;
        if ("goety_magic".equals(msgId)) return true;
        if ("ars_nouveau".equals(msgId)) return true;
        if ("ars_magic".equals(msgId)) return true;

        return false;
    }
}