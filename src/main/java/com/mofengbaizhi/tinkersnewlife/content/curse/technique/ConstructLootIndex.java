package com.mofengbaizhi.tinkersnewlife.content.curse.technique;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 构筑造价 · 「获取来源索引」（掉落 / 结构战利品扫描）。
 *
 * <h3>定位（很重要）</h3>
 * 它是<b>缺省证据</b>，不是最终真理：扫描结果默认只写成
 * {@code config/mofengbaizhi/construct/loot_suggestions.toml} 供人工合并进 {@code value_overrides}；
 * 只有把 {@code loot_source_auto_apply} 打开，才会对<b>同时满足</b>下列条件的物品自动加价：
 * <ul>
 *   <li>没有配方（配方链估值抓不到）；</li>
 *   <li>不是方块（硬度代理抓不到）；</li>
 *   <li>可堆叠（不可堆叠加成抓不到）；</li>
 *   <li>没被 {@code value_overrides} / {@code tag_values} 命中；</li>
 *   <li>不在 {@code loot_source_blacklist} 里。</li>
 * </ul>
 *
 * <h3>评分（按需求给的公式）</h3>
 * <pre>
 * 单来源分 = 基础难度
 *          + 20 × max(0, -log10(max(p, 0.001)))      ← 概率越低越贵：p=1→0、0.1→20、0.01→40、0.001→60
 *          + min(10, 3 × log2(平均数量 + 1))
 *          − 可农场惩罚
 * 掉落项   = min(cap, 最高来源分 + 5 × log2(来源数 + 1))   ← 只取最高来源，来源数只给边际加成
 * </pre>
 *
 * <h3>扫描方式</h3>
 * 遍历 {@code entities/*} 与 {@code chests/*} 等掉落表，每张表用真实 {@link LootParams}
 * <b>模拟抽取</b> {@link #SCAN_ROLLS} 次统计概率与平均数量（原版没有公开"静态读池子"的接口）。
 * 为了不卡服，扫描是<b>分帧</b>的：每 tick 处理若干张表，进度用聊天栏汇报。
 */
public final class ConstructLootIndex {

    /** 每张表模拟抽取次数（越大越准、越慢） */
    public static final int SCAN_ROLLS = 64;
    /** 每 tick 处理的表数量（分帧，避免卡服） */
    private static final int TABLES_PER_TICK = 6;
    /** 索引/建议文件的版本号（格式变了就 +1，旧文件会被忽略/覆盖） */
    private static final int INDEX_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path indexFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mofengbaizhi/construct/loot_index.json");
    }

    private static Path suggestionsFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mofengbaizhi/construct/loot_suggestions.toml");
    }

    /** 一条来源证据 */
    public record Source(String type, String id, double p, double avgCount, double base, double score) {}

    /** 某物品的汇总：来源列表 + 建议分 + 是否"可自动应用"（无配方/非方块/可堆叠/未被覆盖） */
    public record Entry(List<Source> sources, double score, boolean autoEligible) {}

    /** 物品 id → 条目（扫描完成后填充；auto_apply 时也从缓存文件读回） */
    private static final Map<String, Entry> INDEX = new LinkedHashMap<>();

    // ---- 扫描进度状态 ----
    private static boolean running = false;
    private static List<ResourceLocation> queue = List.of();
    private static int cursor = 0;
    private static ServerLevel scanLevel;
    private static ServerPlayer scanPlayer;
    private static int okTables = 0, failedTables = 0;

    private ConstructLootIndex() {}

    public static boolean isRunning() {
        return running;
    }

    public static Map<String, Entry> index() {
        return INDEX;
    }

    // ============================================================
    //  扫描
    // ============================================================

    /** 开始扫描（由命令触发）。level/player 用来构造掉落上下文。 */
    public static boolean start(MinecraftServer server, ServerPlayer player) {
        if (running) return false;
        ServerLevel level = player.serverLevel();
        Collection<ResourceLocation> keys = server.getLootData().getKeys(LootDataType.TABLE);
        List<ResourceLocation> targets = new ArrayList<>();
        for (ResourceLocation id : keys) {
            String path = id.getPath();
            // 只扫"物品来源"相关的表：实体掉落 + 箱子/结构战利品
            if (path.startsWith("entities/") || path.startsWith("chests/")
                    || path.startsWith("gameplay/") || path.startsWith("archaeology/")
                    || path.startsWith("pots/") || path.startsWith("dispensers/")) {
                targets.add(id);
            }
        }
        targets.sort(Comparator.comparing(ResourceLocation::toString));
        queue = targets;
        cursor = 0;
        okTables = 0;
        failedTables = 0;
        scanLevel = level;
        scanPlayer = player;
        INDEX.clear();
        running = true;
        TinkersNewlife.LOGGER.info("[构筑/掉落扫描] 开始：共 {} 张掉落表，每表模拟 {} 次", queue.size(), SCAN_ROLLS);
        return true;
    }

    /** 服务端每 tick 推进扫描（分帧） */
    public static void tick(MinecraftServer server) {
        if (!running || scanLevel == null) return;
        for (int n = 0; n < TABLES_PER_TICK && cursor < queue.size(); n++, cursor++) {
            scanTable(queue.get(cursor));
        }
        if (cursor >= queue.size()) {
            running = false;
            finish(server);
        }
    }

    private static void scanTable(ResourceLocation tableId) {
        LootTable table = server_table(tableId);
        if (table == null || table == LootTable.EMPTY) {
            failedTables++;
            return;
        }
        boolean entityTable = tableId.getPath().startsWith("entities/");
        ResourceLocation entityTypeId = entityTable ? entityTypeOf(tableId) : null;
        Entity entity = null;
        double base = 0;
        String type = entityTable ? "entity" : "structure";
        String sourceId = entityTable && entityTypeId != null ? entityTypeId.toString() : tableId.toString();
        try {
            LootParams params;
            if (entityTable && entityTypeId != null) {
                EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(entityTypeId);
                if (et == null) {
                    failedTables++;
                    return;
                }
                entity = et.create(scanLevel);
                if (entity == null) {
                    failedTables++;
                    return;
                }
                params = new LootParams.Builder(scanLevel)
                        .withParameter(LootContextParams.THIS_ENTITY, entity)
                        .withParameter(LootContextParams.ORIGIN, entity.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE,
                                scanLevel.damageSources().playerAttack(scanPlayer))
                        .withOptionalParameter(LootContextParams.KILLER_ENTITY, scanPlayer)
                        .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, scanPlayer)
                        .withParameter(LootContextParams.TOOL, new ItemStack(Items.NETHERITE_SWORD))
                        .withLuck(0.0F)
                        .create(LootContextParamSets.ENTITY);
                base = entityBaseScore(entity) - farmPenalty(entityTypeId);
            } else {
                params = new LootParams.Builder(scanLevel)
                        .withParameter(LootContextParams.ORIGIN, scanPlayer.position())
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, scanPlayer)
                        .withParameter(LootContextParams.TOOL, new ItemStack(Items.NETHERITE_SWORD))
                        .withLuck(0.0F)
                        .create(LootContextParamSets.CHEST);
                base = structureBaseScore(tableId);
            }

            Map<String, Integer> hits = new HashMap<>();
            Map<String, Integer> total = new HashMap<>();
            for (int roll = 0; roll < SCAN_ROLLS; roll++) {
                final Map<String, Integer> perRoll = new HashMap<>();
                table.getRandomItems(params, stack -> {
                    if (stack.isEmpty()) return;
                    ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (key == null) return;
                    perRoll.merge(key.toString(), stack.getCount(), Integer::sum);
                });
                for (Map.Entry<String, Integer> e : perRoll.entrySet()) {
                    hits.merge(e.getKey(), 1, Integer::sum);
                    total.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            for (Map.Entry<String, Integer> e : hits.entrySet()) {
                String itemId = e.getKey();
                double p = e.getValue() / (double) SCAN_ROLLS;
                double avgCount = total.getOrDefault(itemId, 0) / (double) e.getValue();
                double score = sourceScore(base, p, avgCount);
                INDEX.computeIfAbsent(itemId, k -> new Entry(new ArrayList<>(), 0, false))
                        .sources().add(new Source(type, sourceId, p, avgCount, base, score));
            }
            okTables++;
        } catch (Throwable t) {
            failedTables++;
        } finally {
            if (entity != null) entity.discard();
        }
    }

    private static LootTable server_table(ResourceLocation id) {
        try {
            return (LootTable) scanLevel.getServer().getLootData()
                    .getElement(new LootDataId<>(LootDataType.TABLE, id));
        } catch (Throwable t) {
            return null;
        }
    }

    /** entities/wither_skeleton（或更深层）→ minecraft:wither_skeleton */
    private static ResourceLocation entityTypeOf(ResourceLocation tableId) {
        String path = tableId.getPath().substring("entities/".length());
        int slash = path.indexOf('/');
        if (slash > 0) path = path.substring(0, slash);
        return ResourceLocation.tryParse(tableId.getNamespace() + ":" + path);
    }

    /** 单来源评分（需求给的公式） */
    private static double sourceScore(double base, double p, double avgCount) {
        double prob = 20.0 * Math.max(0.0, -Math.log10(Math.max(p, 0.001)));
        double count = Math.min(10.0, 3.0 * (Math.log(avgCount + 1.0) / Math.log(2.0)));
        return base + prob + count;
    }

    /** 最终汇总：最高来源分 + 来源数边际加成，再按配置封顶 */
    private static double aggregateScore(List<Source> sources) {
        double best = 0;
        for (Source s : sources) best = Math.max(best, s.score());
        double bonus = 5.0 * (Math.log(sources.size() + 1.0) / Math.log(2.0));
        return Math.min(cap(), best + bonus);
    }

    private static void finish(MinecraftServer server) {
        // 汇总 + 判定"可自动应用"资格
        List<Map.Entry<String, Entry>> entries = new ArrayList<>(INDEX.entrySet());
        for (Map.Entry<String, Entry> e : entries) {
            Entry old = e.getValue();
            boolean eligible = autoEligible(e.getKey());
            e.setValue(new Entry(old.sources(), aggregateScore(old.sources()), eligible));
        }
        writeIndex();
        writeSuggestions();
        long eligibleCount = INDEX.values().stream().filter(Entry::autoEligible).count();
        TinkersNewlife.LOGGER.info("[构筑/掉落扫描] 完成：成功 {} 张表、失败 {} 张，得到 {} 个物品的来源，其中 {} 个可自动应用",
                okTables, failedTables, INDEX.size(), eligibleCount);
        if (scanPlayer != null) {
            scanPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tinkersnewlife.construct.loot_scan_done", INDEX.size(), eligibleCount,
                    suggestionsFile().toString()), false);
        }
    }

    /**
     * 是否"可自动应用"：无配方、非方块、可堆叠、未被覆盖、不在黑名单。
     * 配方是否有产出由 {@link ConstructTechnique} 的配方表判断（这里只做静态部分）。
     */
    private static boolean autoEligible(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return false;
        var item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) return false;
        if (item instanceof net.minecraft.world.item.BlockItem) return false;   // 方块 → 硬度代理管
        try {
            if (item.getMaxStackSize() <= 1) return false;                      // 不可堆叠 → 独特物品加成管
        } catch (Throwable ignored) {
        }
        for (String s : blacklist()) {
            if (s == null || s.isBlank()) continue;
            String e = s.trim().toLowerCase(Locale.ROOT);
            if (e.equals(itemId.toLowerCase(Locale.ROOT))) return false;
            if (!e.contains(":") && e.equals(id.getNamespace())) return false;
        }
        return true;
    }

    // ============================================================
    //  难度基础分
    // ============================================================

    /** 实体基础分：血量/攻击/护甲折算（Boss 自然很高），可被 entity_value_overrides 覆盖 */
    private static double entityBaseScore(Entity entity) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (typeId != null) {
            Double override = overrideFor(entityOverrides(), typeId.toString());
            if (override != null) return override;
        }
        double hp = 20, atk = 3, armor = 0;
        if (entity instanceof LivingEntity living) {
            hp = living.getMaxHealth();
            var a = living.getAttribute(Attributes.ATTACK_DAMAGE);
            if (a != null) atk = a.getValue();
            var ar = living.getAttribute(Attributes.ARMOR);
            if (ar != null) armor = ar.getValue();
        }
        return Math.min(80.0, hp * 0.6 + atk * 2.0 + armor * 1.5);
    }

    /** 结构基础分：按战利品表路径关键词给默认值，可被 structure_value_overrides 覆盖 */
    private static double structureBaseScore(ResourceLocation tableId) {
        Double override = overrideFor(structureOverrides(), tableId.toString());
        if (override != null) return override;
        String p = tableId.getPath().toLowerCase(Locale.ROOT);
        if (p.contains("ancient_city")) return 50;
        if (p.contains("end_city")) return 45;
        if (p.contains("bastion")) return 40;
        if (p.contains("woodland_mansion")) return 30;
        if (p.contains("buried_treasure")) return 25;
        if (p.contains("nether_bridge")) return 25;
        if (p.contains("stronghold")) return 20;
        if (p.contains("pillager_outpost")) return 20;
        if (p.contains("desert_pyramid") || p.contains("jungle_temple")) return 15;
        if (p.contains("shipwreck") || p.contains("ruined_portal") || p.contains("ocean_ruin")) return 10;
        if (p.contains("mineshaft") || p.contains("dungeon") || p.contains("simple_dungeon")) return 8;
        if (p.contains("village")) return 5;
        if (p.contains("igloo") || p.contains("witch_hut") || p.contains("temple")) return 8;
        return 12;   // 未知结构：中等
    }

    /** 常见养殖场怪物 → 惩罚（腐肉/骨头/线不该因"来源多"而涨价） */
    private static final List<String> FARMLIKE = List.of(
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned", "minecraft:zombie_villager",
            "minecraft:skeleton", "minecraft:stray", "minecraft:spider", "minecraft:cave_spider",
            "minecraft:creeper", "minecraft:cow", "minecraft:pig", "minecraft:sheep", "minecraft:chicken",
            "minecraft:rabbit", "minecraft:cod", "minecraft:salmon", "minecraft:tropical_fish",
            "minecraft:pufferfish", "minecraft:squid", "minecraft:glow_squid", "minecraft:bat",
            "minecraft:slime", "minecraft:magma_cube", "minecraft:blaze", "minecraft:witch",
            "minecraft:guardian", "minecraft:phantom"
    );

    private static double farmPenalty(ResourceLocation entityTypeId) {
        return FARMLIKE.contains(entityTypeId.toString()) ? 18.0 : 0.0;
    }

    private static Double overrideFor(List<? extends String> list, String id) {
        String target = id.toLowerCase(Locale.ROOT);
        String mod = id.contains(":") ? id.substring(0, id.indexOf(':')).toLowerCase(Locale.ROOT) : id;
        for (String raw : list) {
            if (raw == null) continue;
            String e = raw.trim();
            int i = e.indexOf('=');
            if (i <= 0) continue;
            String key = e.substring(0, i).trim().toLowerCase(Locale.ROOT);
            double v;
            try {
                v = Double.parseDouble(e.substring(i + 1).trim());
            } catch (Throwable t) {
                continue;
            }
            if (key.equals(target) || key.equals(mod)) return v;
        }
        return null;
    }

    // ============================================================
    //  读写缓存 / 建议
    // ============================================================

    private static void writeIndex() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", INDEX_VERSION);
            root.addProperty("scanned_at", java.time.Instant.now().toString());
            root.addProperty("rolls", SCAN_ROLLS);
            root.addProperty("tables_ok", okTables);
            root.addProperty("tables_failed", failedTables);
            JsonArray mods = new JsonArray();
            for (var mod : net.minecraftforge.fml.ModList.get().getMods()) {
                mods.add(mod.getModId());
            }
            root.add("mods", mods);
            JsonObject items = new JsonObject();
            for (Map.Entry<String, Entry> e : INDEX.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("score", Math.round(e.getValue().score() * 100) / 100.0);
                o.addProperty("auto_eligible", e.getValue().autoEligible());
                JsonArray sources = new JsonArray();
                for (Source s : e.getValue().sources()) {
                    JsonObject so = new JsonObject();
                    so.addProperty("type", s.type());
                    so.addProperty("id", s.id());
                    so.addProperty("p", Math.round(s.p() * 10000) / 10000.0);
                    so.addProperty("avg", Math.round(s.avgCount() * 100) / 100.0);
                    so.addProperty("base", Math.round(s.base() * 100) / 100.0);
                    so.addProperty("score", Math.round(s.score() * 100) / 100.0);
                    sources.add(so);
                }
                o.add("sources", sources);
                items.add(e.getKey(), o);
            }
            root.add("items", items);
            Path p = indexFile();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root), StandardCharsets.UTF_8);
            TinkersNewlife.LOGGER.info("[构筑/掉落扫描] 索引已写入 {}", p);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[构筑/掉落扫描] 写入索引失败: {}", t.toString());
        }
    }

    /** 写"建议"文件：可直接把里面的行粘进 value_overrides */
    private static void writeSuggestions() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# 构筑术式 · 掉落来源估值建议（自动生成，请人工确认后再合并）\n");
            sb.append("# 用法：把下面条目按需粘贴到 config/mofengbaizhi/tinkersnewlife-common.toml 的\n");
            sb.append("#       [construct] value_overrides = [ ... ] 里。\n");
            sb.append("# 说明：\n");
            sb.append("#   * auto_eligible=true 表示该物品【无配方 + 非方块 + 可堆叠 + 未被覆盖】，\n");
            sb.append("#     可以打开 loot_source_auto_apply 让它自动生效。\n");
            sb.append("#   * 分数 = 最高来源分 + 5*log2(来源数+1)，来源分含概率倒数项与可农场惩罚。\n");
            sb.append("#   * 扫描时间：").append(java.time.Instant.now()).append("\n\n");
            sb.append("value_overrides = [\n");
            List<Map.Entry<String, Entry>> sorted = new ArrayList<>(INDEX.entrySet());
            sorted.sort((a, b) -> Double.compare(b.getValue().score(), a.getValue().score()));
            for (Map.Entry<String, Entry> e : sorted) {
                Entry entry = e.getValue();
                Source best = entry.sources().stream().max(Comparator.comparingDouble(Source::score)).orElse(null);
                String comment = best == null ? "" :
                        String.format(Locale.ROOT, "  # 最高来源 %s %s  p=%.3f x%.2f → %.1f",
                                best.type(), best.id(), best.p(), best.avgCount(), best.score());
                sb.append(String.format(Locale.ROOT, "\t\"%s=%d\",%s%s\n",
                        e.getKey(), (long) Math.round(entry.score()),
                        entry.autoEligible() ? "  # auto_eligible" : "", comment));
            }
            sb.append("]\n");
            Path p = suggestionsFile();
            Files.createDirectories(p.getParent());
            Files.writeString(p, sb.toString(), StandardCharsets.UTF_8);
            TinkersNewlife.LOGGER.info("[构筑/掉落扫描] 建议已写入 {}", p);
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[构筑/掉落扫描] 写入建议失败: {}", t.toString());
        }
    }

    /** 供 auto_apply 使用：从缓存文件读回索引（不存在或版本不符则忽略） */
    public static void loadCachedIndex() {
        if (!INDEX.isEmpty()) return;
        try {
            Path p = indexFile();
            if (!Files.exists(p)) return;
            JsonObject root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("version") && root.get("version").getAsInt() != INDEX_VERSION) {
                TinkersNewlife.LOGGER.warn("[构筑/掉落扫描] 索引版本不符，忽略缓存（重新执行扫描命令即可）");
                return;
            }
            JsonObject items = root.getAsJsonObject("items");
            for (var e : items.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                List<Source> sources = new ArrayList<>();
                if (o.has("sources")) {
                    for (var se : o.getAsJsonArray("sources")) {
                        JsonObject so = se.getAsJsonObject();
                        sources.add(new Source(so.get("type").getAsString(), so.get("id").getAsString(),
                                so.get("p").getAsDouble(), so.get("avg").getAsDouble(),
                                so.get("base").getAsDouble(), so.get("score").getAsDouble()));
                    }
                }
                INDEX.put(e.getKey(), new Entry(sources, o.get("score").getAsDouble(),
                        o.has("auto_eligible") && o.get("auto_eligible").getAsBoolean()));
            }
            TinkersNewlife.LOGGER.info("[构筑/掉落扫描] 已载入缓存索引：{} 个物品", INDEX.size());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[构筑/掉落扫描] 读取缓存索引失败: {}", t.toString());
        }
    }

    // ============================================================
    //  配置
    // ============================================================

    public static boolean enabled() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_LOOT_SOURCE_ENABLED.get();
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean autoApply() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_LOOT_SOURCE_AUTO_APPLY.get();
        } catch (Throwable t) {
            return false;
        }
    }

    private static double cap() {
        try {
            return Math.max(0.0, com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_LOOT_SOURCE_CAP.get());
        } catch (Throwable t) {
            return 60.0;
        }
    }

    private static List<? extends String> blacklist() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_LOOT_SOURCE_BLACKLIST.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<? extends String> entityOverrides() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_ENTITY_VALUE_OVERRIDES.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    private static List<? extends String> structureOverrides() {
        try {
            return com.mofengbaizhi.tinkersnewlife.config.ModConfig.CONSTRUCT_STRUCTURE_VALUE_OVERRIDES.get();
        } catch (Throwable t) {
            return List.of();
        }
    }

    /** 供造价公式查询（未启用 / 未加载 / 不可自动应用 → 0） */
    public static double lootScoreFor(String itemId) {
        if (!enabled() || !autoApply()) return 0.0;
        Entry e = INDEX.get(itemId);
        if (e == null || !e.autoEligible()) return 0.0;
        return e.score();
    }

    /** 忽略 {@code autoEligible}（命令/调试用） */
    public static double rawScoreFor(String itemId) {
        Entry e = INDEX.get(itemId);
        return e == null ? 0.0 : e.score();
    }
}
