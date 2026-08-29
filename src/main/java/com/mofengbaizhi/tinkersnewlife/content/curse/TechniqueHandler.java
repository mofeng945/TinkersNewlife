package com.mofengbaizhi.tinkersnewlife.content.curse;

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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式注册表（服务端）
 * <p>
 * 管理所有已实现术式：术式修饰符 → 术式实例（后续新术式继承 {@link BaseTechnique} 后在此登记）。
 * 释放按键触发时扫描佩戴咒力核心上的修饰符，按下分发 {@code onKeyPress}（即时术式直接释放、
 * 蓄力术式开始蓄力），松开分发 {@code onKeyRelease}（蓄力术式向当前朝向发射）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TechniqueHandler {

    private static final Map<ModifierId, BaseTechnique> TECHNIQUES = new ConcurrentHashMap<>();

    private TechniqueHandler() {}

    /** 注册术式：修饰符 ID → 术式实例（在 TinkersNewlife 初始化时调用） */
    public static void register(BaseTechnique technique) {
        TECHNIQUES.put(technique.getModifierId(), technique);
    }

    /** 按键按下：熔断检查 → 找到术式 → 按下行为（即时释放 / 开始蓄力） */
    public static void onKeyPress(ServerPlayer player) {
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        BaseTechnique technique = findEquipped(player);
        if (technique != null) {
            technique.onKeyPress(player);
        }
    }

    /** 按键松开：找到术式 → 松开行为（蓄力术式发射） */
    public static void onKeyRelease(ServerPlayer player) {
        BaseTechnique technique = findEquipped(player);
        if (technique != null) {
            technique.onKeyRelease(player);
        }
    }

    /** 扫描佩戴咒力核心，返回第一个已注册术式（无核心/无术式时提示并返回 null） */
    private static BaseTechnique findEquipped(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            return null;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return null;
        for (ModifierEntry entry : tool.getModifierList()) {
            BaseTechnique technique = TECHNIQUES.get(entry.getId());
            if (technique != null) {
                return technique;
            }
        }
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_trait"), true);
        return null;
    }

    /** 玩家登出：取消进行中的蓄力 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ZaoKaiTechnique.cancelCharge(sp);
        }
    }

    /** 玩家死亡：取消进行中的蓄力 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ZaoKaiTechnique.cancelCharge(sp);
        }
    }
}
