package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Optional;
import java.util.UUID;

/**
 * 天与咒缚（伏黑甚尔式束缚）状态（服务端）
 * <p>
 * 由仪式「天与咒缚·暴君」赋予、仪式二解除：
 * <ul>
 *   <li>失去咒力：无法佩戴咒力核心（curios curse_core 槽位被拒）→ 无咒力来源，黑闪概率锁定 0</li>
 *   <li>换来肉体强化：移动速度 ×5、跳跃高度 ×3、攻击力 ×10（永久属性修饰符）</li>
 * </ul>
 * 状态存玩家持久数据（死亡/重登保留）；属性用永久修饰符，另每 5 秒自检补挂，
 * 并强制摘除仍佩戴的咒力核心、把咒力清零（防残留数据）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HeavenlyRestrictionHandler {

    private HeavenlyRestrictionHandler() {}

    /** 玩家持久数据：是否处于天与咒缚 */
    public static final String KEY_RESTRICTED = "tinkersnewlife.heavenly_restriction";

    /** 被封印的饰品槽（咒力核心） */
    private static final String CURSE_CORE_SLOT = "curse_core";

    // ==================== 肉体强化修饰符（固定 UUID 便于解除） ====================

    /** 速度 ×5 */
    private static final UUID SPEED_UUID = UUID.fromString("d5e6f7a8-1b2c-4d3e-8f9a-0b1c2d3e4f50");
    /** 跳跃 ×3 */
    private static final UUID JUMP_UUID = UUID.fromString("d5e6f7a8-1b2c-4d3e-8f9a-0b1c2d3e4f51");
    /** 攻击 ×10 */
    private static final UUID ATTACK_UUID = UUID.fromString("d5e6f7a8-1b2c-4d3e-8f9a-0b1c2d3e4f52");

    private static final AttributeModifier SPEED_MOD = new AttributeModifier(
            SPEED_UUID, "heavenly_restriction_speed", 4.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier JUMP_MOD = new AttributeModifier(
            JUMP_UUID, "heavenly_restriction_jump", 2.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier ATTACK_MOD = new AttributeModifier(
            ATTACK_UUID, "heavenly_restriction_attack", 9.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    // ==================== 状态查询 ====================

    /** 是否处于天与咒缚（服务端持久数据；客户端数据为空恒为 false，佩戴校验以服务端为准） */
    public static boolean isRestricted(Player player) {
        return player != null && player.getPersistentData().getBoolean(KEY_RESTRICTED);
    }

    // ==================== 赋予 / 解除 ====================

    /** 仪式一完成：赋予天与咒缚（失去咒力 + 肉体强化） */
    public static void applyRestriction(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_RESTRICTED, true);
        // 失去咒力：咒力清零、无限窗口作废
        CursePowerHelper.setCurse(player, 0.0);
        player.getPersistentData().putLong(CursePowerHelper.KEY_INFINITE_UNTIL, 0L);
        // 强制摘下仍佩戴的咒力核心（若仪式前还戴着）
        unequipCurseCore(player);
        // 肉体强化
        applyMods(player);
        TinkersNewlife.LOGGER.info("[天与咒缚] 玩家 {} 获得天与咒缚（失去咒力；速度×5 跳跃×3 攻击×10）",
                player.getName().getString());
    }

    /** 仪式二（解除）：移除状态与属性修饰符 */
    public static void removeRestriction(ServerPlayer player) {
        player.getPersistentData().putBoolean(KEY_RESTRICTED, false);
        removeMods(player);
        TinkersNewlife.LOGGER.info("[天与咒缚] 玩家 {} 的天与咒缚已解除", player.getName().getString());
    }

    // ==================== 属性修饰符 ====================

    private static void applyMods(ServerPlayer player) {
        addMod(player, Attributes.MOVEMENT_SPEED, SPEED_MOD);
        addMod(player, Attributes.JUMP_STRENGTH, JUMP_MOD);
        addMod(player, Attributes.ATTACK_DAMAGE, ATTACK_MOD);
    }

    private static void addMod(ServerPlayer player, Attribute attribute, AttributeModifier modifier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(modifier)) {
            instance.addPermanentModifier(modifier);
        }
    }

    private static void removeMods(ServerPlayer player) {
        removeMod(player, Attributes.MOVEMENT_SPEED, SPEED_UUID);
        removeMod(player, Attributes.JUMP_STRENGTH, JUMP_UUID);
        removeMod(player, Attributes.ATTACK_DAMAGE, ATTACK_UUID);
    }

    private static void removeMod(ServerPlayer player, Attribute attribute, UUID uuid) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
        }
    }

    // ==================== 咒力核心摘除 ====================

    /** 摘除 curse_core 槽位里的所有饰品（含时装层），放回背包，背包满则掉落在脚边 */
    private static void unequipCurseCore(ServerPlayer player) {
        var inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) return;
        Optional<ICurioStacksHandler> opt = inventory.get().getStacksHandler(CURSE_CORE_SLOT);
        if (opt.isEmpty()) return;
        ICurioStacksHandler handler = opt.get();
        IDynamicStackHandler stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            ItemStack stack = stacks.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack out = stacks.extractItem(i, stack.getCount(), false);
            if (!out.isEmpty()) {
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
            }
        }
        IDynamicStackHandler cosmetic = handler.getCosmeticStacks();
        for (int i = 0; i < cosmetic.getSlots(); i++) {
            ItemStack stack = cosmetic.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            ItemStack out = cosmetic.extractItem(i, stack.getCount(), false);
            if (!out.isEmpty()) {
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
            }
        }
    }

    // ==================== 自检维护（每 5 秒） ====================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % 100 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isRestricted(player)) continue;
            // 防残留：咒力核心一律摘下、咒力恒为 0
            unequipCurseCore(player);
            if (CursePowerHelper.getCurse(player) > 0 || CursePowerHelper.isCurseInfinite(player)) {
                CursePowerHelper.setCurse(player, 0.0);
                player.getPersistentData().putLong(CursePowerHelper.KEY_INFINITE_UNTIL, 0L);
            }
            // 属性修饰符若被意外移除则补挂
            applyMods(player);
        }
    }
}
