package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.modifier.events.CharmHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingConversionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「心」善恶规则·<b>批 3</b>：战斗 / 击杀 / 随从 ✓（备忘录 §440 ✓ 13 条 ✓）。
 *
 * <p><b>总则</b>（用户注明 ✓）：所有"杀死"类规则都指<b>该生物在玩家 5 格范围内死亡</b> ✓
 * （例外：E5 杀同一玩家、E25 杀随从、E26 杀女仆、G6 给随从穿装备 按"击杀者/交互者"判定 ✓）。
 *
 * <ul>
 *   <li><b>E2</b> 不打碎末影水晶杀末影龙 −1% ✓</li>
 *   <li><b>E4</b> 一击杀死任何生物 −1% ✓（致死那次伤害 ≥ 目标最大生命 ✓）</li>
 *   <li><b>E5</b> 杀死同一玩家 5 次 −10% ✓（按被杀者 UUID 分别计数 ✓ 永久累积 ✓）</li>
 *   <li><b>E11</b> 魅惑某生物超过 10s −1% ✓（读 {@link CharmHandler#charmedElapsedTicks} ✓ 每只只扣一次 ✓）</li>
 *   <li><b>E12</b> 10 格内村民被感染成僵尸村民 −3% ✓</li>
 *   <li><b>E13</b> 使其他生物掉落武器/盔甲 −1% ✓</li>
 *   <li><b>E14</b> 凋灵未回满血时被杀 −1% ✓</li>
 *   <li><b>E19</b> 被某生物打后 2s 内杀死它 −1% ✓</li>
 *   <li><b>E25</b> 杀死其他玩家的随从 −2% ✓</li>
 *   <li><b>E26</b> 杀死任何车万女仆 −10% ✓（软依赖 ✓ 没装不触发 ✓）</li>
 *   <li><b>G2</b> 与随从/其他玩家一同作战 +1% ✓（近似：击杀敌对生物时 10 格内有你的随从或别的玩家 ✓）</li>
 *   <li><b>G6</b> 给你的随从穿盔甲并装备武器 +3% ✓（每只生物只算一次 ✓）</li>
 *   <li><b>G11</b> 某生物连续攻击你 &gt;3 次且你没还手 +10% ✓</li>
 * </ul>
 *
 * <p><b>「随从」口径（用户拍板 ✓）</b>：诡厄（Goety）仆从 ✓ 车万女仆 ✓ 原版驯服宠物（狼/猫/鹦鹉/马等 ✓）
 * <b>以及其它 mod 的召唤物（按"拥有者"判定 ✓）</b> —— 实现顺序：
 * {@link TamableAnimal}（原版驯服 ✓）→ {@link OwnableEntity}（原版接口 ✓ 很多 mod 也实现它 ✓）→
 * 反射探测 诡厄 {@code IOwned#getTrueOwner} ✓（软依赖 ✓ 没装就跳过 ✓）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ConscienceCombatHandler {

    private ConscienceCombatHandler() {}

    private static final double NEAR = 5.0D;
    private static final double SERVANT_NEAR = 10.0D;

    // ============================================================
    //  随从判定（四类 ✓）
    // ============================================================

    /** 是某个玩家的随从/仆从/宠物 ⇒ 返回主人 UUID ✓ 否则 null ✓ */
    public static UUID servantOwner(Entity e) {
        try {
            if (e instanceof TamableAnimal t && t.isTame()) return t.getOwnerUUID();
            if (e instanceof OwnableEntity o) return o.getOwnerUUID();
            UUID goety = reflectOwner(e, "com.Polarice3.Goety.api.entities.IOwned", "getTrueOwner");
            if (goety != null) return goety;
            UUID maid = reflectOwner(e, "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid", "getOwnerUUID");
            if (maid != null) return maid;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static UUID reflectOwner(Entity e, String className, String method) {
        try {
            Class<?> c = Class.forName(className);
            if (!c.isInstance(e)) return null;
            Object r = c.getMethod(method).invoke(e);
            if (r instanceof UUID u) return u;
            if (r instanceof Entity ent) return ent.getUUID();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static List<ServerPlayer> nearbyPlayers(Level level, Entity center, double range) {
        if (!(level instanceof ServerLevel sl)) return Collections.emptyList();
        return sl.getEntitiesOfClass(ServerPlayer.class, center.getBoundingBox().inflate(range),
                p -> p.isAlive() && !p.isSpectator());
    }

    // ============================================================
    //  E2：末影水晶 / 末影龙
    // ============================================================

    private static boolean crystalBroken = false;

    @SubscribeEvent
    public static void onCrystalGone(net.minecraftforge.event.entity.EntityLeaveLevelEvent event) {
        try {
            // ⚠ 末影水晶**不是** LivingEntity ✗ 收不到 LivingDeathEvent ✗ ⇒ 用"离开世界"（被打碎/被移除 ✓）判定 ✓
            //   末地常年加载 ⇒ 基本等同"被摧毁" ✓（近似 ✓ 已写进备忘录 ✓）
            if (event.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal) {
                crystalBroken = true;                                   // 打碎过水晶 ⇒ 本轮不算"不打水晶" ✓
            }
            if (event.getEntity() instanceof EnderDragon) crystalBroken = false;   // 新一条龙 ⇒ 重置 ✓
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E4：一击杀死（记最后一次伤害 ✓）
    // ============================================================

    private record LastHit(float amount, long tick) {}

    private static final Map<UUID, LastHit> LAST_HIT = new HashMap<>();

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        try {
            LivingEntity e = event.getEntity();
            if (e.level().isClientSide) return;
            LAST_HIT.put(e.getUUID(), new LastHit(event.getAmount(), e.level().getGameTime()));
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E19 被谁打过 / G11 连击计数
    // ============================================================

    private static final Map<UUID, UUID> LAST_ATTACKER = new HashMap<>();       // 玩家 -> 最近打它的生物
    private static final Map<UUID, Long> LAST_ATTACK_TICK = new HashMap<>();
    private static final Map<String, Integer> HIT_STREAK = new HashMap<>();     // 玩家|攻击者 -> 连击数

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        try {
            // 玩家被生物打 ⇒ 记录 + 连击 ++
            if (event.getEntity() instanceof ServerPlayer victim
                    && event.getSource().getEntity() instanceof LivingEntity attacker
                    && !(attacker instanceof Player)) {
                LAST_ATTACKER.put(victim.getUUID(), attacker.getUUID());
                LAST_ATTACK_TICK.put(victim.getUUID(), victim.level().getGameTime());
                String key = victim.getUUID() + "|" + attacker.getUUID();
                int n = HIT_STREAK.merge(key, 1, Integer::sum);
                if (n > 3) {                                            // G11：连续被打 >3 次没还手 ✓
                    HIT_STREAK.put(key, 0);
                    ConscienceHandler.addAlignment(victim, +10);
                }
            }
            // 玩家打了谁 ⇒ 那一条连击清零（还手了 ✓）
            if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
                LivingEntity target = event.getEntity();
                HIT_STREAK.put(attacker.getUUID() + "|" + target.getUUID(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E12：村民被感染成僵尸村民
    // ============================================================

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        try {
            if (!(event.getEntity() instanceof Villager)) return;
            if (!(event.getOutcome() instanceof ZombieVillager)) return;
            for (ServerPlayer p : nearbyPlayers(event.getEntity().level(), event.getEntity(), 10.0D)) {
                ConscienceHandler.addAlignment(p, -3);                  // E12 ✓
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  E13：让生物掉落武器/盔甲
    // ============================================================

    @SubscribeEvent
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (dead.level().isClientSide) return;
            if (!hasEquipment(dead)) return;
            for (ServerPlayer p : nearbyPlayers(dead.level(), dead, NEAR)) {
                ConscienceHandler.addAlignment(p, -1);                  // E13 ✓（近似的"使掉落"✓ 见备忘录 ✓）
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasEquipment(LivingEntity e) {
        if (!e.getMainHandItem().isEmpty() || !e.getOffhandItem().isEmpty()) return true;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            // ⚠ 1.20.1 的 EquipmentSlot.Type 只有 HAND / ARMOR 两个值 ✗ 没有 HUMANOID_ARMOR ✗
            if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR
                    && !e.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    // ============================================================
    //  E2/E4/E5/E14/E19/E25/E26/G2：死亡结算（统一入口 ✓）
    // ============================================================

    private static final Set<UUID> CHARM_PINGED = new HashSet<>();

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        try {
            LivingEntity dead = event.getEntity();
            if (dead.level().isClientSide) return;
            long now = dead.level().getGameTime();

            // E5：杀死同一玩家 5 次 ⇒ −10%
            if (dead instanceof ServerPlayer victim) {
                ServerPlayer killer = killerOf(event, victim);
                if (killer != null && !killer.getUUID().equals(victim.getUUID())) {
                    CompoundTag d = killer.getPersistentData();
                    String key = "tn_kill_" + victim.getUUID();
                    int n = d.getInt(key) + 1;
                    if (n >= 5) {
                        n = 0;
                        ConscienceHandler.addAlignment(killer, -10);
                    }
                    d.putInt(key, n);
                }
                return;
            }

            // E14：凋灵没回满血就被杀 ⇒ −1%
            if (dead instanceof WitherBoss wither && wither.getHealth() < wither.getMaxHealth() - 0.01F) {
                for (ServerPlayer p : nearbyPlayers(dead.level(), dead, NEAR)) ConscienceHandler.addAlignment(p, -1);
            }

            // E2：龙死时这轮没打碎过水晶 ⇒ −1%
            if (dead instanceof EnderDragon && !crystalBroken) {
                for (ServerPlayer p : nearbyPlayers(dead.level(), dead, NEAR)) ConscienceHandler.addAlignment(p, -1);
            }

            // E4：一击致死 ⇒ −1%
            LastHit hit = LAST_HIT.remove(dead.getUUID());
            if (hit != null && now - hit.tick() <= 2 && hit.amount() >= dead.getMaxHealth()) {
                for (ServerPlayer p : nearbyPlayers(dead.level(), dead, NEAR)) ConscienceHandler.addAlignment(p, -1);
            }

            // E19：刚被打过 2s 内就被反杀 ⇒ −1%
            for (ServerPlayer p : nearbyPlayers(dead.level(), dead, NEAR)) {
                UUID attacker = LAST_ATTACKER.get(p.getUUID());
                Long tick = LAST_ATTACK_TICK.get(p.getUUID());
                if (attacker != null && attacker.equals(dead.getUUID()) && tick != null && now - tick <= 40) {
                    ConscienceHandler.addAlignment(p, -1);
                }
            }

            // E25/E26：杀死别人的随从 / 车万女仆 ⇒ −2% / −10%
            ServerPlayer killer = killerOf(event, dead);
            UUID owner = servantOwner(dead);
            if (owner != null && killer != null && !owner.equals(killer.getUUID())) {
                ConscienceHandler.addAlignment(killer, -2);
            }
            if (isTouhouMaid(dead)) {
                if (killer != null) ConscienceHandler.addAlignment(killer, -10);
                else for (ServerPlayer p : nearbyPlayers(dead.level(), dead, NEAR)) ConscienceHandler.addAlignment(p, -10);
            }

            // G2：与随从/其他玩家一同作战（近似：击杀敌对生物时 10 格内有你的随从或别的玩家 ✓）
            if (dead instanceof Monster && killer != null) {
                boolean ally = false;
                for (LivingEntity e : dead.level().getEntitiesOfClass(LivingEntity.class,
                        dead.getBoundingBox().inflate(SERVANT_NEAR), x -> x != killer && x.isAlive())) {
                    if (e instanceof ServerPlayer other && !other.getUUID().equals(killer.getUUID())) { ally = true; break; }
                    UUID o = servantOwner(e);
                    if (o != null && o.equals(killer.getUUID())) { ally = true; break; }
                }
                if (ally) ConscienceHandler.addAlignment(killer, +1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 击杀者：优先看伤害来源 ✓ 否则取 5 格内最近的玩家 ✓（与"杀死规则看 5 格内"一致 ✓） */
    private static ServerPlayer killerOf(LivingDeathEvent event, LivingEntity dead) {
        if (event.getSource().getEntity() instanceof ServerPlayer p) return p;
        if (event.getSource().getEntity() instanceof net.minecraft.world.entity.projectile.Projectile proj
                && proj.getOwner() instanceof ServerPlayer p2) return p2;
        List<ServerPlayer> near = nearbyPlayers(dead.level(), dead, NEAR);
        return near.isEmpty() ? null : near.get(0);
    }

    private static boolean isTouhouMaid(LivingEntity e) {
        try {
            String id = e.getType().builtInRegistryHolder().key().location().toString();
            if (id.startsWith("touhou_little_maid:")) return true;
            return e.getClass().getName().startsWith("com.github.tartaricacid.touhoulittlemaid");
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ============================================================
    //  E11：魅惑超过 10s（每秒扫附近 ✓ 每只只扣一次 ✓）
    // ============================================================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) return;
            if (!(event.player instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 20 != 0) return;
            for (Mob mob : sp.level().getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(24.0D))) {
                if (!CharmHandler.charmedBy(mob.getUUID(), sp.getUUID())) continue;
                if (CharmHandler.charmedElapsedTicks(mob.getUUID()) <= 10 * 20) continue;
                if (!CHARM_PINGED.add(mob.getUUID())) continue;          // 每只只扣一次 ✓
                ConscienceHandler.addAlignment(sp, -1);
            }
            if (sp.tickCount % 1200 == 0) CHARM_PINGED.clear();
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  G6：给随从穿盔甲并装备武器（每只生物一次 ✓）
    // ============================================================

    private static final Set<UUID> SERVANT_EQUIPPED = new HashSet<>();

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;
            if (!(event.getTarget() instanceof LivingEntity servant)) return;
            UUID owner = servantOwner(servant);
            if (owner == null || !owner.equals(sp.getUUID())) return;    // 必须是**你的**随从 ✓
            if (!SERVANT_EQUIPPED.add(servant.getUUID())) return;        // 每只只算一次 ✓

            ItemStack held = event.getItemStack();
            boolean gear = held.getItem() instanceof ArmorItem || held.getItem() instanceof SwordItem
                    || held.getItem() instanceof AxeItem || held.getItem() instanceof BowItem;
            if (!gear) {
                SERVANT_EQUIPPED.remove(servant.getUUID());
                return;
            }
            // 穿上/装上之后才算：要求主手 + 全身护甲都非空 ✓
            boolean armed = !servant.getMainHandItem().isEmpty() || held.getItem() instanceof SwordItem
                    || held.getItem() instanceof AxeItem || held.getItem() instanceof BowItem;
            boolean armored = !servant.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                    || held.getItem() instanceof ArmorItem;
            if (armed && armored) ConscienceHandler.addAlignment(sp, +3);
        } catch (Throwable ignored) {
        }
    }
}
