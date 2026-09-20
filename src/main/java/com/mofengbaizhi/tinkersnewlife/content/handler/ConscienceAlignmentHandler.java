package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 善恶值（{@link ConscienceHandler}）第二期：<b>12 条增减规则 + 两条最外层乘算</b> ✓。
 *
 * <h2>单位约定（很重要 ✓）</h2>
 * 善恶值范围 <b>−50 ~ +50</b> ⇒ <b>1 点 = 1%</b> ✓（不引入"点/百分比"两套单位 ✗ 省得换算出错 ✓）。
 * 所以规则表里写的 {@code -5} 就是"−5%"✓，{@link #DMG_SCALE}=0.01 只是给乘算用的 ✓。
 *
 * <h2>12 条规则（用户口径 ✓ 第 12 条见下方 ⚠）</h2>
 * <table border="1">
 *   <tr><th>#</th><th>行为</th><th>变化</th><th>触发</th></tr>
 *   <tr><td>1</td><td>动物死亡（5 格内）</td><td>−1%</td><td>{@link LivingDeathEvent}</td></tr>
 *   <tr><td>2</td><td>村民死亡（5 格内）</td><td>−5%</td><td>同上</td></tr>
 *   <tr><td>3</td><td>铁傀儡死亡（5 格内）</td><td>−5%</td><td>同上</td></tr>
 *   <tr><td>4</td><td>踩踏农田</td><td>−1%</td><td>{@link BlockEvent.FarmlandTrampleEvent}</td></tr>
 *   <tr><td>5</td><td>破坏村庄结构内的方块</td><td>−1% / 方块</td><td>{@link BlockEvent.BreakEvent}</td></tr>
 *   <tr><td>6</td><td>打开村庄战利品箱</td><td>−3% / 箱子</td><td>{@link PlayerInteractEvent.RightClickBlock}</td></tr>
 *   <tr><td>7</td><td>驯服动物</td><td>+5%</td><td>{@link AnimalTameEvent}</td></tr>
 *   <tr><td>8</td><td>繁殖动物</td><td>+3%</td><td>{@link BabyEntitySpawnEvent}</td></tr>
 *   <tr><td>9</td><td>获得村庄英雄</td><td>+10%</td><td>{@link MobEffectEvent.Added}</td></tr>
 *   <tr><td>10</td><td>击杀 boss（{@code forge:bosses}）</td><td>+3%</td><td>{@link LivingDeathEvent}</td></tr>
 *   <tr><td>11</td><td>每击杀 100 只亡灵</td><td>+1%</td><td>同上（持久计数 ✓）</td></tr>
 *   <tr><td>12</td><td>杀死<b>已驯服的宠物</b></td><td>−3%</td><td>同上</td></tr>
 * </table>
 *
 * <h2>两条最外层乘算（用户口径 ✓）</h2>
 * <ul>
 *   <li><b>受到伤害 ×(1 − 善恶%)</b> ✓ 挂在 {@link LivingDamageEvent}（<b>减伤/护甲之后</b>的最终伤害 ✓
 *       = 能拿到的最外层 ✓）；善 ⇒ 承伤降低 ✓ 恶 ⇒ 承伤升高 ✓。</li>
 *   <li><b>最大生命 ×(1 + 善恶%)</b> ✓ 走 {@link Attributes#MAX_HEALTH} 的
 *       {@link AttributeModifier.Operation#MULTIPLY_TOTAL} 修饰符（乘算里最外层的一档 ✓）。</li>
 * </ul>
 *
 * <h2>几条实现口径（照实写 ✓）</h2>
 * <ul>
 *   <li>接近判定用<b>实体包围盒外扩 5 格</b> ✓ 取<b>该范围内的所有服务端玩家</b>（同罪 ✓ 用户口径"5 格内"✓）；</li>
 *   <li><b>旁观者不算</b> ✗；<b>创造模式算</b> ✓（否则你没法在创造里验证条色 ✓）；</li>
 *   <li>第 1/2/3/12 条是"死亡触发 + 5 格内" ✓ 所以<b>不要求是你亲手杀的</b> ✓（狼群杀的羊也算你头上 ✓ 用户口径 ✓）；</li>
 *   <li>第 6 条的"战利品箱"判定 = 方块实体 NBT 里<b>还有 {@code LootTable}</b> 键 ✓
 *       ⇒ 村庄里<b>第一次</b>打开才算 ✓（原版拆完就把 LootTable 清掉 ✓ 天然"每箱只扣一次" ✓ 不用额外记账 ✓）；
 *       玩家自己放的箱子没有 LootTable ⇒ 不扣 ✓；</li>
 *   <li>第 5 条的"村庄方块"= 该坐标落在 <b>{@link StructureTags#VILLAGE}</b> 的结构片里 ✓（原版村庄结构标签 ✓ 比"附近有村民"准 ✓）；</li>
 *   <li>第 9 条只在<b>第一次</b>获得该效果时给 ✓（`getOldEffectInstance()==null` ✓ 续时长不重复给 ✓）；</li>
 *   <li>第 11 条的计数器存玩家持久数据 ✓ 满 100 清零再 +1% ✓ 不封顶 ✓。</li>
 * </ul>
 *
 * <p>⚠ <b>待你确认</b>：我记录下来的原始口径只数到 <b>11 条</b> ✗，第 12 条是我按最合理的一条补的
 * （"杀死<b>自己驯服的</b>宠物 −3%" ✓ 比普通动物更重 ✓）。
 * 如果你原本指的别的事（例如"与村民交易 +1%"✓"给动物喂食 +1%"✓），说一声即可换 ✓。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceAlignmentHandler {

    private ConscienceAlignmentHandler() {}

    // ============================================================
    //  规则数值（单位 = 百分点 = 善恶值 1 点 ✓）
    // ============================================================

    public static final int ANIMAL_DEATH = -1;          // ①
    public static final int VILLAGER_DEATH = -5;        // ②
    public static final int IRON_GOLEM_DEATH = -5;      // ③
    public static final int FARMLAND_TRAMPLE = -1;      // ④
    public static final int VILLAGE_BLOCK_BREAK = -1;   // ⑤
    public static final int VILLAGE_LOOT_CHEST = -3;    // ⑥
    public static final int ANIMAL_TAME = +5;           // ⑦
    public static final int ANIMAL_BREED = +3;          // ⑧
    public static final int HERO_OF_VILLAGE = +10;      // ⑨
    public static final int BOSS_KILL = +3;             // ⑩
    public static final int UNDEAD_KILL_REWARD = +1;    // ⑪（每 100 只）
    public static final int UNDEAD_KILL_PER = 100;
    public static final int PET_KILL = -3;              // ⑫（⚠ 见类注释 ✓）

    /** 善恶值 1 点 = 1%（乘算用 ✓） */
    public static final double DMG_SCALE = 0.01D;

    /** "5 格内" ✓ */
    private static final double NEAR_RANGE = 5.0D;

    private static final String KEY_UNDEAD_KILLS = "tn_undead_kills";
    private static final String KEY_LOOT_CHESTS = "tn_loot_chests_open";

    /** 最大生命修饰符的固定 UUID（同名同 UUID ⇒ 重加会替换而不是叠加 ✓） */
    private static final UUID HEALTH_MOD_ID = UUID.nameUUIDFromBytes(
            "tinkersnewlife:conscience_health".getBytes(StandardCharsets.UTF_8));
    private static final String HEALTH_MOD_NAME = "tn_conscience_health";

    // ============================================================
    //  ① ② ③ ⑩ ⑪ ⑫：生物死亡
    // ============================================================

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;

        // ⑪ 亡灵计数：只有**亲手击杀**才记 ✓（不清算旁观 ✓）
        if (dead.getMobType() == MobType.UNDEAD
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            countUndeadKill(killer);
        }

        // ⑩ boss：**算击杀者**（远程打死也算 ✓）✓ 没有玩家击杀者时才给 5 格内的玩家 ✓
        if (dead.getType().is(Tags.EntityTypes.BOSSES)) {
            if (event.getSource().getEntity() instanceof ServerPlayer killer) {
                ConscienceHandler.addAlignment(killer, BOSS_KILL);
            } else {
                for (ServerPlayer p : nearbyPlayers(level, dead)) {
                    ConscienceHandler.addAlignment(p, BOSS_KILL);
                }
            }
            return;
        }

        // ⑫ 已驯服的宠物 → ③ 铁傀儡 → ② 村民 → ① 普通动物 ✓（顺序即优先级 ✓）
        int delta;
        if (dead instanceof TamableAnimal tamable && tamable.isTame()) {
            delta = PET_KILL;
        } else if (dead instanceof IronGolem) {
            delta = IRON_GOLEM_DEATH;
        } else if (dead instanceof Villager) {
            delta = VILLAGER_DEATH;
        } else if (dead instanceof Animal) {
            delta = ANIMAL_DEATH;
        } else {
            return;
        }
        for (ServerPlayer p : nearbyPlayers(level, dead)) {
            ConscienceHandler.addAlignment(p, delta);
        }
    }

    /** 每满 {@link #UNDEAD_KILL_PER} 只亡灵 +1%（持久计数器 ✓ 不封顶 ✓） */
    private static void countUndeadKill(ServerPlayer killer) {
        CompoundTag data = killer.getPersistentData();
        int n = data.getInt(KEY_UNDEAD_KILLS) + 1;
        if (n >= UNDEAD_KILL_PER) {
            n -= UNDEAD_KILL_PER;
            ConscienceHandler.addAlignment(killer, UNDEAD_KILL_REWARD);
        }
        data.putInt(KEY_UNDEAD_KILLS, n);
    }

    /** 死亡位置 5 格内的服务端玩家（旁观者不算 ✗ 创造算 ✓） */
    private static java.util.List<ServerPlayer> nearbyPlayers(ServerLevel level, Entity center) {
        return level.getEntitiesOfClass(ServerPlayer.class,
                center.getBoundingBox().inflate(NEAR_RANGE),
                p -> p.isAlive() && !p.isSpectator());
    }

    // ============================================================
    //  ④ 踩踏农田
    // ============================================================

    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            ConscienceHandler.addAlignment(p, FARMLAND_TRAMPLE);
        }
    }

    // ============================================================
    //  ⑤ 破坏村庄方块
    // ============================================================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer p)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (inVillage(level, event.getPos())) {
            ConscienceHandler.addAlignment(p, VILLAGE_BLOCK_BREAK);
        }
    }

    // ============================================================
    //  ⑥ 打开村庄战利品箱
    // ============================================================

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;   // 副手那次不算 ✓
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!inVillage(level, pos)) return;
        if (!hasUnopenedLootTable(level, pos)) return;
        ConscienceHandler.addAlignment(p, VILLAGE_LOOT_CHEST);
        rememberChest(p, pos);
    }

    /** 村庄结构片判定（原版 {@link StructureTags#VILLAGE} ✓） */
    private static boolean inVillage(ServerLevel level, BlockPos pos) {
        try {
            return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 是不是"还没开过的战利品容器" ✓。
     * 原版结构里的箱子 NBT 带 {@code LootTable} ✓ 第一次拆开后会被清掉 ✓
     * ⇒ 天然做到"每个箱子只扣一次" ✓ 玩家自己放的箱子没有该键 ⇒ 不扣 ✓。
     */
    private static boolean hasUnopenedLootTable(ServerLevel level, BlockPos pos) {
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return false;
            CompoundTag tag = be.saveWithoutMetadata();
            return tag.contains("LootTable", Tag.TAG_STRING);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 记账：这个坐标的箱子已经扣过分（防"同箱子反复开关"✓ 上限 256 条 FIFO ✓） */
    private static void rememberChest(ServerPlayer p, BlockPos pos) {
        CompoundTag data = p.getPersistentData();
        long key = pos.asLong();
        net.minecraft.nbt.ListTag list = data.getList(KEY_LOOT_CHESTS, Tag.TAG_LONG);
        for (Tag element : list) {
            // ⚠ 1.20.1 的 ListTag **没有** getLong(int) ✗（那是 1.21.5+ 才有的强类型 API ✓）⇒ 自己拆箱 ✓
            if (element instanceof net.minecraft.nbt.LongTag lt && lt.getAsLong() == key) return;
        }
        list.add(net.minecraft.nbt.LongTag.valueOf(key));
        while (list.size() > 256) list.remove(0);
        data.put(KEY_LOOT_CHESTS, list);
    }

    // ============================================================
    //  ⑦ 驯服
    // ============================================================

    @SubscribeEvent
    public static void onAnimalTame(AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer p) {
            ConscienceHandler.addAlignment(p, ANIMAL_TAME);
        }
    }

    // ============================================================
    //  ⑧ 繁殖
    // ============================================================

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        Player player = event.getCausedByPlayer();
        if (player != null) {
            ConscienceHandler.addAlignment(player, ANIMAL_BREED);
        }
    }

    // ============================================================
    //  ⑨ 村庄英雄
    // ============================================================

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!MobEffects.HERO_OF_THE_VILLAGE.equals(event.getEffectInstance().getEffect())) return;
        if (event.getOldEffectInstance() != null) return;      // 续时长/升级不重复给 ✓
        ConscienceHandler.addAlignment(p, HERO_OF_VILLAGE);
    }

    // ============================================================
    //  最外层乘算之一：受到伤害 ×(1 − 善恶%)
    // ============================================================

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        double pct = ConscienceHandler.getAlignment(p) * DMG_SCALE;   // ±0.5
        if (pct == 0.0D) return;
        float amount = event.getAmount();
        if (amount <= 0.0F) return;
        event.setAmount((float) (amount * (1.0D - pct)));
    }

    // ============================================================
    //  最外层乘算之二：最大生命 ×(1 + 善恶%)
    // ============================================================

    /** 把最大生命修饰符对齐到当前善恶值 ✓（值没变就什么都不做 ✓ 避免每次重加把血顶下去 ✗） */
    public static void refreshMaxHealth(Player player) {
        if (player == null) return;
        try {
            AttributeInstance attr = player.getAttribute(Attributes.MAX_HEALTH);
            if (attr == null) return;
            double desired = ConscienceHandler.getAlignment(player) * DMG_SCALE;   // ±0.5
            AttributeModifier old = attr.getModifier(HEALTH_MOD_ID);
            if (old != null && Math.abs(old.getAmount() - desired) < 1.0E-6D) return;
            if (old != null) attr.removeModifier(HEALTH_MOD_ID);
            if (desired != 0.0D) {
                attr.addTransientModifier(new AttributeModifier(HEALTH_MOD_ID, HEALTH_MOD_NAME, desired,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
            if (player.getHealth() > player.getMaxHealth()) {      // 最大生命变小时夹一下 ✓
                player.setHealth(player.getMaxHealth());
            }
        } catch (Throwable ignored) {
        }
    }

    /** 善恶值一变就立刻同步最大生命 ✓（由 {@link ConscienceHandler#setAlignment} 回调 ✓） */
    public static void onAlignmentChanged(Player player) {
        refreshMaxHealth(player);
    }
}
