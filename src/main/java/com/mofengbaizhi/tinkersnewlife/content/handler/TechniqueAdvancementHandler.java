package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 帕秋莉手册章节解锁：
 * 玩家佩戴的咒力核心上出现某个术式 / 领域特性时，授予对应的 advancement
 * （advancement 用 impossible 触发器，只能由这里手动授予），
 * 手册中带 advancement 字段的词条会随获得进度逐个解锁。
 */
public final class TechniqueAdvancementHandler {

    /** 特性 modifier id → advancement 路径（data/tinkersnewlife/advancements 下） */
    private static final Map<ModifierId, ResourceLocation> MODIFIER_ADVANCEMENTS = new HashMap<>();

    static {
        // 术式
        put(Modifiers.KAI.getId(), "techniques/kai");
        put(Modifiers.BA.getId(), "techniques/ba");
        put(Modifiers.ZAO_KAI.getId(), "techniques/zao_kai");
        put(Modifiers.BLOOD_MANIPULATION.getId(), "techniques/blood_manipulation");
        put(Modifiers.BLOOD_MANIPULATION_HYAKUREN.getId(), "techniques/blood_manipulation_hyakuren");
        put(Modifiers.BLOOD_MANIPULATION_SUPERNOVA.getId(), "techniques/blood_manipulation_supernova");
        put(Modifiers.TEN_SHADOWS.getId(), "techniques/ten_shadows");
        put(Modifiers.BLACK_BIRD.getId(), "techniques/black_bird");
        put(Modifiers.PROJECTION.getId(), "techniques/projection");
        put(Modifiers.WULIANG_WUXIAN.getId(), "techniques/wuliang_wuxian");
        put(Modifiers.WULIANG_CANG.getId(), "techniques/wuliang_cang");
        put(Modifiers.JACOBS_LADDER.getId(), "techniques/jacobs_ladder");
        put(Modifiers.REVERSE_CURSED.getId(), "techniques/reverse_cursed");
        // 领域
        put(Modifiers.ZUOSHA_BOTU.getId(), "domains/zuosha_botu");
        put(Modifiers.WULIANG_KONGCHU.getId(), "domains/wuliang_kongchu");
        put(Modifiers.FUMO_YUCHUZI.getId(), "domains/fumo_yuchuzi");
    }

    private TechniqueAdvancementHandler() {}

    private static void put(ModifierId id, String path) {
        MODIFIER_ADVANCEMENTS.put(id, new ResourceLocation(TinkersNewlife.MOD_ID, path));
    }

    /**
     * 扫描玩家佩戴核心上的特性，发现已获得但未解锁的术式 / 领域即授予对应 advancement。
     * 每 20 tick 调用一次即可；玩家登入后首次扫描会立即补齐已持有的特性。
     */
    public static void scanAndUnlock(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return;
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return;
        ServerAdvancementManager advancements = player.server.getAdvancements();
        PlayerAdvancements playerAdvancements = player.getAdvancements();
        for (ModifierEntry entry : tool.getModifierList()) {
            ResourceLocation adv = MODIFIER_ADVANCEMENTS.get(entry.getId());
            if (adv == null) continue;
            Advancement holder = advancements.getAdvancement(adv);
            if (holder != null && !playerAdvancements.getOrStartProgress(holder).isDone()) {
                playerAdvancements.award(holder, "unlock");
            }
        }
    }
}
