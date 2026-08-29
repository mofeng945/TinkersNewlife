package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mofengbaizhi.tinkersnewlife.util.ProjectileWeaponHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
        return damage + MELEE_BONUS;
    }

    // ============================================================
    //  功能2：法术增伤（附加伤害 + 法术强度增幅）
    // ============================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return; // ⭐ 客户端 setAmount 无效，且防止双端重复计算
        DamageSource source = event.getSource();
        if (!isSpellDamage(source)) return;
        float originalDamage = event.getAmount();

        float newDamage = getSpellAmplification(player, originalDamage);
        if (newDamage <= originalDamage) return; // 未持有有效魔杖，不处理
        event.setAmount(newDamage);

        // 粒子特效
        if (player.level() instanceof ServerLevel server) {
            LivingEntity target = event.getEntity();
            server.sendParticles(ParticleTypes.ENCHANT,
                    target.getX(),
                    target.getY() + target.getBbHeight() / 2,
                    target.getZ(),
                    8, 0.3, 0.3, 0.3, 0.1);
        }
    }

    /**
     * 模块化魔杖增幅（法术/咒术共用）：
     * 玩家主手或副手持有有效模块化魔杖（带「模块化魔杖」特性）时，
     * 伤害 = 原伤害 × (1 + (魔杖攻击力+玩家基础伤害)/10×0.01) + (魔杖攻击力×0.5 + 玩家基础伤害×0.2)。
     * 未持有有效魔杖时返回原伤害。
     */
    public static float getSpellAmplification(Player player, float originalDamage) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        ItemStack staffStack = ItemStack.EMPTY;

        if (mainHand.getItem() instanceof ModularStaffItem) {
            staffStack = mainHand;
        } else if (offHand.getItem() instanceof ModularStaffItem) {
            staffStack = offHand;
        } else {
            return originalDamage;
        }

        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(staffStack);
        if (tool == null) return originalDamage;

        int level = tool.getModifierLevel(Modifiers.MODULAR_STAFF_MODIFIER.getId());
        if (level <= 0) return originalDamage;

        // 法杖近战伤害
        float staffDamage = tool.getStats().get(ToolStats.ATTACK_DAMAGE);

        // 玩家基础近战伤害
        AttributeInstance attackAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        float basePlayerDamage = attackAttr != null ? (float) attackAttr.getValue() : 1.0f;

        // 计算加成
        float bonusDamage = staffDamage * 0.5f + basePlayerDamage * 0.2f;
        float powerMultiplier = (staffDamage + basePlayerDamage) / 10.0f * 0.01f;

        return originalDamage * (1.0f + powerMultiplier) + bonusDamage;
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