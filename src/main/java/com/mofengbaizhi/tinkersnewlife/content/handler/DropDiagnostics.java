package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>掉落诊断</b>（临时排查用 ✓）：玩家击杀的怪"没掉东西"或"掉落事件被取消"时打一条 INFO，
 * 把<b>所有可能的抑制来源</b>一次性列清楚 ✓ —— 免得靠猜 ✗。
 *
 * <p>排查目标（用户反馈"原钻合金打怪不掉东西"）：
 * <ul>
 *   <li>掉落件数 / 事件是否被取消；</li>
 *   <li>伤害类型（混沌之流会把一次伤害拆成"1 物理 + N 学派"✓，学派段可能是别 mod 的类型 ✓）；</li>
 *   <li>击杀者主手/副手/护甲上有没有 <b>混沌之流</b>、<b>幸运掉落</b>；</li>
 *   <li>四个已知抑制器是否命中：无为变形体 / 咒灵释放体 / 召唤物 / 狱门疆封印体。</li>
 * </ul>
 *
 * <p>限流：2 秒最多一条 ✓；正式发布前可整类删除 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DropDiagnostics {

    private DropDiagnostics() {
    }

    private static final ModifierId CHAOS_FLOW = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "chaos_flow"));
    private static final ModifierId LUCKY_DROP = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "lucky_drop"));

    private static long lastLog = 0L;

    /** receiveCanceled = true ⇒ 被别的处理器取消掉的事件我们也能看到 ✓（这正是要查的情况 ✓） */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        try {
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) return;

            DamageSource source = event.getSource();
            Player killer = null;
            if (source.getEntity() instanceof Player p) killer = p;
            else if (source.getDirectEntity() instanceof Player p) killer = p;

            // 只在"玩家击杀 + （被取消 或 一件都没掉）"时记录 ✓
            boolean suspicious = event.isCanceled() || event.getDrops().isEmpty();
            if (killer == null || !suspicious) return;

            long now = System.currentTimeMillis();
            if (now - lastLog < 2000L) return;
            lastLog = now;

            ItemStack hand = killer.getMainHandItem();
            TinkersNewlife.LOGGER.info(
                    "[掉落诊断] {} 掉落 {} 件 / 事件被取消={} / 伤害类型={} / 击杀者主手={} / 特性来源：{} / 抑制判定：无为变形={} 释放体={} 召唤物={} 狱门疆={}",
                    entity.getType(),
                    event.getDrops().size(),
                    event.isCanceled(),
                    source.getMsgId(),
                    hand.isEmpty() ? "空手" : hand.getHoverName().getString(),
                    modifierSources(killer),
                    com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.isTransformedUnit(entity),
                    com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique
                            .ownerOfReleased(entity) != null,
                    SummonDropSuppressor.isNoDropSummon(entity),
                    entity.getPersistentData().getBoolean(com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailHandler.KEY_SUPPRESS_LOOT));
        } catch (Throwable ignored) {
            // 诊断本身绝不影响游戏 ✓
        }
    }

    /** 把"混沌之流 / 幸运掉落"这两个关键特性出现在哪个部位列出来（主手 / 副手 / 四件护甲） */
    private static String modifierSources(Player player) {
        StringBuilder sb = new StringBuilder();
        append(sb, "主手", player.getMainHandItem());
        append(sb, "副手", player.getOffhandItem());
        append(sb, "头", player.getItemBySlot(EquipmentSlot.HEAD));
        append(sb, "胸", player.getItemBySlot(EquipmentSlot.CHEST));
        append(sb, "腿", player.getItemBySlot(EquipmentSlot.LEGS));
        append(sb, "脚", player.getItemBySlot(EquipmentSlot.FEET));
        return sb.length() == 0 ? "无" : sb.toString();
    }

    private static void append(StringBuilder sb, String where, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        try {
            ToolStack tool = ToolStack.from(stack);
            if (tool == null) return;
            int chaos = tool.getModifierLevel(CHAOS_FLOW);
            int lucky = tool.getModifierLevel(LUCKY_DROP);
            if (chaos <= 0 && lucky <= 0) return;
            if (sb.length() > 0) sb.append("， ");
            sb.append(where).append("=").append(shortName(stack));
            if (chaos > 0) sb.append("(混沌之流x").append(chaos).append(")");
            if (lucky > 0) sb.append("(幸运掉落x").append(lucky).append(")");
        } catch (Throwable ignored) {
            // 不是匠魂工具就跳过 ✓
        }
    }

    private static String shortName(ItemStack stack) {
        Component name = stack.getHoverName();
        return name == null ? "?" : name.getString();
    }
}
