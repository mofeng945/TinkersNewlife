package com.mofengbaizhi.tinkersnewlife.content.curse.binding;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.client.ClientCurseData;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.Optional;
import java.util.UUID;

/**
 * 天与咒缚·束缚状态统一管理（服务端权威 + 客户端同步读数）
 * <p>
 * 两种束缚（暴君 TYRANT / 咒力 CURSE）共用一套状态机：
 * <ul>
 *   <li>暴君：失去咒力（无法佩戴咒力核心、黑闪概率锁 0、咒力归零），肉体强化
 *       生命上限 ×5 / 速度 ×5 / 跳跃 ×3 / 攻击 ×10</li>
 *   <li>咒力：肉体削弱 生命上限 / 速度 / 伤害 ×0.5，自带咒力亲和 +200，
 *       佩戴咒力核心时总量/输出各 +1</li>
 * </ul>
 * 两者互斥（举行任一仪式后两种仪式均无法再次举行），状态存玩家持久数据（死亡/重登不解除），
 * 属性用永久修饰符 + 每 5 秒自检补挂；客户端读数走 {@link ClientCurseData} 同步值。
 * 清除指令：{@code /tinkersnewlife unbind [玩家]}。
 * <p>
 * 数据兼容：旧版本分别存 {@code tinkersnewlife.heavenly_restriction}（暴君）与
 * {@code tinkersnewlife.curse_binding}（咒力）两个布尔键；现在统一为单一字符串键
 * {@code tinkersnewlife.binding}（"tyrant"/"curse"），读取时自动迁移旧键。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BindingStateHandler {

    private BindingStateHandler() {}

    /** 玩家持久数据：当前束缚类型（"tyrant" / "curse" / 缺省=无） */
    public static final String KEY_BINDING = "tinkersnewlife.binding";
    /** 旧版暴君布尔键（迁移用） */
    public static final String LEGACY_KEY_RESTRICTED = "tinkersnewlife.heavenly_restriction";
    /** 旧版咒力布尔键（迁移用） */
    public static final String LEGACY_KEY_CURSE_BINDING = "tinkersnewlife.curse_binding";

    private static final String TYPE_TYRANT = "tyrant";
    private static final String TYPE_CURSE = "curse";

    /** 被封印的饰品槽（暴君失去佩戴咒力核心的能力） */
    private static final String CURSE_CORE_SLOT = "curse_core";

    // ==================== 属性修饰符（固定 UUID 便于解除） ====================

    // ---- 暴君：×5 / ×5 / ×3 / ×10 ----
    private static final UUID T_HEALTH_UUID = UUID.fromString("d5e6f7a8-1b2c-4d3e-8f9a-0b1c2d3e4f53");
    private static final UUID T_SPEED_UUID = UUID.fromString("d5e6f7a8-1b2c-4d3e-8f9a-0b1c2d3e4f50");
    private static final UUID T_JUMP_UUID = UUID.fromString("d5e6f7a8-1b2c-4d3e-8f9a-0b1c2d3e4f51");
    private static final UUID T_ATTACK_UUID = UUID.fromString("d5e6f7a8-1b2c-4d3e-8f9a-0b1c2d3e4f52");

    private static final AttributeModifier T_HEALTH_MOD = new AttributeModifier(
            T_HEALTH_UUID, "heavenly_restriction_health", 4.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier T_SPEED_MOD = new AttributeModifier(
            T_SPEED_UUID, "heavenly_restriction_speed", 4.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier T_JUMP_MOD = new AttributeModifier(
            T_JUMP_UUID, "heavenly_restriction_jump", 2.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier T_ATTACK_MOD = new AttributeModifier(
            T_ATTACK_UUID, "heavenly_restriction_attack", 9.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    // ---- 咒力：×0.5 / ×0.5 / ×0.5 ----
    private static final UUID C_HEALTH_UUID = UUID.fromString("e6f7a8b9-1c2d-4e3f-8a9b-0c1d2e3f4050");
    private static final UUID C_SPEED_UUID = UUID.fromString("e6f7a8b9-1c2d-4e3f-8a9b-0c1d2e3f4051");
    private static final UUID C_ATTACK_UUID = UUID.fromString("e6f7a8b9-1c2d-4e3f-8a9b-0c1d2e3f4052");

    private static final AttributeModifier C_HEALTH_MOD = new AttributeModifier(
            C_HEALTH_UUID, "curse_binding_health", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier C_SPEED_MOD = new AttributeModifier(
            C_SPEED_UUID, "curse_binding_speed", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier C_ATTACK_MOD = new AttributeModifier(
            C_ATTACK_UUID, "curse_binding_attack", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /** 咒力束缚自带咒力亲和 */
    public static final int AFFINITY_BONUS = 200;
    /** 咒力束缚佩戴核心时的总量/输出等级加成 */
    public static final int CORE_LEVEL_BONUS = 1;

    private enum Binding { NONE, TYRANT, CURSE }

    // ==================== 状态读取（服务端持久数据 + 旧档迁移） ====================

    /** 服务端权威读取（含旧键迁移）；客户端请走 isRestricted/isBound 的同步分支 */
    private static Binding readServerType(Player player) {
        if (player == null || player.level().isClientSide) return Binding.NONE;
        var data = player.getPersistentData();
        String type = data.getString(KEY_BINDING);
        if (!type.isEmpty()) {
            return TYPE_TYRANT.equals(type) ? Binding.TYRANT : Binding.CURSE;
        }
        // 旧档迁移：读旧布尔键并统一写入新键
        Binding migrated = Binding.NONE;
        if (data.getBoolean(LEGACY_KEY_RESTRICTED)) {
            migrated = Binding.TYRANT;
        } else if (data.getBoolean(LEGACY_KEY_CURSE_BINDING)) {
            migrated = Binding.CURSE;
        }
        if (migrated != Binding.NONE) {
            data.putString(KEY_BINDING, migrated == Binding.TYRANT ? TYPE_TYRANT : TYPE_CURSE);
            data.remove(LEGACY_KEY_RESTRICTED);
            data.remove(LEGACY_KEY_CURSE_BINDING);
        }
        return migrated;
    }

    /** 服务端写入束缚类型（同时清掉旧布尔键） */
    private static void writeServerType(ServerPlayer player, Binding binding) {
        var data = player.getPersistentData();
        if (binding == Binding.NONE) {
            data.remove(KEY_BINDING);
        } else {
            data.putString(KEY_BINDING, binding == Binding.TYRANT ? TYPE_TYRANT : TYPE_CURSE);
        }
        data.remove(LEGACY_KEY_RESTRICTED);
        data.remove(LEGACY_KEY_CURSE_BINDING);
    }

    /** 客户端上是否为本地玩家（服务端恒 false） */
    private static boolean isLocalClientPlayer(Player player) {
        try {
            return net.minecraft.client.Minecraft.getInstance().player == player;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 对外查询（保持旧 API 名） ====================

    /** 暴君束缚（天与咒缚·暴君）：服务端读持久数据，客户端读同步值 */
    public static boolean isRestricted(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) {
            return isLocalClientPlayer(player) && ClientCurseData.isRestricted();
        }
        return readServerType(player) == Binding.TYRANT;
    }

    /** 咒力束缚（天与咒缚·咒力）：服务端读持久数据，客户端读同步值 */
    public static boolean isBound(Player player) {
        if (player == null) return false;
        if (player.level().isClientSide) {
            return isLocalClientPlayer(player) && ClientCurseData.isBound();
        }
        return readServerType(player) == Binding.CURSE;
    }

    /** 是否持有任一束缚标识（暴君 / 咒力）——两种仪式互斥共用的仪式检测 */
    public static boolean hasAnyBinding(Player player) {
        return isRestricted(player) || isBound(player);
    }

    /** 咒力束缚自带咒力亲和加成 */
    public static int getAffinityBonus(Player player) {
        return isBound(player) ? AFFINITY_BONUS : 0;
    }

    /** 咒力束缚佩戴核心时的总量/输出等级加成 */
    public static int getCoreLevelBonus(Player player) {
        return isBound(player) ? CORE_LEVEL_BONUS : 0;
    }

    // ==================== 赋予 / 解除（保持旧 API 名） ====================

    /** 仪式一完成：赋予暴君束缚（失去咒力 + 肉体强化） */
    public static void applyRestriction(ServerPlayer player) {
        removeCurseSideEffects(player);
        writeServerType(player, Binding.TYRANT);
        // 失去咒力：咒力清零、无限窗口作废
        CursePowerHelper.setCurse(player, 0.0);
        player.getPersistentData().putLong(CursePowerHelper.KEY_INFINITE_UNTIL, 0L);
        // 强制摘下仍佩戴的咒力核心
        unequipCurseCore(player);
        // 肉体强化
        applyMods(player, Binding.TYRANT);
        com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler.syncToClient(player);
        TinkersNewlife.LOGGER.info("[天与咒缚·暴君] 玩家 {} 获得暴君束缚（失去咒力；生命上限×5 速度×5 跳跃×3 攻击×10）",
                player.getName().getString());
    }

    /** 仪式二完成：赋予咒力束缚（肉体削弱 + 咒力亲和/等级加成） */
    public static void applyBinding(ServerPlayer player) {
        removeTyrantSideEffects(player);
        writeServerType(player, Binding.CURSE);
        applyMods(player, Binding.CURSE);
        com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler.syncToClient(player);
        TinkersNewlife.LOGGER.info("[天与咒缚·咒力] 玩家 {} 获得咒力束缚（生命/速度/伤害×0.5；咒力亲和+200；核心总量/输出+1）",
                player.getName().getString());
    }

    /** 解除暴君束缚（仅清除暴君一侧；与咒力互斥，正常不会同时存在） */
    public static void removeRestriction(ServerPlayer player) {
        if (readServerType(player) == Binding.TYRANT) {
            writeServerType(player, Binding.NONE);
            removeMods(player, Binding.TYRANT);
            com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler.syncToClient(player);
            TinkersNewlife.LOGGER.info("[天与咒缚·暴君] 玩家 {} 的暴君束缚已解除", player.getName().getString());
        }
    }

    /** 解除咒力束缚 */
    public static void removeBinding(ServerPlayer player) {
        if (readServerType(player) == Binding.CURSE) {
            writeServerType(player, Binding.NONE);
            removeMods(player, Binding.CURSE);
            com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler.syncToClient(player);
            TinkersNewlife.LOGGER.info("[天与咒缚·咒力] 玩家 {} 的咒力束缚已解除", player.getName().getString());
        }
    }

    /** 防御：转暴君前清掉咒力侧副作用（正常不会同时存在） */
    private static void removeCurseSideEffects(ServerPlayer player) {
        if (readServerType(player) == Binding.CURSE) {
            removeMods(player, Binding.CURSE);
        }
    }

    /** 防御：转咒力前清掉暴君侧副作用 */
    private static void removeTyrantSideEffects(ServerPlayer player) {
        if (readServerType(player) == Binding.TYRANT) {
            removeMods(player, Binding.TYRANT);
            unequipCurseCore(player);
        }
    }

    /** 清除玩家身上的任一束缚标识并移除对应属性修饰符（/tinkersnewlife unbind） */
    public static boolean clearAnyBinding(ServerPlayer player) {
        Binding type = readServerType(player);
        if (type == Binding.NONE) return false;
        writeServerType(player, Binding.NONE);
        removeMods(player, type);
        com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler.syncToClient(player);
        TinkersNewlife.LOGGER.info("[天与咒缚] 玩家 {} 的束缚已清除（{}）", player.getName().getString(), type.name());
        return true;
    }

    // ==================== 属性修饰符 ====================

    private static void applyMods(ServerPlayer player, Binding binding) {
        if (binding == Binding.TYRANT) {
            addMod(player, Attributes.MAX_HEALTH, T_HEALTH_MOD);
            addMod(player, Attributes.MOVEMENT_SPEED, T_SPEED_MOD);
            addMod(player, Attributes.JUMP_STRENGTH, T_JUMP_MOD);
            addMod(player, Attributes.ATTACK_DAMAGE, T_ATTACK_MOD);
        } else if (binding == Binding.CURSE) {
            addMod(player, Attributes.MAX_HEALTH, C_HEALTH_MOD);
            addMod(player, Attributes.MOVEMENT_SPEED, C_SPEED_MOD);
            addMod(player, Attributes.ATTACK_DAMAGE, C_ATTACK_MOD);
        }
    }

    private static void removeMods(ServerPlayer player, Binding binding) {
        if (binding == Binding.TYRANT) {
            removeMod(player, Attributes.MAX_HEALTH, T_HEALTH_UUID);
            removeMod(player, Attributes.MOVEMENT_SPEED, T_SPEED_UUID);
            removeMod(player, Attributes.JUMP_STRENGTH, T_JUMP_UUID);
            removeMod(player, Attributes.ATTACK_DAMAGE, T_ATTACK_UUID);
        } else if (binding == Binding.CURSE) {
            removeMod(player, Attributes.MAX_HEALTH, C_HEALTH_UUID);
            removeMod(player, Attributes.MOVEMENT_SPEED, C_SPEED_UUID);
            removeMod(player, Attributes.ATTACK_DAMAGE, C_ATTACK_UUID);
        }
    }

    private static void addMod(ServerPlayer player, Attribute attribute, AttributeModifier modifier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(modifier)) {
            instance.addPermanentModifier(modifier);
        }
    }

    private static void removeMod(ServerPlayer player, Attribute attribute, UUID uuid) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(uuid);
        }
    }

    // ==================== 咒力核心摘除（暴君） ====================

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
            Binding type = readServerType(player);
            if (type == Binding.NONE) continue;
            if (type == Binding.TYRANT) {
                // 暴君防残留：咒力核心一律摘下、咒力恒为 0
                unequipCurseCore(player);
                if (CursePowerHelper.getCurse(player) > 0 || CursePowerHelper.isCurseInfinite(player)) {
                    CursePowerHelper.setCurse(player, 0.0);
                    player.getPersistentData().putLong(CursePowerHelper.KEY_INFINITE_UNTIL, 0L);
                }
            }
            // 属性修饰符若被意外移除则补挂
            applyMods(player, type);
        }
    }

    // ==================== 死亡 / 重生：固化状态 ====================

    /** 死亡重生（Clone）时把束缚类型与对应属性修饰符转移到新实体——死亡也绝不解除 */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Binding type = readServerType(event.getOriginal());
        if (type == Binding.NONE) return;
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        writeServerType(newPlayer, type);
        applyMods(newPlayer, type);
        TinkersNewlife.LOGGER.info("[天与咒缚] 玩家 {} 死亡重生，束缚固化保留（{}）",
                newPlayer.getName().getString(), type.name());
    }

    // ==================== 指令：/tinkersnewlife unbind [玩家] ====================

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tinkersnewlife")
                .then(Commands.literal("unbind")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
                                ctx.getSource().sendFailure(Component.literal("[天与咒缚] 请在游戏内执行，或指定玩家：/tinkersnewlife unbind <玩家>"));
                                return 0;
                            }
                            return unbindBinding(ctx.getSource(), player);
                        })
                        .then(Commands.argument("target", EntityArgument.players())
                                .executes(ctx -> {
                                    int cleared = 0;
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "target")) {
                                        cleared += unbindBinding(ctx.getSource(), player);
                                    }
                                    return cleared;
                                }))));
    }

    private static int unbindBinding(CommandSourceStack source, ServerPlayer player) {
        if (!clearAnyBinding(player)) {
            source.sendSuccess(() -> Component.literal("[天与咒缚] " + player.getName().getString()
                    + " 当前没有束缚标识（暴君 / 咒力）"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("[天与咒缚] 已清除 " + player.getName().getString()
                + " 的束缚标识，体质与属性已恢复到仪式前水准"), false);
        return 1;
    }
}
