package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.FlyingSwordFootEntity;
import com.mofengbaizhi.tinkersnewlife.content.item.FlyingSwordItem;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioEquipEvent;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 飞剑·脚部饰品飞行。
 *
 * <h3>为什么必须"严格校验脚部槽位"</h3>
 * Curios 的 {@code findFirstCurio(predicate)} 会<b>无视槽位类型</b>扫描所有饰品槽，
 * 于是"飞剑塞在别的饰品槽里"甚至"使用飞剑时触发的伪装备事件"都会被当成已装备 →
 * 飞行能力被错误开启（玩家没穿在脚上却能飞）。本类因此：
 * <ul>
 *   <li>{@link #hasFlyingSwordInFeet(Player)}：只认 <b>feet</b> 槽位里的飞剑；</li>
 *   <li>装备时<b>只授予 mayfly（飞行权限），不强制 flying=true</b>——是否起飞交给玩家
 *       （双击空格），避免"一装备就悬空"；</li>
 *   <li>每 tick 校验：不在脚部 → 立即撤销飞行并还原玩家原本的 mayfly 权限。</li>
 * </ul>
 *
 * <h3>⭐ 授予/快照状态**只放服务器内存**，不写玩家持久数据</h3>
 * 旧做法把 {@code flying_sword_active} 与 {@code flying_sword_prev_mayfly} 写进
 * {@code player.getPersistentData()} ✗，于是：
 * <ul>
 *   <li><b>快照会跨会话带过来</b>：旧版本曾在"玩家已经能飞"时写快照（创造模式装备、
 *       TCon 更新 NBT 触发的伪装备事件…）⇒ 存进去的 `true` 是**污染值** ✗；
 *       摘下飞剑时"还原"成 true ⇒ <b>摘了还能飞</b> ✗（用户实测）；</li>
 *   <li><b>标记跨会话保留</b>：重登后 {@code mayfly} 已被服务端按游戏模式重置，
 *       而标记还在 → 旧逻辑以为"已生效"从而从不补权限 ✗（"每次进服务器/切模式后默认不生效" ✗）。</li>
 * </ul>
 * 现在这两样都放进 {@link #GRANTED} / {@link #PREV_MAYFLY}（**服务器本次运行内存** ✓）：
 * 跨会话必然为空 → 重新按"登录时服务端刚设好的游戏模式默认值"取快照 ✓，
 * 所以**永远还原到正确的值** ✓；登录时还会把旧版遗留的持久键一并删掉 ✓（老存档一次性修好 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FlyingSwordCuriosHandler {

    private static int tickCounter = 0;
    /** 飞剑所在的脚部饰品槽标识 */
    private static final String FEET_SLOT = "feet";

    /** 旧版本写进**持久数据**的键（遗留：登录时清理 ✓） */
    private static final String LEGACY_ACTIVE = "flying_sword_active";
    private static final String LEGACY_PREV_MAYFLY = "flying_sword_prev_mayfly";

    /** 本会话内"由飞剑授予过飞行"的玩家（不写持久数据 ✓，见类注释） */
    private static final Set<UUID> GRANTED = ConcurrentHashMap.newKeySet();
    /** 本会话内记下的"授予前玩家能不能飞"快照（只在第一次授予时写入，绝不覆盖 ✓） */
    private static final Map<UUID, Boolean> PREV_MAYFLY = new ConcurrentHashMap<>();

    private static boolean isFlyingSwordBroken(ItemStack stack) {
        if (stack.isEmpty()) return true;
        // ✅ 使用 ToolHelper 安全获取，避免 "non-modifiable tool" 警告
        ToolStack tool = ToolHelper.getToolStack(stack);
        return tool == null || tool.isBroken();
    }

    /**
     * 飞剑是否<b>确实装备在脚部饰品位</b>（严格版）。
     * 不使用 {@code findFirstCurio}——它会无视槽位类型，是"飞行被错误开启"的根源。
     */
    public static boolean hasFlyingSwordInFeet(Player player) {
        try {
            var curios = CuriosApi.getCuriosInventory(player).resolve();
            if (curios.isEmpty()) return false;
            var handler = curios.get().getStacksHandler(FEET_SLOT);
            if (handler.isEmpty()) return false;
            var stacks = handler.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (stacks.getStackInSlot(i).getItem() instanceof FlyingSwordItem) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 脚部槽位里的那把飞剑（没有则 EMPTY） */
    private static ItemStack getFeetSword(Player player) {
        try {
            var curios = CuriosApi.getCuriosInventory(player).resolve();
            if (curios.isEmpty()) return ItemStack.EMPTY;
            var handler = curios.get().getStacksHandler(FEET_SLOT);
            if (handler.isEmpty()) return ItemStack.EMPTY;
            var stacks = handler.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (stack.getItem() instanceof FlyingSwordItem) return stack;
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    /** 脚部槽里那把飞剑（客户端渲染层也要用，见 FlyingSwordFootRenderHandler） */
    public static ItemStack feetSwordOf(Player player) {
        return getFeetSword(player);
    }

    // ============================================================
    //  授予 / 撤销（幂等，会话内记忆）
    // ============================================================

    /** 授予飞行权限：只在"本会话第一次由飞剑授予"时记快照 ✓（绝不覆盖 ✓） */
    private static void grantFlight(Player player) {
        if (player.isCreative() || player.isSpectator()) return;   // 本来就会飞：不记快照也不授予 ✓
        if (!GRANTED.add(player.getUUID())) return;                // 已经授予过 → 保持原快照 ✓
        PREV_MAYFLY.put(player.getUUID(), player.getAbilities().mayfly);
        if (!player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            TinkersNewlife.LOGGER.info("[飞剑] 授予飞行能力（脚部饰品）：玩家={}", player.getName().getString());
        }
    }

    /** 撤销飞剑授予的飞行：还原本会话记下的快照 ✓（没授予过则什么都不动 ✓） */
    private static void revokeFlight(Player player) {
        if (!GRANTED.remove(player.getUUID())) return;
        Boolean prev = PREV_MAYFLY.remove(player.getUUID());
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = prev != null && prev;   // 快照缺失时保守收回 ✓
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            TinkersNewlife.LOGGER.info("[飞剑] 撤销飞行能力：玩家={}", player.getName().getString());
        }
    }

    // ============================================================
    //  事件
    // ============================================================

    @SubscribeEvent
    public static void onCurioEquip(CurioEquipEvent event) {
        ItemStack stack = event.getStack();
        if (!(stack.getItem() instanceof FlyingSwordItem)) return;
        if (!FEET_SLOT.equals(event.getSlotContext().identifier())) return;   // 只认脚部槽

        Player player = (Player) event.getEntity();
        if (player.level().isClientSide) return;

        // 右键发射飞剑时 TCon 会更新工具 NBT，可能连带触发一次伪装备事件 → 忽略
        UUID emittingId = FlyingSwordItem.EMITTING_PLAYER.get();
        if (emittingId != null && emittingId.equals(player.getUUID())) {
            return;
        }
        // 损坏的飞剑不授予飞行
        if (isFlyingSwordBroken(stack)) return;

        // ⭐ 只授予"飞行权限"，不强制起飞（原来直接 flying=true 会导致一装备就悬空）
        grantFlight(player);
    }

    @SubscribeEvent
    public static void onCurioUnequip(CurioUnequipEvent event) {
        ItemStack stack = event.getStack();
        if (!(stack.getItem() instanceof FlyingSwordItem)) return;
        if (!FEET_SLOT.equals(event.getSlotContext().identifier())) return;

        Player player = (Player) event.getEntity();
        if (!player.level().isClientSide) {
            clearFlyingState(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // 死亡复活后先清空遗留的飞行状态（避免 curios 重放 equip 造成状态错乱）
        clearFlyingState(player);

        // 若脚部仍装备着未损坏的飞剑，重新授予飞行权限（不强制起飞 ✓）
        ItemStack stack = getFeetSword(player);
        if (stack.isEmpty() || isFlyingSwordBroken(stack)) return;
        grantFlight(player);
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    /**
     * 登录：清掉旧版遗留的持久键与会话表条目 + 残留的脚部实体。
     * <p>旧版把 {@code flying_sword_active}/{@code flying_sword_prev_mayfly} 写进持久数据，
     * 其中快照可能是被污染的 `true` ✗ —— 一律删除 ✓（{@code mayfly} 本身不是持久数据：
     * 登录时服务端已按游戏模式重新设过 ✓，所以删掉键就干净了 ✓）；
     * 之后的授予由每 tick 的自愈逻辑用**正确的新快照**重新完成 ✓。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        var data = player.getPersistentData();
        data.remove(LEGACY_ACTIVE);
        data.remove(LEGACY_PREV_MAYFLY);
        GRANTED.remove(player.getUUID());
        PREV_MAYFLY.remove(player.getUUID());
        // 脚部飞剑的渲染已改为客户端直接画（见 FlyingSwordFootRenderHandler），
        // 这里清掉旧存档可能残留的实体，免得和新渲染叠成两把 ✓
        removeFootEntity(player);
    }

    /** 登出：丢掉会话表条目（下次登录重新按当次状态取快照 ✓） */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        GRANTED.remove(event.getEntity().getUUID());
        PREV_MAYFLY.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        if (player.isCreative()) return; // 创造性玩家不干预

        // ⭐ 严格判定：只有脚部饰品槽里的飞剑才算"装备中"（手持/其他饰品槽都不算）
        ItemStack feetSword = getFeetSword(player);
        boolean usable = !feetSword.isEmpty() && !isFlyingSwordBroken(feetSword);

        if (!usable) {
            // ⭐ 不在脚部槽就**必须撤销** —— 不能只看某个"标记"：
            //   旧版持久数据里的污染快照会让一次"还原"把 mayfly 变回 true ✗（用户实测：摘了还能飞）。
            //   revokeFlight 幂等：不是飞剑授予的（例如别的模组给的飞行）就什么都不动 ✓。
            revokeFlight(player);
            return;
        }

        // 装了 → 保证"权限"到位：重登 / 切游戏模式 / 被别的模组清掉，服务端都会按游戏模式
        // 重置 mayfly ✗ —— 旧代码从不补权限，于是"每次进服务器或切模式后默认不生效" ✗ → 已修 ✓。
        grantFlight(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;
        if (tickCounter % 100 != 0) return;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!player.isAlive() || !player.getAbilities().flying) continue;
            // ⭐ 只有脚部饰品槽的飞剑才消耗耐久（手持的不消耗）
            ItemStack stack = getFeetSword(player);
            if (stack.isEmpty()) continue;
            if (isFlyingSwordBroken(stack)) {
                clearFlyingState(player);
                continue;
            }

            // ✅ 走匠魂正常耐久逻辑：受粘液覆层（slime covering）等 onDamageTool 钩子减免
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool != null) {
                slimeknights.tconstruct.library.tools.helper.ToolDamageUtil.damage(tool, 1, player, stack);
            }
        }
    }

    // ===== 摔落伤害逻辑修正 =====
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        // 只在飞行状态下取消摔伤
        if (player.getAbilities().flying) {
            event.setCanceled(true);
        }
        // 关闭飞行后，正常计算摔伤，由原版处理
    }

    // ===== 辅助方法 =====

    /** 清除飞剑带来的飞行状态（撤销权限 + 移除残留的脚部实体 ✓） */
    private static void clearFlyingState(Player player) {
        revokeFlight(player);
        removeFootEntity(player);
    }

    private static void removeFootEntity(Player player) {
        if (player.level().isClientSide) return;
        player.level().getEntitiesOfClass(FlyingSwordFootEntity.class, player.getBoundingBox().inflate(3))
                .stream()
                .filter(e -> e.getOwnerUUID() != null && e.getOwnerUUID().equals(player.getUUID()))
                .findFirst()
                .ifPresent(Entity::discard);
    }
}
