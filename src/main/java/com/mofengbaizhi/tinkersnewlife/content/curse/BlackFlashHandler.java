package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.binding.BindingStateHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.BlackFlashParticleHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.KnockbackHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BlackFlashHandler {

    private static final ModifierId BLACK_FLASH_ID = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "black_flash")
    );

    /** 西中之虎：加持在黑闪武器上时，黑闪基础概率额外增加（玩家当前攻击力 ÷ 10000） */
    private static final ModifierId WEST_TIGER_ID = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID, "west_tiger")
    );

    private static final double BASE_CHANCE = 0.1;
    private static final double PROBABILITY_BOOST = 0.1;
    private static final int BUFF_DURATION_TICKS = 60 * 20;
    private static final double BUFF_MULTIPLIER = 1.2;

    private static final Map<UUID, PlayerData> PLAYER_DATA = new ConcurrentHashMap<>();

    /** 玩家是否处于黑闪增幅状态（供咒力系统等查询，黑闪后 60 秒内） */
    public static boolean isBuffActive(UUID playerId) {
        PlayerData data = PLAYER_DATA.get(playerId);
        return data != null && data.isBuffActive();
    }

    private static class PlayerData {
        double probabilityBoost;
        int remainingTicks;
        AttributeModifier speedModifier;
        AttributeModifier damageModifier;
        AttributeModifier jumpModifier;

        float baseDamageValue;

        PlayerData() {
            this.probabilityBoost = 0;
            this.remainingTicks = 0;
            this.baseDamageValue = 0;
            this.speedModifier = null;
            this.damageModifier = null;
            this.jumpModifier = null;
        }

        boolean isBuffActive() {
            return remainingTicks > 0;
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof Player)) return;
        Player player = (Player) event.getSource().getEntity();

        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity target = (LivingEntity) event.getEntity();

        if (player.level().isClientSide) return;

        // ⭐ 天与咒缚·暴君：黑闪概率锁定为 0（无法再打出黑闪）
        if (BindingStateHandler.isRestricted(player)) return;

        // ✅ 统一获取攻击工具（近战/弹射物/悠悠球从球实体读取），主手无武器时兜底取佩戴的咒力核心
        ToolStack tool = ToolHelper.getCombatToolWith(event.getSource(), player, BLACK_FLASH_ID);
        if (tool == null) return;
        int level = tool.getModifierLevel(BLACK_FLASH_ID);
        if (level <= 0) return;

        PlayerData data = PLAYER_DATA.computeIfAbsent(player.getUUID(), k -> new PlayerData());
        double totalChance = BASE_CHANCE + data.probabilityBoost;

        // 🐯 西中之虎：黑闪基础概率额外增加（玩家当前攻击力 ÷ 10000）
        if (tool.getModifierLevel(WEST_TIGER_ID) > 0) {
            double attack = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            totalChance += attack / 10000.0;
        }

        if (player.getRandom().nextDouble() < totalChance) {
            float originalDamage = event.getAmount();
            float flashDamage = (float) Math.pow(originalDamage, 2.5);

            event.setAmount(flashDamage);

            // ✅ 黑闪特效 + 强击退（传入方向）
            if (player.level() instanceof ServerLevel) {
                ServerLevel serverLevel = (ServerLevel) player.level();
                
                // 计算从玩家指向目标的方向（用于扇形闪电朝向）
                Vec3 direction = target.position().subtract(player.position()).normalize();
                
                // 播放扇形红黑闪电
                BlackFlashParticleHelper.spawnBlackFlash(serverLevel, target.position(), direction);
                
                // 强击退
                KnockbackHelper.applyStrongKnockback(target, player, 3.0, 0.8);
            }

            data.probabilityBoost += PROBABILITY_BOOST;
            data.remainingTicks = BUFF_DURATION_TICKS;
            data.baseDamageValue = originalDamage;

            applyBuff(player, data, originalDamage);

            TinkersNewlife.LOGGER.debug("[黑闪] 玩家 {} 打出黑闪！伤害: {:.1f} -> {:.1f}，当前概率加成: {}%",
                    player.getName().getString(),
                    originalDamage, flashDamage,
                    data.probabilityBoost * 100);
        }
    }

    // ==================== 应用 / 移除属性增益 ====================

    private static void applyBuff(Player player, PlayerData data, float currentDamage) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance damageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance jumpAttr = player.getAttribute(Attributes.JUMP_STRENGTH);

        removeBuff(player, data);

        UUID uuid = player.getUUID();

        double speedBase = speedAttr != null ? speedAttr.getBaseValue() : 0.1;
        double speedBonus = speedBase * (BUFF_MULTIPLIER - 1);

        double jumpBase = jumpAttr != null ? jumpAttr.getBaseValue() : 0.42;
        double jumpBonus = jumpBase * (BUFF_MULTIPLIER - 1);

        double damageBonus = currentDamage * 0.2;

        if (damageBonus < 0.1) {
            double damageBase = damageAttr != null ? damageAttr.getBaseValue() : 1.0;
            damageBonus = damageBase * 0.2;
        }

        data.speedModifier = new AttributeModifier(
                uuid, "black_flash_speed",
                speedBonus, AttributeModifier.Operation.ADDITION
        );
        data.damageModifier = new AttributeModifier(
                uuid, "black_flash_damage",
                damageBonus, AttributeModifier.Operation.ADDITION
        );
        data.jumpModifier = new AttributeModifier(
                uuid, "black_flash_jump",
                jumpBonus, AttributeModifier.Operation.ADDITION
        );

        if (speedAttr != null && !speedAttr.hasModifier(data.speedModifier)) {
            speedAttr.addTransientModifier(data.speedModifier);
        }
        if (damageAttr != null && !damageAttr.hasModifier(data.damageModifier)) {
            damageAttr.addTransientModifier(data.damageModifier);
        }
        if (jumpAttr != null && !jumpAttr.hasModifier(data.jumpModifier)) {
            jumpAttr.addTransientModifier(data.jumpModifier);
        }
    }

    private static void removeBuff(Player player, PlayerData data) {
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance damageAttr = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance jumpAttr = player.getAttribute(Attributes.JUMP_STRENGTH);

        if (speedAttr != null && data.speedModifier != null) {
            speedAttr.removeModifier(data.speedModifier);
        }
        if (damageAttr != null && data.damageModifier != null) {
            damageAttr.removeModifier(data.damageModifier);
        }
        if (jumpAttr != null && data.jumpModifier != null) {
            jumpAttr.removeModifier(data.jumpModifier);
        }

        data.speedModifier = null;
        data.damageModifier = null;
        data.jumpModifier = null;
    }

    // ==================== 服务端 Tick ====================
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;

        int tickCount = event.getServer().getTickCount();
        if (tickCount % 20 != 0) return;

        for (Map.Entry<UUID, PlayerData> entry : PLAYER_DATA.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerData data = entry.getValue();

            Player player = event.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || !player.isAlive()) {
                if (player != null) {
                    removeBuff(player, data);
                }
                PLAYER_DATA.remove(uuid);
                continue;
            }

            if (data.isBuffActive()) {
                data.remainingTicks -= 20;

                if (!data.isBuffActive()) {
                    removeBuff(player, data);
                    data.probabilityBoost = 0;
                    data.baseDamageValue = 0;
                    TinkersNewlife.LOGGER.debug("[黑闪] 玩家 {} 的黑闪增益已过期", player.getName().getString());
                }
            }
        }

        PLAYER_DATA.entrySet().removeIf(entry -> {
            UUID uuid = entry.getKey();
            Player player = event.getServer().getPlayerList().getPlayer(uuid);
            return player == null || !player.isAlive();
        });
    }

    // ==================== 玩家登出清理 ====================
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        PlayerData data = PLAYER_DATA.remove(player.getUUID());
        if (data != null) {
            removeBuff(player, data);
        }
    }
}