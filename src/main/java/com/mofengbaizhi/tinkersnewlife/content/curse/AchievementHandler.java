package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.content.handler.ConscienceHandler;
import com.mofengbaizhi.tinkersnewlife.content.handler.MomoFavor;
import com.mofengbaizhi.tinkersnewlife.content.curse.domain.DomainRegistry;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 模组成就的发放器（`data/tinkersnewlife/advancements/achievements/*`，共 20 个）。
 *
 * <h2>两类成就</h2>
 * <ul>
 *   <li><b>条件式</b>（`minecraft:inventory_changed` / `player_killed_entity`）✓
 *       —— 原版自己就会发放 ✓ 这里<b>什么都不用做</b> ✗（root / first_spark / cursed_tool /
 *       cursed_tools_full / gate_key / elder_crystal / momo_kill 共 7 个 ✓）。</li>
 *   <li><b>行为式</b>（`minecraft:impossible`）✓ —— 由本类 {@link #award} 发放 ✓
 *       （咒力核心仪式 / 首次展开领域 / 无量空处 / 伏魔御厨子 / 黑闪 / 三技巧 / 全术式 / 全领域 /
 *        善恶两极 / 雇佣墨默 / 好感 / 驯龙 共 13 个 ✓）。</li>
 * </ul>
 *
 * <h2>为什么还要一个每 40 tick 的自查</h2>
 * "集齐全部术式 / 全部领域 / 三技巧 / 善恶到顶 / 好感达标"这类是<b>状态量</b> ✓ 没有单一事件可挂 ✓
 * ⇒ 用轮询自查 ✓（只读已解锁的 advancement 与玩家持久数据 ✓ 开销极小 ✓ 已发放的会被
 * {@link #AWARDED} 短路 ✓ 不会反复查世界 ✗）。
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(
        modid = TinkersNewlife.MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
public final class AchievementHandler {

    /** 成就命名空间下的路径前缀 */
    private static final String PREFIX = "achievements/";

    /** 全部术式 / 领域 / 技巧 的条目数（与 `advancements/techniques|domains|skills` 的文件数一致 ✓） */
    private static final int TOTAL_TECHNIQUES = 21;
    private static final int TOTAL_DOMAINS = 12;
    private static final int TOTAL_SKILLS = 3;

    /** 本进程内"已经发过"的成就（防重复构造资源路径 ✓ 换存档时由 advancement 自身兜底 ✓） */
    private static final java.util.Set<String> AWARDED = ConcurrentHashMap.newKeySet();

    private AchievementHandler() {}

    // ============================================================
    //  发放
    // ============================================================

    /** 发放一个成就（幂等 ✓ 已拿到就不动 ✓） */
    public static void award(ServerPlayer player, String path) {
        String key = player.getUUID() + "|" + path;
        if (AWARDED.contains(key)) return;
        try {
            ServerAdvancementManager manager = player.server.getAdvancements();
            Advancement holder = manager.getAdvancement(
                    new ResourceLocation(TinkersNewlife.MOD_ID, PREFIX + path));
            if (holder == null) {
                TinkersNewlife.LOGGER.warn("[成就] 找不到进度 {}", PREFIX + path);
                return;
            }
            if (!player.getAdvancements().getOrStartProgress(holder).isDone()) {
                player.getAdvancements().award(holder, "unlock");
            }
            AWARDED.add(key);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[成就] 发放 {} 失败: {}", path, t.toString());
        }
    }

    /**
     * 忘掉"本进程已发过"的记录 ✓ —— 给调试指令用（`/tinkersnewlife achievement revoke` ✓）。
     * <p>⚠ 必须清：否则撤销之后再满足条件时，{@link #award} 会被 {@link #AWARDED} 短路 ✗
     * ⇒ **再也发不出来** ✗（测试"重新拿一次"就会卡住 ✓）。
     * <p>只影响本进程的缓存 ✓ **不动**玩家的实际进度 ✓（那由指令那边负责 ✓）。
     */
    public static void forgetAwarded(ServerPlayer player, String path) {
        AWARDED.remove(player.getUUID() + "|" + path);
    }

    /** 该玩家是否已完成模组的某个成就 */
    private static boolean done(ServerPlayer player, String path) {
        ServerAdvancementManager manager = player.server.getAdvancements();
        Advancement holder = manager.getAdvancement(
                new ResourceLocation(TinkersNewlife.MOD_ID, PREFIX + path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** 模组某个（非成就目录下的）进度是否完成，例如 {@code techniques/sky_manipulation} */
    private static boolean doneRaw(ServerPlayer player, String path) {
        ServerAdvancementManager manager = player.server.getAdvancements();
        Advancement holder = manager.getAdvancement(new ResourceLocation(TinkersNewlife.MOD_ID, path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    private static int countDone(ServerPlayer player, String dir, String[] names) {
        int n = 0;
        for (String s : names) if (doneRaw(player, dir + "/" + s)) n++;
        return n;
    }

    /** `advancements/techniques` 下的文件名（= 术式路径 ✓ 与 {@link TechniqueHandler#getAllTechniqueIds()} 同名 ✓） */
    private static final String[] TECHNIQUE_PATHS = {
            "anti_gravity", "black_bird", "blood_manipulation", "construct", "cursed_energy_release",
            "cursed_speech", "cursed_spirit", "flame_manipulation", "jacobs_ladder", "lightning_manipulation",
            "plant_manipulation", "projection", "puppet", "reverse_cursed", "sky_manipulation",
            "ten_divide", "ten_shadows", "wu_wei", "wuliang_cang", "wuliang_wuxian", "yuchuzi"};

    /** `advancements/domains` 下的文件名 */
    private static final String[] DOMAIN_PATHS = {
            "dang_yun_ping_xian", "fumo_yuchuzi", "fuzhu_cisi", "qianhe_yingyi", "san_chong_ji_ku",
            "shi_bao_yue_gong_dian", "taizang_bianye", "tie_guan_gai_wei_shan", "wuliang_kongchu",
            "zhenyan_xiangai", "zi_bi_yuan_dun_guo", "zuosha_botu"};

    /** `advancements/skills` 下的文件名 */
    private static final String[] SKILL_PATHS = {"jianyi_lingyu", "luohua", "mixu_gelong"};

    // ============================================================
    //  行为埋点入口（由各处调用）
    // ============================================================

    /** 咒力核心生成仪式交付产物时调用 */
    public static void onCurseCoreObtained(ServerPlayer player) {
        award(player, "curse_core");
    }

    /** 成功展开领域时调用（{@code DomainRegistry.toggleDomain} 成功分支 ✓） */
    public static void onDomainOpened(ServerPlayer player, ModifierId domainId) {
        award(player, "first_domain");
        String p = domainId.getPath();
        if ("wuliang_kongchu".equals(p)) award(player, "wuliang_kongchu");
        if ("fumo_yuchuzi".equals(p)) award(player, "fumo_yuchuzi");
    }

    /** 打出黑闪时调用 */
    public static void onBlackFlash(ServerPlayer player) {
        award(player, "black_flash");
    }

    /** 成功雇佣墨默时调用 */
    public static void onMomoHired(ServerPlayer player) {
        award(player, "momo_hired");
    }

    /** 用驯龙杖收服一条龙时调用 */
    public static void onDragonTamed(ServerPlayer player) {
        award(player, "dragon_tamed");
    }

    // ============================================================
    //  每 40 tick 的状态自查
    // ============================================================

    /** 每 tick 由 {@link TechniqueHandler#onPlayerTick} 调用；内部自己按 40 tick 节流 */
    public static void checkAchievements(ServerPlayer player) {
        if (player.tickCount % 40 != 0) return;

        // ---- 善恶两极（"心"的 ±上限） ----
        int align = ConscienceHandler.getAlignment(player);
        if (align <= ConscienceHandler.ALIGNMENT_MIN) award(player, "alignment_evil");
        if (align >= ConscienceHandler.ALIGNMENT_MAX) award(player, "alignment_good");

        // ---- 墨默好感 ----
        if (MomoFavor.get(player) >= 30) award(player, "momo_friend");

        // ---- 三技巧 / 全术式 / 全领域（都按"手册解锁进度"数 ✓ 与玩家实际拿到的一致 ✓） ----
        if (countDone(player, "skills", SKILL_PATHS) >= TOTAL_SKILLS) award(player, "skill_all_three");
        if (countDone(player, "techniques", TECHNIQUE_PATHS) >= TOTAL_TECHNIQUES) award(player, "all_techniques");
        if (countDone(player, "domains", DOMAIN_PATHS) >= TOTAL_DOMAINS) award(player, "all_domains");

        // ---- "现持有某物"补发（⚠ 见 §628：inventory_changed 只在背包**变化**那一刻判定 ✗
        //      ⇒ 玩家"早就有"的东西永远不会触发 ✗ ⇒ 这里按持有状态兜底发一次 ✓） ----
        // ⚠⚠ 物品引用**必须写在方法体里**（见 §629 那次启动崩溃 ✗）：
        //    `ModItems.X.get()` 写进 `static final Item[]` 会在**类初始化**时求值 ✓ 那时注册表还没填 ✓
        //    ⇒ `NullPointerException: Registry Object not present` ⇒ **模组加载失败、游戏启动即崩** ✗✗
        if (hasItem(player, ModItems.GUIDE_BOOK.get())) award(player, "root");
        if (hasItem(player, ModItems.GHELOTH_REMAINS.get())) award(player, "first_spark");
        if (hasItem(player, ModItems.ELDER_CRYSTAL.get())) award(player, "elder_crystal");
        if (hasItem(player, ModItems.YOG_SOTHOTH_GATE_KEY.get())) award(player, "gate_key");
        if (hasAny(player, cursedToolItems())) award(player, "cursed_tool");
        if (hasAll(player, momoPoolItems())) award(player, "cursed_tools_full");
        debugScan(player);
    }

    /** 诊断开关（用户实测"拿着书不给根成就"时打开 ✓ 定位完就关掉 ✗ 见 §630） */
    private static final boolean DEBUG_SCAN = true;

    /** 诊断：每 40 tick 打一行"扫到了什么 / root 有没有入手" ✓（只在开着 DEBUG_SCAN 时输出 ✓） */
    private static void debugScan(ServerPlayer player) {
        if (!DEBUG_SCAN) return;
        var inv = player.getInventory();
        boolean book = hasItem(player, ModItems.GUIDE_BOOK.get());
        boolean rootDone = done(player, "root");
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.isEmpty()) continue;
            var id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(s.getItem());
            if (found.length() < 160) found.append(id == null ? "?" : id).append(' ');
        }
        TinkersNewlife.LOGGER.info("[成就诊断] 背包={} 格；有书={}；root 已完成={}；物品: {}",
                inv.getContainerSize(), book, rootDone, found);
    }

    /**
     * 「持械之人」认这些（与 `advancements/achievements/cursed_tool.json` 的条件保持一致 ✓）。
     * <p>⚠ 每次调用现取 ✓ **不要**提成 `static final` 字段 ✗（§629 的启动崩溃就是这么来的 ✓）。
     */
    private static net.minecraft.world.item.Item[] cursedToolItems() {
        return new net.minecraft.world.item.Item[]{
                ModItems.TIAN_NI_HUO.get(), ModItems.GOURD_JAIL.get(), ModItems.LIFE_LAMP_RING.get(),
                ModItems.RING_OF_ONE_MIND.get(), ModItems.COGNITIVE_MASK.get(), ModItems.DURANDAL_SWORD.get(),
                ModItems.YOU_YUN.get(), ModItems.PLANETARIUM.get()};
    }

    /**
     * 「咒具集全」认这六件（= 墨默咒具池 ✓ 与 §623 一致 ✓）。
     * <p>同上：现取 ✓ 不提成静态字段 ✗。
     */
    private static net.minecraft.world.item.Item[] momoPoolItems() {
        return new net.minecraft.world.item.Item[]{
                ModItems.TIAN_NI_HUO.get(), ModItems.YOU_YUN.get(), ModItems.GOURD_JAIL.get(),
                ModItems.LIFE_LAMP_RING.get(), ModItems.RING_OF_ONE_MIND.get(), ModItems.COGNITIVE_MASK.get()};
    }

    /** 背包里有没有这件物品（⚠ 1.20.1 的 `Inventory` **没有** `has(Item)`/`countItem` ⇒ 自己遍历 ✓） */
    private static boolean hasItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(item)) return true;
        }
        return false;
    }

    private static boolean hasAny(ServerPlayer player, net.minecraft.world.item.Item[] items) {
        for (net.minecraft.world.item.Item it : items) if (hasItem(player, it)) return true;
        return false;
    }

    private static boolean hasAll(ServerPlayer player, net.minecraft.world.item.Item[] items) {
        for (net.minecraft.world.item.Item it : items) if (!hasItem(player, it)) return false;
        return true;
    }

    // ============================================================
    //  兼容：旧调用点可能只想"检查+发放"一次
    // ============================================================

    /** 给 {@link TechniqueHandler} 之类的地方用：按佩戴核心上的 modifier 直接发对应成就 */
    public static void checkCoreModifiers(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return;
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return;
        boolean hasAllSkills = true;
        for (ModifierId skill : com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler.getAllSkillIds()) {
            if (ToolHelper.getActiveModifierLevel(tool, skill) <= 0) { hasAllSkills = false; break; }
        }
        if (hasAllSkills) award(player, "skill_all_three");
        // 领域特性存在即算"展开过"（首次展开由 onDomainOpened 负责 ✓ 这里只兜底集齐判定 ✓）
        for (ModifierEntry e : tool.getModifierList()) {
            if (DomainRegistry.isDomain(e.getId())) award(player, "first_domain");
        }
        // 领域全集（按核心上装过的领域特性数计 ✓ 与手册进度互补 ✓）
        int domainsOn = 0;
        for (ModifierEntry e : tool.getModifierList()) if (DomainRegistry.isDomain(e.getId())) domainsOn++;
        if (domainsOn >= TOTAL_DOMAINS) award(player, "all_domains");
    }

    /** 供调试/指令：列出还差哪些成就（未使用，保留给将来） */
    public static Component describe(ServerPlayer player) {
        return Component.literal("achievements: " + AWARDED.size());
    }
}
