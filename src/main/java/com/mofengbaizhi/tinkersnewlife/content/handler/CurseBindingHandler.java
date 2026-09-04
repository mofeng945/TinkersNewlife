package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 天与咒缚·咒力 状态（服务端）——与「暴君」相反方向的束缚
 * <p>
 * 由仪式「天与咒缚·咒力」赋予：
 * <ul>
 *   <li>肉体削弱：基础生命上限 / 移动速度 / 攻击伤害各 ×0.5（永久属性修饰符）</li>
 *   <li>咒力亲和：自带 +200（{@link #getAffinityBonus}，由 CursePowerHelper 计入）</li>
 *   <li>佩戴咒力核心时：咒力总量与咒力输出各自动 +1 级（{@link #getCoreLevelBonus}）</li>
 * </ul>
 * 与「暴君」束缚互相排斥：举行对立的仪式会转换束缚（本状态应用/解除时联动）。
 * 状态存玩家持久数据 + 永久属性修饰符，死亡/重登不解除（Clone 转移）；每 5 秒自检补挂。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CurseBindingHandler {

    private CurseBindingHandler() {}

    /** 玩家持久数据：是否处于天与咒缚·咒力 */
    public static final String KEY_CURSE_BINDING = "tinkersnewlife.curse_binding";

    /** 自带咒力亲和 */
    public static final int AFFINITY_BONUS = 200;
    /** 佩戴咒力核心时的总量/输出等级加成 */
    public static final int CORE_LEVEL_BONUS = 1;

    // ==================== 肉体削弱修饰符（固定 UUID 便于解除） ====================

    /** 生命上限 ×0.5 */
    private static final UUID HEALTH_UUID = UUID.fromString("e6f7a8b9-1c2d-4e3f-8a9b-0c1d2e3f4050");
    /** 速度 ×0.5 */
    private static final UUID SPEED_UUID = UUID.fromString("e6f7a8b9-1c2d-4e3f-8a9b-0c1d2e3f4051");
    /** 攻击 ×0.5 */
    private static final UUID ATTACK_UUID = UUID.fromString("e6f7a8b9-1c2d-4e3f-8a9b-0c1d2e3f4052");

    private static final AttributeModifier HEALTH_MOD = new AttributeModifier(
            HEALTH_UUID, "curse_binding_health", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier SPEED_MOD = new AttributeModifier(
            SPEED_UUID, "curse_binding_speed", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier ATTACK_MOD = new AttributeModifier(
            ATTACK_UUID, "curse_binding_attack", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);

    // ==================== 状态查询 ====================

    /** 是否处于天与咒缚·咒力 */
    public static boolean isBound(Player player) {
        return player != null && player.getPersistentData().getBoolean(KEY_CURSE_BINDING);
    }

    /** 咒力亲和加成（自带 200；未绑定返回 0） */
    public static int getAffinityBonus(Player player) {
        return isBound(player) ? AFFINITY_BONUS : 0;
    }

    /** 咒力核心等级加成（佩戴时总量/输出各 +1；未绑定返回 0） */
    public static int getCoreLevelBonus(Player player) {
        return isBound(player) ? CORE_LEVEL_BONUS : 0;
    }

    // ==================== 赋予 / 解除 ====================

    /** 仪式二完成：赋予天与咒缚·咒力（仪式层面已互斥；此处防御性移除对方的标识，避免两种状态叠加） */
    public static void applyBinding(ServerPlayer player) {
        if (HeavenlyRestrictionHandler.isRestricted(player)) {
            HeavenlyRestrictionHandler.removeRestriction(player);
        }
        player.getPersistentData().putBoolean(KEY_CURSE_BINDING, true);
        applyMods(player);
        TinkersNewlife.LOGGER.info("[天与咒缚·咒力] 玩家 {} 获得咒力束缚（生命/速度/伤害×0.5；咒力亲和+200；核心总量/输出+1）",
                player.getName().getString());
    }

    /** 解除咒力束缚（转暴君或未来解除仪式用） */
    public static void removeBinding(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_CURSE_BINDING, false);
        removeMods(player);
        TinkersNewlife.LOGGER.info("[天与咒缚·咒力] 玩家 {} 的咒力束缚已解除", player.getName().getString());
    }

    // ==================== 属性修饰符 ====================

    private static void applyMods(ServerPlayer player) {
        addMod(player, Attributes.MAX_HEALTH, HEALTH_MOD);
        addMod(player, Attributes.MOVEMENT_SPEED, SPEED_MOD);
        addMod(player, Attributes.ATTACK_DAMAGE, ATTACK_MOD);
    }

    private static void addMod(ServerPlayer player, Attribute attribute, AttributeModifier modifier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(modifier)) {
            instance.addPermanentModifier(modifier);
        }
    }

    private static void removeMods(ServerPlayer player) {
        removeMod(player, Attributes.MAX_HEALTH, HEALTH_UUID);
        removeMod(player, Attributes.MOVEMENT_SPEED, SPEED_UUID);
        removeMod(player, Attributes.ATTACK_DAMAGE, ATTACK_UUID);
    }

    private static void removeMod(ServerPlayer player, Attribute attribute, UUID uuid) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
        }
    }

    // ==================== 自检维护（每 5 秒） ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 100 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isBound(player)) continue;
            // 属性修饰符若被意外移除则补挂
            applyMods(player);
        }
    }

    // ==================== 死亡 / 重生：固化状态 ====================

    /**
     * 玩家死亡重生（Clone）时，把咒力束缚状态与属性修饰符转移到新实体，
     * 保证效果固化——死亡也绝不解除（转暴君/未来解除仪式才可解除）。
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!isBound(event.getOriginal())) return;
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        newPlayer.getPersistentData().putBoolean(KEY_CURSE_BINDING, true);
        applyMods(newPlayer);
        TinkersNewlife.LOGGER.info("[天与咒缚·咒力] 玩家 {} 死亡重生，咒力束缚固化保留",
                newPlayer.getName().getString());
    }
}
