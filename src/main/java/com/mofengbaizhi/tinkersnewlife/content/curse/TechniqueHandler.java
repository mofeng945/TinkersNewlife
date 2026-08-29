package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式注册表（服务端）
 * <p>
 * 管理所有已实现术式：术式修饰符 → 术式实例（后续新术式继承 {@link BaseTechnique} 后在此登记）。
 * 释放按键触发时扫描佩戴咒力核心上的修饰符，命中已注册术式即执行。
 */
public final class TechniqueHandler {

    private static final Map<ModifierId, BaseTechnique> TECHNIQUES = new ConcurrentHashMap<>();

    private TechniqueHandler() {}

    /** 注册术式：修饰符 ID → 术式实例（在 TinkersNewlife 初始化时调用） */
    public static void register(BaseTechnique technique) {
        TECHNIQUES.put(technique.getModifierId(), technique);
    }

    /** 释放按键入口：扫描佩戴咒力核心上的术式并执行 */
    public static void tryUse(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            return;
        }
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return;
        for (ModifierEntry entry : tool.getModifierList()) {
            BaseTechnique technique = TECHNIQUES.get(entry.getId());
            if (technique != null) {
                technique.tryUse(player);
                return;
            }
        }
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_trait"), true);
    }
}
