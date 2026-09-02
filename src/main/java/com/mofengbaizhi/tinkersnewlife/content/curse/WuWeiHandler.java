package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.network.PacketBlackBirdCamera;
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
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
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
 * 无为转变 服务端核心 v2（本体直接变形）：
 * <ul>
 *   <li>击杀记录：玩家击杀的生物 EntityType 记录到其持久数据（供 UI 选择形态）</li>
 *   <li>选中形态：UI 中选中的当前形态（持久数据）</li>
 *   <li>顺转（变自己）/反转（变玩家）：玩家本体隐形+无敌+跟随化身（不再原地杵着），
 *       化身实体即身体：移动/跳跃/受击均由化身承载，继承该生物全部基础属性（生命/护甲/移速/攻击力）；
 *       攻击被拦截 → 化身以继承的攻击力造成伤害；反转玩家的变形限时 60s，期间无法使用工具（右键禁用）
 *       只能空手造成基础攻击伤害</li>
 *   <li>反转（变生物）：目标被替换成所选生物并限时 60s；被变生物认施术者为主人（可驯服生物真正认主），
 *       主人受击时反击攻击者（护主）</li>
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
        UUID playerId;            // 被变形玩家（本体跟随者 = 控制者）
        int formId;               // 化身实体 id
        Vec3 formPos;             // 上次化身位置（本体跟随用）
        int remaining = -1;       // 反转限时（-1 = 顺转无限）
        boolean forcedByOther;    // 是否被他人反转（限时 + 禁工具）
        // 输入缓存
        float inputZza, inputXxa;
        boolean inputJump;
        float inputYRot, inputXRot;
        // 原玩家数值（结束变形后恢复本体用，本体本身不改属性，此处存原隐形/无敌状态即可）
        boolean wasInvisible;
    }

    /** 反转生物数据 */
    private static final class ReverseMobData {
        UUID ownerId;             // 施术者（主人）
        int formId;               // 当前化身实体 id
        Vec3 restPos;
        float restYRot, restXRot;
        int remaining = REVERSE_TICKS;
        CompoundTag revertNbt;    // 原生物 NBT
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

    /** 该变形是否为"被他人反转"（限时 + 禁工具） */
    public static boolean isForcedTransform(ServerPlayer player) {
        TransformData d = TRANSFORMS.get(player.getUUID());
        return d != null && d.forcedByOther;
    }

    /** 当前化身实体（可失效） */
    public static Entity getFormEntity(ServerPlayer player) {
        TransformData d = TRANSFORMS.get(player.getUUID());
        if (d == null) return null;
        return findEntity(player.serverLevel().getServer(), d.formId);
    }

    public static void endTransformPublic(ServerPlayer player) {
        endTransform(player, false);
    }

    public static void endTransformKeep(ServerPlayer player) {
        endTransform(player, true);
    }

    /** 结束玩家变形：移除化身、恢复本体（回到化身当前位置） */
    private static void endTransform(ServerPlayer player, boolean keepSelected) {
        TransformData d = TRANSFORMS.remove(player.getUUID());
        if (d == null) return;
        ServerLevel level = player.serverLevel();
        Entity form = findEntity(level.getServer(), d.formId);
        Vec3 back = form != null ? form.position() : (d.formPos != null ? d.formPos : player.position());
        if (form != null) form.discard();
        // 本体恢复：出现在化身位置、恢复可视与可交互
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setNoGravity(false);
        player.noPhysics = false;
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(back.x, back.y, back.z);
        if (!keepSelected) setSelected(player, "");
        sendCameraReset(player);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.revert"), true);
    }

    private static void sendCameraReset(ServerPlayer player) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketBlackBirdCamera(0, false));
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiControl(0));
    }

    /** 输入（客户端相机绑定化身期间发送） */
    public static void setInput(ServerPlayer player, int entityId, float zza, float xxa, boolean jump,
                                float yRot, float xRot) {
        TransformData d = TRANSFORMS.get(player.getUUID());
        if (d == null || d.formId != entityId) return;
        d.inputZza = zza;
        d.inputXxa = xxa;
        d.inputJump = jump;
        d.inputYRot = yRot;
        d.inputXRot = xRot;
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
    //  变形核心（顺转自己 / 反转玩家 共用）
    // ============================================================

    /**
     * 让玩家进入变形：本体隐形无敌，生成所选生物化身，视角转移到化身。
     * 本体每 tick 跟随化身位置（不留在原地）。
     */
    private static boolean enterForm(ServerPlayer player, String formId, boolean forced, int remaining) {
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null) return false;
        ServerLevel level = player.serverLevel();
        Entity form = type.create(level);
        if (!(form instanceof Mob mob)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.invalid"), true);
            return false;
        }
        // 先解除已有变形
        if (TRANSFORMS.containsKey(player.getUUID())) endTransform(player, true);

        TransformData d = new TransformData();
        d.playerId = player.getUUID();
        d.remaining = remaining;
        d.forcedByOther = forced;
        d.wasInvisible = player.isInvisible();

        // 化身即身体：清 AI（不继承能力），保留该生物全部默认属性（生命/护甲/移速/攻击）
        mob.setNoAi(true);
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        mob.setPersistenceRequired();
        // 本体的隐形/无敌/无物理，避免双身
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.noPhysics = true;
        player.setDeltaMovement(Vec3.ZERO);
        Vec3 spawn = player.position();
        mob.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
        level.addFreshEntity(mob);
        d.formId = mob.getId();
        d.formPos = spawn;
        TRANSFORMS.put(player.getUUID(), d);

        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketBlackBirdCamera(mob.getId(), true));
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiControl(mob.getId()));
        level.sendParticles(ParticleTypes.SNEEZE, spawn.x, spawn.y + 1, spawn.z,
                16, 0.6, 1.0, 0.6, 0.02);
        level.playSound(null, spawn.x, spawn.y, spawn.z,
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
            // 玩家目标：对方也进入变形（限时 60s、由对方自己操控、禁工具）
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
                fm.setTarget(null);
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

    /**
     * 变形玩家攻击 → 取消原玩家攻击，改为化身以"继承的生物攻击力"造成伤害。
     * 反转玩家（forced）只能空手造成基础伤害（本就以生物攻击力计算）。
     */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        TransformData d = TRANSFORMS.get(player.getUUID());
        if (d == null) return;
        Entity target = event.getTarget();
        Entity form = findEntity(player.serverLevel().getServer(), d.formId);
        if (target == null || target == player || target == form || !(target instanceof LivingEntity living)) {
            event.setCanceled(true);
            return;
        }
        event.setCanceled(true);
        // 化身攻击力（继承生物属性；无攻击力属性则 1）
        float dmg = 1.0F;
        if (form instanceof Mob mob) {
            var attr = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            dmg = attr != null ? (float) attr.getValue() : 1.0F;
            if (dmg <= 0.01F) dmg = 1.0F;
        }
        // 施术者伤害强化（核心特性）应用于化身攻击
        dmg = (float) com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper
                .applyCurseCoreTraits(player, living, dmg);
        living.invulnerableTime = 0;
        DamageSource src = player.damageSources().playerAttack(player);
        living.hurt(src, dmg);
        com.mofengbaizhi.tinkersnewlife.util.CurseCoreTraitHelper.afterCurseCoreHit(player, living, dmg);
        // 命中粒子
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() / 2, living.getZ(),
                6, 0.2, 0.2, 0.2, 0.1);
    }

    /** 反转玩家（forced）变形期间禁用一切右键（工具/食物/方块） */
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
            if (form instanceof Mob mob && mob.isAlive()) {
                // 距离较近才反击
                if (mob.distanceToSqr(aggro) < 32.0 * 32.0) {
                    mob.setTarget(aggro);
                }
            }
        }
    }

    // ============================================================
    //  服务端 tick：驱动化身 + 本体跟随 + 反转倒计时 + 护主清理
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        // 反转生物倒计时 / 化身存活检查
        if (!REVERSE_MOBS.isEmpty()) {
            Iterator<Map.Entry<UUID, ReverseMobData>> it = REVERSE_MOBS.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (tickReverseMob(server, e.getValue())) it.remove();
            }
        }
        // 玩家变形驱动
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

    /** 驱动玩家化身；返回 true = 应结束变形 */
    private static boolean tickPlayerForm(MinecraftServer server, TransformData d) {
        Entity form = findEntity(server, d.formId);
        ServerPlayer player = server.getPlayerList().getPlayer(d.playerId);
        if (player == null || !player.isAlive()) return true;
        if (form == null || !form.isAlive()) {
            // 化身死亡 → 变形被打断，恢复本体
            endTransform(player, true);
            return true;
        }
        // 反转限时
        if (d.remaining > 0) {
            d.remaining--;
            if (d.remaining <= 0) {
                endTransform(player, true);
                return true;
            }
        }
        // 本体跟随化身（不留在原地）
        d.formPos = form.position();
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.noPhysics = true;
        player.teleportTo(form.getX(), form.getY(), form.getZ());
        driveForm(form, d);
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
        // 无主人或主人死亡 → 提前还原
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

    /** 驱动化身移动（继承其基础移速；跳跃 0.42） */
    private static void driveForm(Entity form, TransformData d) {
        if (!(form instanceof Mob mob)) return;
        mob.setYRot(d.inputYRot);
        mob.yBodyRot = d.inputYRot;
        mob.yHeadRot = d.inputYRot;
        mob.setXRot(d.inputXRot);
        Vec3 look = mob.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 side = new Vec3(flat.z, 0, -flat.x).normalize();
        // 继承生物基础移速
        double speed = 0.25;
        var spAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spAttr != null) speed = spAttr.getValue();
        if (speed < 0.05) speed = 0.25;
        Vec3 motion = Vec3.ZERO;
        if (d.inputZza != 0) motion = motion.add(flat.scale(d.inputZza * speed * 3.0));
        if (d.inputXxa != 0) motion = motion.add(side.scale(d.inputXxa * speed * 3.0 * 0.7));
        double vy = mob.getDeltaMovement().y;
        if (d.inputJump) {
            if (mob.onGround()) {
                vy = 0.42;
            } else {
                vy += 0.2;
                if (vy > 0.8) vy = 0.8;
            }
        } else if (!mob.onGround()) {
            vy -= 0.08;
            if (vy < -1.5) vy = -1.5;
        } else {
            vy = 0;
        }
        mob.setDeltaMovement(motion.x, vy, motion.z);
        mob.move(MoverType.SELF, mob.getDeltaMovement());
        mob.fallDistance = 0;
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
