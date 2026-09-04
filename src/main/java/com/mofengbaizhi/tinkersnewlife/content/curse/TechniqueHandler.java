package com.mofengbaizhi.tinkersnewlife.content.curse;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ZaoKaiTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式注册表（服务端）
 * <p>
 * 管理所有已实现术式：术式修饰符 → 术式实例（后续新术式继承 {@link BaseTechnique} 后在此登记）。
 * <p>
 * 多术式支持：每个玩家记住当前选中的术式（{@link #SELECTED}），切换按键按核心修饰符顺序循环，
 * 释放按键只释放当前选中的术式；当前术式随 {@code PacketSyncCurse} 同步到客户端 HUD 显示。
 * 未选中或选中的术式已不在核心上时，自动回退到第一个术式。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TechniqueHandler {

    private static final Map<ModifierId, BaseTechnique> TECHNIQUES = new ConcurrentHashMap<>();
    /** 每个玩家当前选中的术式 id（按核心修饰符列表顺序循环） */
    private static final Map<UUID, ModifierId> SELECTED = new ConcurrentHashMap<>();

    private TechniqueHandler() {}

    /** 注册术式：修饰符 ID → 术式实例（在 TinkersNewlife 初始化时调用） */
    public static void register(BaseTechnique technique) {
        TECHNIQUES.put(technique.getModifierId(), technique);
    }

    /** 全部已注册术式修饰符 id（供剥离/槽位配方等遍历；新增术式自动包含） */
    public static java.util.Set<ModifierId> getAllTechniqueIds() {
        return TECHNIQUES.keySet();
    }

    /** 该修饰符是否为本模组已注册的术式 */
    public static boolean isTechnique(ModifierId id) {
        return TECHNIQUES.containsKey(id);
    }

    /** 按键按下：熔断检查 → 封印检查 → 当前选中的术式 → 按下行为（即时释放 / 开始蓄力） */
    public static void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        if (CursePowerHelper.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.sealed.active",
                    CursePowerHelper.getSealedRemainingSeconds(player)), true);
            return;
        }
        BaseTechnique technique = findSelected(player);
        if (technique != null) {
            technique.onKeyPress(player);
        }
    }

    /** 按键松开：当前选中的术式 → 松开行为（蓄力术式发射） */
    public static void onKeyRelease(ServerPlayer player) {
        BaseTechnique technique = findSelected(player);
        if (technique != null) {
            technique.onKeyRelease(player);
        }
    }

    /** 术式反转按键按下（F）：封印检查 → 当前选中的术式 → 反转行为（如无下限·苍 → 赫） */
    public static void onReverseKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.sealed.active",
                    CursePowerHelper.getSealedRemainingSeconds(player)), true);
            return;
        }
        BaseTechnique technique = findSelected(player);
        if (technique != null) {
            technique.onReverseKeyPress(player);
        }
    }

    /** 术式反转按键松开（F） */
    public static void onReverseKeyRelease(ServerPlayer player) {
        BaseTechnique technique = findSelected(player);
        if (technique != null) {
            technique.onReverseKeyRelease(player);
        }
    }

    /**
     * 切换按键：把当前选中的术式循环到核心上的下一个术式（列表末尾回到第一个）。
     * 切换后提示并立即同步 HUD。
     */
    public static void onSwitch(ServerPlayer player) {
        List<ModifierId> techniques = getTechniquesOnCore(player);
        if (techniques == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            return;
        }
        if (techniques.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_trait"), true);
            return;
        }
        UUID uuid = player.getUUID();
        ModifierId current = SELECTED.get(uuid);
        int index = current != null ? techniques.indexOf(current) : -1;
        ModifierId next = techniques.get((index + 1) % techniques.size());
        // 切换术式时取消草木操术的顺转蓄力（防止切回后残留旧蓄力）
        com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cancelCharge(player);
        SELECTED.put(uuid, next);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.switched",
                getDisplayName(next)), true);
        CursePowerHandler.syncToClient(player);
    }

    /** 静默获取当前应选术式 id（无核心/无术式返回 null）；未选中或选中失效时自动补选第一个 */
    @Nullable
    public static ModifierId getSelectedTechniqueId(ServerPlayer player) {
        List<ModifierId> techniques = getTechniquesOnCore(player);
        if (techniques == null || techniques.isEmpty()) return null;
        UUID uuid = player.getUUID();
        ModifierId selected = SELECTED.get(uuid);
        if (selected == null || !techniques.contains(selected)) {
            selected = techniques.get(0);
            SELECTED.put(uuid, selected);
        }
        return selected;
    }

    /** 取佩戴核心上已注册的术式 id 列表（按修饰符列表顺序）；无核心返回 null */
    @Nullable
    private static List<ModifierId> getTechniquesOnCore(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return null;
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return null;
        List<ModifierId> list = new ArrayList<>();
        for (ModifierEntry entry : tool.getModifierList()) {
            if (TECHNIQUES.containsKey(entry.getId())) {
                list.add(entry.getId());
            }
        }
        return list;
    }

    /** 当前选中的术式实例；无核心/无术式时提示并返回 null */
    private static BaseTechnique findSelected(ServerPlayer player) {
        ModifierId selected = getSelectedTechniqueId(player);
        if (selected == null) {
            ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
            if (core.isEmpty()) {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_trait"), true);
            }
            return null;
        }
        return TECHNIQUES.get(selected);
    }

    /** 术式显示名（modifier.<命名空间>.<路径> 本地化键） */
    private static Component getDisplayName(ModifierId id) {
        return Component.translatable(slimeknights.tconstruct.library.utils.Util.makeTranslationKey("modifier", id));
    }

    /** 玩家登出：取消进行中的蓄力并清空选中记录 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ZaoKaiTechnique.cancelCharge(sp);
            WuliangCangTechnique.cancelCharge(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique.cleanup(sp);
            SELECTED.remove(sp.getUUID());
        }
    }

    /** 玩家死亡：取消进行中的蓄力，正在操控的傀儡立即消散、视角回归 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ZaoKaiTechnique.cancelCharge(sp);
            WuliangCangTechnique.cancelCharge(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique.cleanup(sp);
        }
    }
}
