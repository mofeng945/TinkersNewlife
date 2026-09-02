package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiDisguise;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 无为转变 服务端核心 v3（本体直接变形，不创建替身实体）：
 * <ul>
 *   <li>击杀记录：玩家击杀的生物 EntityType 记录到其持久数据（供 UI 选择形态）</li>
 *   <li>选中形态：UI 中选中的当前形态（持久数据）</li>
 *   <li>顺转（变自己）/反转（变玩家）：玩家本体直接变形成所选生物——修改玩家自身属性
 *       （生命上限/护甲/移速/攻击力）继承该生物数值，攻击被拦截按生物攻击力造成伤害；
 *       客户端经 RenderPlayerEvent 把该玩家渲染成目标生物外观（无替身实体）</li>
 *   <li>反转（变生物）：目标生物被替换成所选生物并限时 60s；认施术者为主人（可驯服生物真正认主），
 *       主人受击时护主反击</li>
 *   <li>反转玩家（forced）变形期间无法使用工具（右键禁用），只能空手造成基础伤害</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WuWeiHandler {

    private WuWeiHandler() {}

    /** 变形限时（反转用）：60 秒 */
    private static final int REVERSE_TICKS = 60 * 20;

    /** 玩家持久数据：已记录（击杀过）的形态 id 列表 */
    public static final String KEY_RECORDS = "tinkersnewlife.wuwei_records";
    /** 玩家持久数据：当前选中形态（EntityType 注册名，空 = 未选） */
    public static final String KEY_SELECTED = "tinkersnewlife.wuwei_selected";

    /** 变形玩家：玩家 UUID → 变形数据 */
    private static final Map<UUID, TransformData> TRANSFORMS = new HashMap<>();
    /** 反转生物：化身生物 uuid → 数据（含主人、待还原 NBT） */
    private static final Map<UUID, ReverseMobData> REVERSE_MOBS = new HashMap<>();

    /** 变形中的玩家数据 */
    private static final class TransformData {
        UUID playerId;            // 被变形玩家
        String formId;            // 形态 EntityType 注册名
        int remaining = -1;       // 反转限时（-1 = 顺转无限）
        boolean forcedByOther;    // 是否被他人反转（限时 + 禁工具）
        // 继承数值（来自所选生物的默认属性）
        float maxHealth, armor, toughness, speed, attack;
        // 原玩家属性值（恢复用）
        double origMaxHealth, origArmor, origToughness, origSpeed, origAttack;
        float origHealth;
        // 已广播伪装的标志（用于重置）
        boolean disguiseSent = false;
    }

    /** 反转生物数据 */
    private static final class ReverseMobData {
        UUID ownerId;
        int formId;
        Vec3 restPos;
        float restYRot, restXRot;
        int remaining = REVERSE_TICKS;
        CompoundTag revertNbt;
    }

    // ============================================================
    //  击杀记录 / 选中形态
    // ============================================================

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity killed = event.getEntity();
        if (!(killed instanceof Mob)) return;
        if (killed instanceof Player) return;
        ServerPlayer killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer sp) {
            killer = sp;
        } else if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile p
                && p.getOwner() instanceof ServerPlayer sp2) {
            killer = sp2;
        }
        if (killer == null) return;
        String id = EntityType.getKey(killed.getType()).toString();
        List<String> records = getRecords(killer);
        if (!records.contains(id) && records.size() < 200) {
            records.add(id);
            saveRecords(killer, records);
        }
    }

    private static List<String> getRecords(ServerPlayer player) {
        List<String> list = new ArrayList<>();
        var tag = player.getPersistentData();
        if (tag.contains(KEY_RECORDS)) {
            var nbtList = tag.getList(KEY_RECORDS, net.minecraft.nbt.Tag.TAG_STRING);
            for (int i = 0; i < nbtList.size(); i++) list.add(nbtList.getString(i));
        }
        return list;
    }

    private static void saveRecords(ServerPlayer player, List<String> records) {
        var nbtList = new net.minecraft.nbt.ListTag();
        for (String s : records) nbtList.add(net.minecraft.nbt.StringTag.valueOf(s));
        player.getPersistentData().put(KEY_RECORDS, nbtList);
    }

    public static List<String> getRecordedForms(ServerPlayer player) {
        return getRecords(player);
    }

    public static String getSelected(ServerPlayer player) {
        return player.getPersistentData().getString(KEY_SELECTED);
    }

    public static void setSelected(ServerPlayer player, String entityTypeId) {
        player.getPersistentData().putString(KEY_SELECTED, entityTypeId == null ? "" : entityTypeId);
    }

    public static boolean hasSelection(ServerPlayer player) {
        return !getSelected(player).isEmpty();
    }

    public static boolean hasTechnique(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.WU_WEI.getId()) > 0;
    }

    // ============================================================
    //  状态查询 / 恢复
    // ============================================================

    public static boolean isTransformed(ServerPlayer player) {
        return TRANSFORMS.containsKey(player.getUUID());
    }

    public static boolean isForcedTransform(ServerPlayer player) {
        TransformData d = TRANSFORMS.get(player.getUUID());
        return d != null && d.forcedByOther;
    }

    /** 变形玩家当前形态（空 = 未变形） */
    public static String getFormOf(ServerPlayer player) {
        TransformData d = TRANSFORMS.get(player.getUUID());
        return d != null ? d.formId : "";
    }

    public static void endTransformPublic(ServerPlayer player) {
        endTransform(player, false);
    }

    public static void endTransformKeep(ServerPlayer player) {
        endTransform(player, true);
    }

    /** 结束变形：恢复玩家原属性与生命，撤销伪装 */
    private static void endTransform(ServerPlayer player, boolean keepSelected) {
        TransformData d = TRANSFORMS.remove(player.getUUID());
        if (d == null) return;
        restoreAttributes(player, d);
        if (!keepSelected) setSelected(player, "");
        broadcastDisguise(player, "");
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.revert"), true);
    }

    private static void restoreAttributes(ServerPlayer player, TransformData d) {
        setAttr(player, Attributes.MAX_HEALTH, d.origMaxHealth);
        player.setHealth((float) Math.min(d.origHealth, d.origMaxHealth));
        setAttr(player, Attributes.ARMOR, d.origArmor);
        setAttr(player, Attributes.ARMOR_TOUGHNESS, d.origToughness);
        setAttr(player, Attributes.MOVEMENT_SPEED, d.origSpeed);
        setAttr(player, Attributes.ATTACK_DAMAGE, d.origAttack);
    }

    private static void setAttr(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attr, double v) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.setBaseValue(v);
    }

    private static double getAttr(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attr) {
        AttributeInstance inst = e.getAttribute(attr);
        return inst != null ? inst.getBaseValue() : 0;
    }

    /** 向所有在线玩家广播某玩家的伪装状态（formId 空 = 解除） */
    private static void broadcastDisguise(ServerPlayer disguised, String formId) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.ALL.noArg(),
                new PacketWuWeiDisguise(disguised.getUUID(), formId));
    }

    /** 玩家死亡/登出清理 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            endTransform(sp, false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            endTransform(sp, true);
        }
    }

    // ============================================================
    //  变形核心（本体直接变形：改玩家属性 + 客户端伪装渲染）
    // ============================================================

    /** 读取所选生物的默认五维（创建临时实例读属性后立即丢弃，不进入世界） */
    private static float[] readFormStats(ServerLevel level, EntityType<?> type) {
        float[] stats = new float[5]; // maxHealth, armor, toughness, speed, attack
        Entity sample = null;
        try {
            sample = type.create(level);
        } catch (Exception ignored) {
        }
        if (sample instanceof LivingEntity living) {
            stats[0] = (float) getAttr(living, Attributes.MAX_HEALTH);
            stats[1] = (float) getAttr(living, Attributes.ARMOR);
            stats[2] = (float) getAttr(living, Attributes.ARMOR_TOUGHNESS);
            stats[3] = (float) getAttr(living, Attributes.MOVEMENT_SPEED);
            stats[4] = (float) getAttr(living, Attributes.ATTACK_DAMAGE);
        }
        if (stats[0] <= 0) stats[0] = 20;
        if (stats[3] <= 0) stats[3] = 0.25F;
        if (stats[4] <= 0) stats[4] = 1;
        return stats;
    }

    /**
     * 让玩家直接变形：记录原属性 → 套用生物属性（生命上限/护甲/移速/攻击力）→ 广播伪装。
     * 玩家本体即身体：正常移动/受击/交互；攻击由 AttackEntityEvent 拦截按生物攻击力。
     */
    private static boolean enterForm(ServerPlayer player, String formId, boolean forced, int remaining) {
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null) return false;
        ServerLevel level = player.serverLevel();
        float[] stats = readFormStats(level, type);

        // 先解除已有变形
        if (TRANSFORMS.containsKey(player.getUUID())) endTransform(player, true);

        TransformData d = new TransformData();
        d.playerId = player.getUUID();
        d.formId = formId;
        d.remaining = remaining;
        d.forcedByOther = forced;
        d.maxHealth = stats[0];
        d.armor = stats[1];
        d.toughness = stats[2];
        d.speed = stats[3];
        d.attack = stats[4];
        // 保存玩家原值（含当前生命，恢复时按比例）
        d.origMaxHealth = getAttr(player, Attributes.MAX_HEALTH);
        d.origHealth = player.getHealth();
        d.origArmor = getAttr(player, Attributes.ARMOR);
        d.origToughness = getAttr(player, Attributes.ARMOR_TOUGHNESS);
        d.origSpeed = getAttr(player, Attributes.MOVEMENT_SPEED);
        d.origAttack = getAttr(player, Attributes.ATTACK_DAMAGE);

        // 套用生物属性
        double ratio = d.origMaxHealth > 0 ? player.getHealth() / d.origMaxHealth : 1.0;
        setAttr(player, Attributes.MAX_HEALTH, stats[0]);
        player.setHealth((float) (stats[0] * ratio));
        setAttr(player, Attributes.ARMOR, stats[1]);
        setAttr(player, Attributes.ARMOR_TOUGHNESS, stats[2]);
        setAttr(player, Attributes.MOVEMENT_SPEED, stats[3]);
        setAttr(player, Attributes.ATTACK_DAMAGE, stats[4]);
        TRANSFORMS.put(player.getUUID(), d);

        // 广播伪装（其他客户端把该玩家渲染成生物）
        broadcastDisguise(player, formId);
        level.sendParticles(ParticleTypes.SNEEZE, player.getX(), player.getY() + 1, player.getZ(),
                16, 0.6, 1.0, 0.6, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    /** 顺转键处理 */
    public static void onSelfKey(ServerPlayer player) {
        if (isTransformed(player)) {
            endTransformPublic(player);
            return;
        }
        if (!hasTechnique(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            return;
        }
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        String formId = getSelected(player);
        if (formId.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.need_select"), true);
            return;
        }
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int cost = (int) Math.ceil((1.0 + (output + affinity / 10.0) / 10.0) * 60.0);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        if (enterForm(player, formId, false, -1)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.self",
                    formDisplayName(formId)), true);
        }
    }

    // ============================================================
    //  反转：视线目标（生物/玩家）变形成所选生物
    // ============================================================

    public static void onReverseKey(ServerPlayer player) {
        if (isTransformed(player)) {
            endTransformPublic(player);
            return;
        }
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        String formId = getSelected(player);
        if (formId.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.need_select"), true);
            return;
        }
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.invalid"), true);
            return;
        }
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        if (player.distanceToSqr(target) > 16.0 * 16.0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.too_far"), true);
            return;
        }
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int cost = (int) Math.ceil((1.0 + (output + affinity / 10.0) / 10.0) * 80.0);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }

        ServerLevel level = player.serverLevel();
        if (target instanceof ServerPlayer targetPlayer) {
            // 玩家目标：对方本体直接变形（限时 60s、由对方自己操控、禁工具）
            if (enterForm(targetPlayer, formId, true, REVERSE_TICKS)) {
                targetPlayer.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.self",
                        formDisplayName(formId)), true);
            }
        } else if (target instanceof Mob targetMob) {
            // 生物目标：保存 NBT → 移除 → 生成所选生物，认主人 + 护主，限时还原
            CompoundTag saved = new CompoundTag();
            targetMob.save(saved);
            saved.putString("id", EntityType.getKey(targetMob.getType()).toString());
            ReverseMobData rd = new ReverseMobData();
            rd.ownerId = player.getUUID();
            rd.restPos = targetMob.position();
            rd.restYRot = targetMob.getYRot();
            rd.restXRot = targetMob.getXRot();
            rd.revertNbt = saved;
            targetMob.discard();
            Entity form = type.create(level);
            if (form instanceof Mob fm) {
                fm.moveTo(rd.restPos.x, rd.restPos.y, rd.restPos.z, rd.restYRot, rd.restXRot);
                fm.setPersistenceRequired();
                fm.setHealth(fm.getMaxHealth());
                // 认主：可驯服生物真正认主（狼/猫/鹦鹉等护主反击天然生效）
                if (fm instanceof TamableAnimal tame) {
                    tame.tame(player);
                }
                level.addFreshEntity(fm);
                rd.formId = fm.getId();
                REVERSE_MOBS.put(fm.getUUID(), rd);
            }
        }
        level.sendParticles(ParticleTypes.SNEEZE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                20, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 1.2F);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.reverse",
                formDisplayName(formId)), true);
    }

    // ============================================================
    //  攻击 / 工具拦截（变形玩家）
    // ============================================================

    /** 变形玩家攻击：取消默认攻击，改为按生物攻击力造成伤害（玩家本体仍是攻击者） */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TransformData d = TRANSFORMS.get(player.getUUID());
        if (d == null) return;
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity living) || target == player) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        float dmg = d.attack;
        dmg = (float) com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper
                .applyCurseCoreTraits(player, living, dmg);
        living.invulnerableTime = 0;
        DamageSource src = player.damageSources().playerAttack(player);
        living.hurt(src, dmg);
        com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(player, living, dmg);
        player.serverLevel().sendParticles(ParticleTypes.CRIT,
                living.getX(), living.getY() + living.getBbHeight() / 2, living.getZ(),
                6, 0.2, 0.2, 0.2, 0.1);
    }

    /** 反转玩家（forced）变形期间禁用右键（工具/食物/方块） */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        TransformData d = TRANSFORMS.get(sp.getUUID());
        if (d != null && d.forcedByOther) {
            event.setCanceled(true);
            sp.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.no_tool"), true);
        }
    }

    /** 反转生物：主人受击 → 护主反击 */
    @SubscribeEvent
    public static void onOwnerHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer owner)) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof LivingEntity aggro)) return;
        MinecraftServer server = owner.serverLevel().getServer();
        for (ReverseMobData rd : REVERSE_MOBS.values()) {
            if (!rd.ownerId.equals(owner.getUUID())) continue;
            Entity form = findEntity(server, rd.formId);
            if (form instanceof Mob mob && mob.isAlive() && mob.distanceToSqr(aggro) < 32.0 * 32.0) {
                mob.setTarget(aggro);
            }
        }
    }

    // ============================================================
    //  服务端 tick：反转倒计时 / 变形属性维持
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (!REVERSE_MOBS.isEmpty()) {
            Iterator<Map.Entry<UUID, ReverseMobData>> it = REVERSE_MOBS.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (tickReverseMob(server, e.getValue())) it.remove();
            }
        }
        if (!TRANSFORMS.isEmpty()) {
            Iterator<Map.Entry<UUID, TransformData>> it = TRANSFORMS.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (tickPlayerForm(server, e.getValue())) it.remove();
            }
        }
    }

    private static Entity findEntity(MinecraftServer server, int id) {
        for (ServerLevel lvl : server.getAllLevels()) {
            Entity e = lvl.getEntity(id);
            if (e != null) return e;
        }
        return null;
    }

    /** 玩家变形：反转限时倒计时；玩家死亡则清理 */
    private static boolean tickPlayerForm(MinecraftServer server, TransformData d) {
        ServerPlayer player = server.getPlayerList().getPlayer(d.playerId);
        if (player == null || !player.isAlive()) return true;
        if (d.remaining > 0) {
            d.remaining--;
            if (d.remaining <= 0) {
                endTransform(player, true);
                return true;
            }
        }
        // 属性持续维持（防装备/药水覆盖），本体正常
        setAttr(player, Attributes.MAX_HEALTH, d.maxHealth);
        setAttr(player, Attributes.ARMOR, d.armor);
        setAttr(player, Attributes.ARMOR_TOUGHNESS, d.toughness);
        setAttr(player, Attributes.MOVEMENT_SPEED, d.speed);
        setAttr(player, Attributes.ATTACK_DAMAGE, d.attack);
        return false;
    }

    /** 反转生物倒计时与还原 */
    private static boolean tickReverseMob(MinecraftServer server, ReverseMobData rd) {
        Entity form = findEntity(server, rd.formId);
        rd.remaining--;
        if (form == null || !form.isAlive() || rd.remaining <= 0) {
            if (form != null) form.discard();
            restoreMob(server, rd);
            return true;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(rd.ownerId);
        if (owner == null || !owner.isAlive()) {
            if (form != null) form.discard();
            restoreMob(server, rd);
            return true;
        }
        return false;
    }

    private static void restoreMob(MinecraftServer server, ReverseMobData rd) {
        if (rd.revertNbt == null || !rd.revertNbt.contains("id")) return;
        CompoundTag tag = rd.revertNbt.copy();
        tag.remove("UUID");
        tag.remove("UUIDMost");
        tag.remove("UUIDLeast");
        ServerLevel level = server.getLevel(rd.revertNbt.contains("Dimension")
                ? net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(rd.revertNbt.getString("Dimension")))
                : net.minecraft.world.level.Level.OVERWORLD);
        if (level == null) return;
        Entity revived = EntityType.loadEntityRecursive(tag, level, e -> e);
        if (revived != null) {
            Vec3 at = rd.restPos != null ? rd.restPos : Vec3.ZERO;
            revived.moveTo(at.x, at.y, at.z, rd.restYRot, rd.restXRot);
            level.addFreshEntity(revived);
        }
    }

    // ============================================================
    //  工具
    // ============================================================

    private static LivingEntity findLookTarget(ServerPlayer player) {
        var eye = player.getEyePosition(1.0F);
        var look = player.getLookAngle();
        var end = eye.add(look.scale(16.0));
        var box = player.getBoundingBox().expandTowards(look.scale(16.0)).inflate(1.0);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && (e instanceof LivingEntity), 16.0 * 16.0);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    private static Component formDisplayName(String entityTypeId) {
        EntityType<?> type = EntityType.byString(entityTypeId).orElse(null);
        if (type == null) return Component.literal(entityTypeId);
        return Component.translatable(type.getDescriptionId());
    }
}
