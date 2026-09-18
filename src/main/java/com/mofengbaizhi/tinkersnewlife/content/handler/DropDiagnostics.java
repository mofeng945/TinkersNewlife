package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * <b>掉落诊断</b>（临时排查用 ✓）：用户反馈"原钻合金（自带混沌之流）打怪不掉东西" ✓，
 * 而关掉 {@code chaos_flow} 就恢复正常 ⇒ 需要看清楚到底哪一环吃掉了掉落 ✓。
 *
 * <p>记录内容（每次玩家击杀一条，限流 300ms、每局最多 40 条 ✓）：
 * <ul>
 *   <li>怪物、**掉落件数与物品 id**、事件是否被取消；</li>
 *   <li>伤害类型（混沌之流会把一刀拆成 "1 物理 + N 学派"，看类型就知道死于哪一段 ✓）；</li>
 *   <li>{@code getLastHurtByMob()} —— 判断"死亡结算时是否认得击杀者"（关系到 {@code killed_by_player} 类战利品 ✓）；</li>
 *   <li>击杀者主手/副手/四件护甲上的 <b>混沌之流</b> / <b>幸运掉落</b> 等级；</li>
 *   <li>四个已知抑制器是否命中（无为变形 / 咒灵释放体 / 召唤物 / 狱门疆）。</li>
 * </ul>
 *
 * <p>开服时会打一行"已启用"，用来确认这段代码真的挂上了 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DropDiagnostics {

    private DropDiagnostics() {
    }

    private static final ModifierId CHAOS_FLOW = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "chaos_flow"));
    private static final ModifierId LUCKY_DROP = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "lucky_drop"));
    private static final ModifierId EX_PIERCE = new ModifierId(new ResourceLocation(TinkersNewlife.MOD_ID, "ex_pierce"));

    private static final int MAX_LINES_PER_SESSION = 80;
    private static final long MIN_GAP_MS = 150L;

    private static long lastLog = 0L;
    private static int lines = 0;
    private static boolean announced = false;

    // ⚠ 死亡与掉落是**同一 tick 前后脚**发生的 ✗ ⇒ 共用一条限流会把"掉落"那条吞掉 ✗
    //   （上一版就吃了这个亏：只看到"死亡"、看不到"掉落 N 件" ✗）⇒ 掉落单独一套计数/间隔 ✓
    private static long lastDropLog = 0L;
    private static int dropLines = 0;

    /** 受击单独一套（100ms / 80 条 ✓）—— 用于看"根本没死"的那些刀 ✓ */
    private static long lastHurtLog = 0L;
    private static int hurtLines = 0;

    /** 开服一行：确认诊断已挂上 ✓（拿不到就把日志级别放没关系的 INFO ✓） */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        announced = true;
        lines = 0;
        dropLines = 0;
        TinkersNewlife.LOGGER.info("[掉落诊断] 已启用（死亡/掉落各自独立记录，各最多 {} 条）", MAX_LINES_PER_SESSION);
    }

    /** 掉落用：独立限流（几乎不间隔 ✓，保证每次死亡后面都能看到掉落件数 ✓） */
    private static boolean takeDropSlot() {
        long now = System.currentTimeMillis();
        if (now - lastDropLog < 5L) return false;
        if (dropLines >= MAX_LINES_PER_SESSION) return false;
        lastDropLog = now;
        dropLines++;
        return true;
    }

    /** ⭐ 死亡事件：**不限击杀者**（任何非玩家实体死亡都记 ✓ —— 先确认"到底死没死、死于什么" ✓） */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        try {
            if (!announced) return;
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) return;
            if (entity instanceof Player) return;                        // 玩家自己死不用看 ✓
            DamageSource source = event.getSource();
            Player killer = killerOf(source);
            if (!takeSlot()) return;
            // ⭐ "记忆表里有没有玩家归属" + "目标自己的 lastHurtByMob" —— 这两条决定战利品能不能带玩家参数 ✓
            net.minecraft.server.level.ServerPlayer remembered =
                    com.mofengbaizhi.tinkersnewlife.content.curse.KillAttribution.find(entity);
            TinkersNewlife.LOGGER.info("[掉落诊断·死亡 #{}/{}] {} 死亡 / 伤害类型={} / 来源击杀者={} / 直接来源={} / 记忆归属={} / lastHurtByMob={} / 主手={}",
                    lines, MAX_LINES_PER_SESSION, entity.getType(), source.getMsgId(),
                    killer == null ? "无" : killer.getName().getString(),
                    source.getDirectEntity() == null ? "null" : source.getDirectEntity().getType().toString(),
                    remembered == null ? "无" : remembered.getName().getString(),
                    entity.getLastHurtByMob() == null ? "null" : entity.getLastHurtByMob().getName().getString(),
                    killer == null || killer.getMainHandItem().isEmpty()
                            ? "-" : killer.getMainHandItem().getHoverName().getString());
        } catch (Throwable ignored) {
        }
    }

    /**
     * ⭐ <b>受击日志</b>：玩家打出的每一刀都记（血量 → 扣多少 → 剩余 / 伤害类型 / 攻击者 / 武器特性）✓
     *
     * <p>为什么还需要它：法杖那一类"根本没造成死亡"的情况（怪没死 ⇒ 既没有死亡事件也没有掉落事件 ✗）
     * 只能靠受击这一层才看得见 ✓ —— 也能看出它的伤害到底是不是穿透、走的是哪条路 ✓。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        try {
            if (!announced) return;
            LivingEntity victim = event.getEntity();
            if (victim == null || victim.level().isClientSide) return;
            if (victim instanceof Player) return;
            DamageSource source = event.getSource();
            Player killer = killerOf(source);
            if (killer == null) return;                      // 只看玩家造成的 ✓
            long now = System.currentTimeMillis();
            if (now - lastHurtLog < 100L) return;
            if (hurtLines >= MAX_LINES_PER_SESSION) return;
            lastHurtLog = now;
            hurtLines++;
            float before = victim.getHealth();
            float amount = event.getAmount();
            TinkersNewlife.LOGGER.info(
                    "[掉落诊断·受击 #{}/{}] {} 血 {}/{} 受到 {} → 剩 {} / 伤害类型={} / 攻击者={} / 直接={} / 武器：{} / 取消={}",
                    hurtLines, MAX_LINES_PER_SESSION, victim.getType(),
                    String.format("%.1f", before), String.format("%.1f", victim.getMaxHealth()),
                    String.format("%.1f", amount), String.format("%.1f", Math.max(0.0F, before - amount)),
                    source.getMsgId(), killer.getName().getString(),
                    source.getDirectEntity() == null ? "null" : source.getDirectEntity().getType().toString(),
                    modifierSources(killer), event.isCanceled());
        } catch (Throwable ignored) {
        }
    }

    /** ⭐ 掉落物实体生成：确认"东西到底有没有生成出来"（生成又消失 ⇒ 是别处吞了 ✗） */
    @SubscribeEvent
    public static void onEntityJoin(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        try {
            if (!announced) return;
            if (!(event.getEntity() instanceof ItemEntity item)) return;
            if (event.getLevel().isClientSide) return;
            Player near = event.getLevel().getNearestPlayer(item, 16.0D);
            if (near == null) return;
            if (!takeDropSlot()) return;
            TinkersNewlife.LOGGER.info("[掉落诊断·掉落物 #{}/{}] 生成 {} x{}（距最近玩家 {} 格）",
                    dropLines, MAX_LINES_PER_SESSION,
                    ForgeRegistries.ITEMS.getKey(item.getItem().getItem()),
                    item.getItem().getCount(),
                    (int) Math.sqrt(near.distanceToSqr(item)));
        } catch (Throwable ignored) {
        }
    }

    /** 限流 + 每局条数上限（三个入口共用 ✓） */
    private static boolean takeSlot() {
        long now = System.currentTimeMillis();
        if (now - lastLog < MIN_GAP_MS) return false;
        if (lines >= MAX_LINES_PER_SESSION) return false;
        lastLog = now;
        lines++;
        return true;
    }

    private static Player killerOf(DamageSource source) {
        if (source == null) return null;
        if (source.getEntity() instanceof Player p) return p;
        if (source.getDirectEntity() instanceof Player p) return p;
        return null;
    }

    /** receiveCanceled = true ⇒ 被别的处理器取消掉的事件我们也能看到 ✓（这正是要查的情况之一 ✓） */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent event) {
        try {
            if (!announced) return;
            LivingEntity entity = event.getEntity();
            if (entity == null || entity.level().isClientSide) return;

            DamageSource source = event.getSource();
            Player killer = killerOf(source);                 // 可能为 null（非玩家击杀也记 ✓）
            if (entity instanceof Player) return;             // 玩家自己死不用看 ✓

            if (!takeDropSlot()) return;                      // ⭐ 掉落单独限流（死亡那条不会把它吞掉 ✓）

            ItemStack hand = killer == null ? ItemStack.EMPTY : killer.getMainHandItem();
            TinkersNewlife.LOGGER.info(
                    "[掉落诊断 #{}/{}] {} → 掉落 {} 件 {} / 取消={} / 伤害类型={} / 击杀者={} 主手={} / 特性：{} / 死亡时 lastHurtByMob={} / 抑制：无为变形={} 释放体={} 召唤物={} 狱门疆={}",
                    dropLines, MAX_LINES_PER_SESSION,
                    entity.getType(),
                    event.getDrops().size(),
                    dropNames(event),
                    event.isCanceled(),
                    source.getMsgId(),
                    killer == null ? "无（非玩家击杀）" : killer.getName().getString(),
                    hand.isEmpty() ? "空手" : hand.getHoverName().getString(),
                    killer == null ? "-" : modifierSources(killer),
                    entity.getLastHurtByMob() == null ? "null" : entity.getLastHurtByMob().getName().getString(),
                    com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.isTransformedUnit(entity),
                    com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique
                            .ownerOfReleased(entity) != null,
                    SummonDropSuppressor.isNoDropSummon(entity),
                    entity.getPersistentData().getBoolean(com.mofengbaizhi.tinkersnewlife.content.gourd.GourdJailHandler.KEY_SUPPRESS_LOOT));
        } catch (Throwable ignored) {
            // 诊断本身绝不影响游戏 ✓
        }
    }

    /** 掉落物 id 列表（最多列 6 个 ✓） */
    private static String dropNames(LivingDropsEvent event) {
        if (event.getDrops().isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        int i = 0;
        for (ItemEntity drop : event.getDrops()) {
            if (i++ > 0) sb.append(", ");
            ItemStack stack = drop.getItem();
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            sb.append(id == null ? "?" : id.toString()).append('x').append(stack.getCount());
            if (i >= 6) { sb.append(", …"); break; }
        }
        return sb.append(']').toString();
    }

    /** 把"穿透EX / 混沌之流 / 幸运掉落"这几个关键特性出现在哪个部位列出来（主手 / 副手 / 四件护甲） */
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
            int pierce = tool.getModifierLevel(EX_PIERCE);
            int chaos = tool.getModifierLevel(CHAOS_FLOW);
            int lucky = tool.getModifierLevel(LUCKY_DROP);
            if (pierce <= 0 && chaos <= 0 && lucky <= 0) return;
            if (sb.length() > 0) sb.append("， ");
            sb.append(where).append('=').append(stack.getHoverName().getString());
            if (pierce > 0) sb.append("(穿透EX)");
            if (chaos > 0) sb.append("(混沌之流x").append(chaos).append(')');
            if (lucky > 0) sb.append("(幸运掉落x").append(lucky).append(')');
        } catch (Throwable ignored) {
            // 不是匠魂工具就跳过 ✓
        }
    }
}
