package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * TNL1433 善恶规则重做（用户口径 2026-11-18）。
 *
 * <p>规则按世界难度分层继承：
 * 简单=动物日屠阈值、村民/铁傀儡、灾厄与亡灵、击退袭击；
 * 普通=再+村庄方块/箱子、村民职业安排与剥夺；
 * 困难=再+累计战利品箱、随从先死、村民床位、村民幸福度。
 *
 * <p>旧规则事件已停用，只保留属性缩放、阈值效果、交易定价等结果侧机制。
 * 未逐条实机验证；未指定数值都是具名常量。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceRulebook {

    private ConscienceRulebook() {}

    public static final int ANIMAL_DAILY_FREE = 30;
    public static final int ANIMAL_EXCESS_EVIL = -1;
    public static final int VILLAGER_KILL_EVIL = -5;
    public static final int GOLEM_KILL_EVIL = -5;
    public static final int RAIDER_PER = 20;
    public static final int RAIDER_GOOD = 1;
    public static final int UNDEAD_PER = 100;
    public static final int UNDEAD_GOOD = 1;
    public static final int RAID_WIN_GOOD = 5;
    public static final int VILLAGE_BLOCK_EVIL = -1;
    public static final int VILLAGE_CHEST_EVIL = -3;
    public static final int JOB_ASSIGN_GOOD = 1;
    public static final int JOB_REMOVE_EVIL = -1;
    public static final int LOOT_CHEST_PER = 50;
    public static final int LOOT_CHEST_EVIL = -1;
    public static final int SERVANT_DEATH_EVIL = -1;
    public static final int BED_ASSIGN_GOOD = 1;
    public static final int HAPPY_REPUTATION = 30;
    public static final int HAPPY_GOOD = 1;

    private static final String K_ANIMAL_DAY = "tn_rule_animal_day";
    private static final String K_ANIMAL_COUNT = "tn_rule_animal_count";
    private static final String K_RAIDER_KILLS = "tn_rule_raider_kills";
    private static final String K_UNDEAD_KILLS = "tn_rule_undead_kills";
    private static final String K_LOOT_COUNT = "tn_rule_loot_count";
    private static final String K_LOOT_POS = "tn_rule_loot_pos";
    private static final String K_VILLAGE_CHESTS = "tn_rule_village_chests";
    private static final String K_HAPPY = "tn_rule_happy";
    private static final String K_VILLAGER_PROF = "tn_rule_prof";

    /** 1 = 简单，2 = 普通，3 = 困难。 */
    private static int tier(Player player) {
        Difficulty d = player.level().getDifficulty();
        if (d == Difficulty.HARD) return 3;
        if (d == Difficulty.NORMAL) return 2;
        return 1;
    }

    private static List<ServerPlayer> nearby(ServerLevel level, Entity center, double range) {
        return level.getEntitiesOfClass(ServerPlayer.class,
                center.getBoundingBox().inflate(range), p -> p.isAlive() && !p.isSpectator());
    }

    private static ServerPlayer killerOf(LivingDeathEvent event, LivingEntity dead) {
        if (event.getSource().getEntity() instanceof ServerPlayer p) return p;
        if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj
                && proj.getOwner() instanceof ServerPlayer p2) return p2;
        return null;
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (!(dead.level() instanceof ServerLevel level)) return;
            ServerPlayer killer = killerOf(event, dead);

            if (killer != null) {
                if (dead instanceof Raider || dead instanceof Vex) {
                    bump(killer, K_RAIDER_KILLS, RAIDER_PER, RAIDER_GOOD);
                } else if (dead.getMobType() == MobType.UNDEAD) {
                    bump(killer, K_UNDEAD_KILLS, UNDEAD_PER, UNDEAD_GOOD);
                }
            }

            if (dead instanceof Villager || dead instanceof IronGolem) {
                int delta = dead instanceof Villager ? VILLAGER_KILL_EVIL : GOLEM_KILL_EVIL;
                if (killer != null) {
                    ConscienceHandler.addAlignment(killer, delta);
                } else {
                    for (ServerPlayer p : nearby(level, dead, 5.0D)) ConscienceHandler.addAlignment(p, delta);
                }
            }

            if (dead instanceof Animal && killer != null) {
                CompoundTag d = killer.getPersistentData();
                long day = level.getDayTime() / 24000L;
                if (d.getLong(K_ANIMAL_DAY) != day) {
                    d.putLong(K_ANIMAL_DAY, day);
                    d.putInt(K_ANIMAL_COUNT, 0);
                }
                int n = d.getInt(K_ANIMAL_COUNT) + 1;
                d.putInt(K_ANIMAL_COUNT, n);
                if (n > ANIMAL_DAILY_FREE) ConscienceHandler.addAlignment(killer, ANIMAL_EXCESS_EVIL);
            }

            ServerPlayer owner = servantOwner(dead);
            if (owner != null && owner.isAlive() && owner.level() == level && tier(owner) >= 3) {
                ConscienceHandler.addAlignment(owner, SERVANT_DEATH_EVIL);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void bump(ServerPlayer player, String key, int per, int delta) {
        CompoundTag d = player.getPersistentData();
        int n = d.getInt(key) + 1;
        if (n >= per) {
            n -= per;
            ConscienceHandler.addAlignment(player, delta);
        }
        d.putInt(key, n);
    }

    private static ServerPlayer servantOwner(LivingEntity dead) {
        try {
            if (dead instanceof com.mofengbaizhi.tinkersnewlife.content.entity.ShikigamiMob shikigami) {
                return shikigami.getOwner();
            }
            LivingEntity owner = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.getServantOwner(dead);
            return owner instanceof ServerPlayer sp ? sp : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer p)) return;
            if (!MobEffects.HERO_OF_THE_VILLAGE.equals(event.getEffectInstance().getEffect())) return;
            if (event.getOldEffectInstance() != null) return;
            ConscienceHandler.addAlignment(p, RAID_WIN_GOOD);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        try {
            if (!(event.getPlayer() instanceof ServerPlayer p)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            if (tier(p) < 2) return;
            if (inVillage(level, event.getPos())) {
                ConscienceHandler.addAlignment(p, VILLAGE_BLOCK_EVIL);
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer p)) return;
            if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            net.minecraft.core.BlockPos pos = event.getPos();
            if (!hasUnopenedLootTable(level, pos)) return;

            int tier = tier(p);
            if (tier >= 2 && inVillage(level, pos) && remember(p, K_VILLAGE_CHESTS, pos)) {
                ConscienceHandler.addAlignment(p, VILLAGE_CHEST_EVIL);
            }
            if (tier >= 3 && remember(p, K_LOOT_POS, pos)) {
                CompoundTag d = p.getPersistentData();
                int n = d.getInt(K_LOOT_COUNT) + 1;
                if (n >= LOOT_CHEST_PER) {
                    n -= LOOT_CHEST_PER;
                    ConscienceHandler.addAlignment(p, LOOT_CHEST_EVIL);
                }
                d.putInt(K_LOOT_COUNT, n);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean inVillage(ServerLevel level, net.minecraft.core.BlockPos pos) {
        try {
            return level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE).isValid();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasUnopenedLootTable(ServerLevel level, net.minecraft.core.BlockPos pos) {
        try {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) return false;
            return be.saveWithoutMetadata().contains("LootTable", Tag.TAG_STRING);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean remember(ServerPlayer p, String key, net.minecraft.core.BlockPos pos) {
        CompoundTag d = p.getPersistentData();
        long at = pos.asLong();
        ListTag list = d.getList(key, Tag.TAG_LONG);
        for (Tag element : list) {
            if (element instanceof net.minecraft.nbt.LongTag lt && lt.getAsLong() == at) return false;
        }
        list.add(net.minecraft.nbt.LongTag.valueOf(at));
        while (list.size() > 512) list.remove(0);
        d.put(key, list);
        return true;
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer p)) return;
            if (!(event.getLevel() instanceof ServerLevel level)) return;
            if (tier(p) < 3) return;
            BlockState placed = event.getPlacedBlock();
            if (!(placed.getBlock() instanceof BedBlock)) return;
            double x = event.getPos().getX(), y = event.getPos().getY(), z = event.getPos().getZ();
            for (Villager v : level.getEntitiesOfClass(Villager.class,
                    new net.minecraft.world.phys.AABB(x - 16, y - 16, z - 16, x + 16, y + 16, z + 16))) {
                if (!v.getBrain().hasMemoryValue(MemoryModuleType.HOME)) {
                    ConscienceHandler.addAlignment(p, BED_ASSIGN_GOOD);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public static void onVillagerTick(LivingEvent.LivingTickEvent event) {
        try {
            if (!(event.getEntity() instanceof Villager v)) return;
            if (v.level().isClientSide) return;
            if (v.tickCount % 20 != 0) return;
            if (!(v.level() instanceof ServerLevel level)) return;

            VillagerProfession now = v.getVillagerData().getProfession();
            String nowKey = professionKey(now);
            CompoundTag pd = v.getPersistentData();
            String before = pd.getString(K_VILLAGER_PROF);
            if (!nowKey.equals(before)) {
                pd.putString(K_VILLAGER_PROF, nowKey);
                if (!before.isEmpty()) {
                    boolean beforeNone = isNone(before);
                    boolean nowNone = isNone(nowKey);
                    ServerPlayer actor = nearestPlayer(level, v, 16.0D);
                    if (actor != null) {
                        if (beforeNone && !nowNone) ConscienceHandler.addAlignment(actor, JOB_ASSIGN_GOOD);
                        else if (!beforeNone && nowNone) ConscienceHandler.addAlignment(actor, JOB_REMOVE_EVIL);
                    }
                }
            }

            if (level.getDifficulty() == Difficulty.HARD) {
                ServerPlayer near = nearestPlayer(level, v, 8.0D);
                if (near != null && v.getPlayerReputation(near) >= HAPPY_REPUTATION) {
                    long day = level.getDayTime() / 24000L;
                    CompoundTag happy = near.getPersistentData().getCompound(K_HAPPY);
                    String id = v.getUUID().toString();
                    if (happy.getLong(id) != day) {
                        happy.putLong(id, day);
                        if (happy.size() > 256) prune(happy, day);
                        near.getPersistentData().put(K_HAPPY, happy);
                        ConscienceHandler.addAlignment(near, HAPPY_GOOD);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static String professionKey(VillagerProfession prof) {
        var id = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
        return id == null ? "" : id.toString();
    }

    private static boolean isNone(String key) {
        return key.endsWith(":none") || key.isEmpty();
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, Entity center, double range) {
        ServerPlayer best = null;
        double bestD = range * range;
        for (ServerPlayer p : level.players()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            double d = p.distanceToSqr(center);
            if (d <= bestD) { bestD = d; best = p; }
        }
        return best;
    }

    private static void prune(CompoundTag happy, long day) {
        for (String key : List.copyOf(happy.getAllKeys())) {
            if (happy.getLong(key) < day - 2) happy.remove(key);
        }
        if (happy.size() > 256) {
            int drop = happy.size() - 256;
            for (String key : List.copyOf(happy.getAllKeys())) {
                if (drop-- <= 0) break;
                happy.remove(key);
            }
        }
    }
}
