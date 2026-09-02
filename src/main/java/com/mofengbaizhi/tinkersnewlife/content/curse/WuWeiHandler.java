package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.network.PacketBlackBirdCamera;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 无为转变 服务端核心：
 * <ul>
 *   <li>击杀记录：玩家击杀的生物 EntityType 记录到其持久数据（供 UI 选择形态）</li>
 *   <li>选中形态：UI 中选择的当前形态（持久数据）</li>
 *   <li>顺转（变自己）：玩家本体隐形/无敌/钉位，生成所选生物实体并转移视角操控（继承属性、无 AI），
 *       再按术式键恢复人形</li>
 *   <li>反转（变目标）：视线目标（生物或玩家）限时变形成所选生物，到时自动恢复（生物存 NBT 还原）</li>
 *   <li>输入驱动：客户端相机绑定变形实体期间，每 tick 发送操控输入（复用 PacketBlackBirdCamera 切视角）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WuWeiHandler {

    private WuWeiHandler() {}

    /** 变形限时（反转用）：60 秒 */
    private static final int REVERSE_TICKS = 60 * 20;

    /** 玩家持久数据：已记录（击杀过）的形态 id 列表（字符串，EntityType 注册名） */
    public static final String KEY_RECORDS = "tinkersnewlife.wuwei_records";
    /** 玩家持久数据：当前选中的形态（EntityType 注册名，空 = 未选） */
    public static final String KEY_SELECTED = "tinkersnewlife.wuwei_selected";

    /** 玩家 UUID → 变形数据（同一玩家同时只有一个变形） */
    private static final Map<UUID, TransformData> TRANSFORMS = new HashMap<>();

    /** 一条变形数据 */
    private static final class TransformData {
        UUID playerId;
        /** 变形实体 id（世界内） */
        int entityId;
        /** 玩家本体钉位 */
        Vec3 restPos;
        float restYRot;
        float restXRot;
        /** 反转限时剩余 tick（-1 = 无限，直到按键/死亡恢复） */
        int remaining = -1;
        /** 反转目标的原始 NBT（生物目标被移除后用于还原；玩家目标为 null） */
        CompoundTag revertNbt = null;
        // 客户端输入缓存
        float inputZza, inputXxa;
        boolean inputJump;
        float inputYRot, inputXRot;
    }

    // ============================================================
    //  击杀记录 / 选中形态
    // ============================================================

    /** 击杀生物 → 记录形态（排除玩家自身与模组工具实体；去重，上限 200） */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity killed = event.getEntity();
        if (!(killed instanceof Mob)) return;          // 只记录生物
        if (killed instanceof Player) return;
        ServerPlayer killer = null;
        if (event.getSource().getEntity() instanceof ServerPlayer sp) {
            killer = sp;
        } else if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile p
                && p.getOwner() instanceof ServerPlayer sp2) {
            killer = sp2; // 间接击杀（箭矢等）
        }
        if (killer == null) return;
        // 无需佩戴核心也可记录（拿到术式后即拥有历史）
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
            for (int i = 0; i < nbtList.size(); i++) {
                list.add(nbtList.getString(i));
            }
        }
        return list;
    }

    private static void saveRecords(ServerPlayer player, List<String> records) {
        var nbtList = new net.minecraft.nbt.ListTag();
        for (String s : records) nbtList.add(net.minecraft.nbt.StringTag.valueOf(s));
        player.getPersistentData().put(KEY_RECORDS, nbtList);
    }

    /** 已记录形态 id 列表（UI 显示用） */
    public static List<String> getRecordedForms(ServerPlayer player) {
        return getRecords(player);
    }

    /** 当前选中形态（空字符串 = 未选） */
    public static String getSelected(ServerPlayer player) {
        return player.getPersistentData().getString(KEY_SELECTED);
    }

    /** 设置选中形态（空 = 清除） */
    public static void setSelected(ServerPlayer player, String entityTypeId) {
        player.getPersistentData().putString(KEY_SELECTED, entityTypeId == null ? "" : entityTypeId);
    }

    /** 校验该玩家是否拥有无为转变术式（核心上有该 modifier） */
    public static boolean hasTechnique(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return false;
        ToolStack tool = ToolHelper.getToolStack(core);
        return tool != null && tool.getModifierLevel(Modifiers.WU_WEI.getId()) > 0;
    }

    // ============================================================
    //  变形查询 / 恢复
    // ============================================================

    /** 玩家是否正处于变形状态 */
    public static boolean isTransformed(ServerPlayer player) {
        return TRANSFORMS.containsKey(player.getUUID());
    }

    /** 公开入口：解除变形并清空选中（术式键恢复人形时用） */
    public static void endTransformPublic(ServerPlayer player) {
        endTransform(player, false);
    }

    /** 公开入口：解除变形但保留选中（内部/限时结束时用） */
    public static void endTransformKeep(ServerPlayer player) {
        endTransform(player, true);
    }

    /** 玩家当前的变形实体（可能已失效） */
    public static Entity getFormEntity(ServerPlayer player) {
        TransformData data = TRANSFORMS.get(player.getUUID());
        if (data == null) return null;
        ServerLevel level = player.serverLevel();
        Entity e = level.getEntity(data.entityId);
        return (e != null && e.isAlive()) ? e : null;
    }

    /** 客户端操控输入（由输入包调用） */
    public static void setInput(ServerPlayer player, int entityId, float zza, float xxa, boolean jump,
                                float yRot, float xRot) {
        TransformData data = TRANSFORMS.get(player.getUUID());
        if (data == null || data.entityId != entityId) return;
        data.inputZza = zza;
        data.inputXxa = xxa;
        data.inputJump = jump;
        data.inputYRot = yRot;
        data.inputXRot = xRot;
    }

    /** 强制恢复（变形实体死亡 / 玩家死亡 / 登出 / 反转超时） */
    private static void endTransform(ServerPlayer player, boolean keepSelected) {
        TransformData data = TRANSFORMS.remove(player.getUUID());
        if (data == null) return;
        ServerLevel level = player.serverLevel();
        Entity form = level.getEntity(data.entityId);
        if (form != null) form.discard();
        // 恢复玩家本体
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setNoGravity(false);
        if (data.restPos != null) {
            player.teleportTo(data.restPos.x, data.restPos.y, data.restPos.z);
            player.setYRot(data.restYRot);
            player.setXRot(data.restXRot);
        }
        if (!keepSelected) {
            setSelected(player, "");
        }
        // 生物目标被移除后在此还原
        if (data.revertNbt != null && data.revertNbt.contains("id")) {
            Entity revived = EntityType.loadEntityRecursive(data.revertNbt, level, e -> e);
            if (revived != null) {
                Vec3 at = data.restPos != null ? data.restPos : player.position();
                revived.moveTo(at.x, at.y, at.z, data.restYRot, data.restXRot);
                level.addFreshEntity(revived);
            }
        }
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketBlackBirdCamera(0, false));
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiControl(0));
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.revert"), true);
    }

    /** 玩家死亡：解除变形并清空选中（避免卡死） */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            endTransform(sp, false);
        }
    }

    /** 玩家登出：解除变形 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            endTransform(sp, true);
        }
    }

    // ============================================================
    //  顺转：自己变形成所选生物（视角控制，可恢复）
    // ============================================================

    /**
     * 顺转入口：玩家本体隐形/无敌/钉位，生成所选生物实体，视角转移并开始操控。
     * 返回是否成功；失败时提示（未选形态等由调用方处理）。
     */
    public static boolean transformSelf(ServerPlayer player) {
        String formId = getSelected(player);
        if (formId.isEmpty()) return false;
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null) return false;
        ServerLevel level = player.serverLevel();
        Entity form = type.create(level);
        if (form == null || !(form instanceof Mob mob)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.invalid"), true);
            return false;
        }
        // 玩家本体钉位 + 隐形无敌（灵魂替换为生物）
        TransformData data = new TransformData();
        data.playerId = player.getUUID();
        data.restPos = player.position();
        data.restYRot = player.getYRot();
        data.restXRot = player.getXRot();
        player.setInvisible(true);
        player.setInvulnerable(true);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);

        // 生成变形生物：清除 AI（不继承能力），保留该生物的基础属性（血量/移速等由实体默认值提供）
        mob.setNoAi(true);
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        mob.setPersistenceRequired();
        mob.setInvulnerable(true); // 操控期间免伤（避免 AI/环境误杀），恢复时由实体消失
        Vec3 spawn = player.position().add(0, 1.0, 0);
        mob.moveTo(spawn.x, spawn.y, spawn.z, player.getYRot(), player.getXRot());
        level.addFreshEntity(mob);
        mob.setHealth(mob.getMaxHealth()); // 继承该生物满血
        data.entityId = mob.getId();
        TRANSFORMS.put(player.getUUID(), data);

        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketBlackBirdCamera(mob.getId(), true));
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiControl(mob.getId()));
        level.sendParticles(ParticleTypes.SNEEZE, player.getX(), player.getY() + 1, player.getZ(),
                16, 0.6, 1.0, 0.6, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.self",
                formDisplayName(formId)), true);
        return true;
    }

    /** 顺转键处理：变形中 → 恢复；否则施放顺转 */
    public static void onSelfKey(ServerPlayer player) {
        if (isTransformed(player)) {
            endTransform(player, true);
            return;
        }
        if (!WuWeiHandler.hasTechnique(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.domain.no_core"), true);
            return;
        }
        if (CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        if (getSelected(player).isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.need_select"), true);
            return;
        }
        // 咒力消耗（解的 6 倍量级）
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        int cost = (int) Math.ceil((1.0 + (output + affinity / 10.0) / 10.0) * 60.0);
        if (!CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return;
        }
        transformSelf(player);
    }

    // ============================================================
    //  反转：将视线目标（生物/玩家）变形成所选生物，限时自动恢复
    // ============================================================

    /** 反转键处理 */
    public static void onReverseKey(ServerPlayer player) {
        if (isTransformed(player)) {
            endTransform(player, true);
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
        LivingEntity target = findLookTarget(player);
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_target"), true);
            return;
        }
        if (player.distanceToSqr(target) > 16.0 * 16.0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.too_far"), true);
            return;
        }
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.invalid"), true);
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
            // 玩家目标：把对方套用顺转（限时 60 秒自动恢复）
            transformPlayerReverse(level, targetPlayer, type, formId);
        } else if (target instanceof Mob targetMob) {
            // 生物目标：保存 NBT → 移除 → 生成所选生物（限时还原）
            CompoundTag saved = new CompoundTag();
            targetMob.save(saved);
            saved.putString("id", EntityType.getKey(targetMob.getType()).toString());
            TransformData data = new TransformData();
            data.playerId = player.getUUID(); // 记录施术者（用于无人控制的倒计时清理由 tick 处理）
            data.restPos = targetMob.position();
            data.restYRot = targetMob.getYRot();
            data.restXRot = targetMob.getXRot();
            data.remaining = REVERSE_TICKS;
            data.revertNbt = saved;
            targetMob.discard();
            Entity form = type.create(level);
            if (form instanceof Mob fm) {
                fm.moveTo(data.restPos.x, data.restPos.y, data.restPos.z, data.restYRot, data.restXRot);
                fm.setPersistenceRequired();
                fm.setHealth(fm.getMaxHealth());
                level.addFreshEntity(fm);
                data.entityId = fm.getId();
                // 键用被变生物自己实体 id？倒计时用施术者键会与自身顺转冲突——改用特殊负值占位：单独存表
                // 这里复用 TRANSFORMS，key 用施术者 uuid；若施术者同时顺转则冲突，为简化反转生物不占用其自身槽位：
                // 采用独立 map（见下）
                reverseForms.put(fm.getUUID(), data);
            }
        }
        level.sendParticles(ParticleTypes.SNEEZE, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                20, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 1.2F);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.reverse",
                formDisplayName(formId)), true);
    }

    /** 生物目标反转的独立表：生物 uuid → 还原数据 */
    private static final Map<UUID, TransformData> reverseForms = new HashMap<>();

    /** 玩家目标反转：限时对目标玩家施放顺转（60s 后自动恢复） */
    private static void transformPlayerReverse(ServerLevel level, ServerPlayer target, EntityType<?> type,
                                               String formId) {
        if (TRANSFORMS.containsKey(target.getUUID())) {
            endTransform(target, true);
        }
        Entity form = type.create(level);
        if (!(form instanceof Mob mob)) return;
        TransformData data = new TransformData();
        data.playerId = target.getUUID();
        data.restPos = target.position();
        data.restYRot = target.getYRot();
        data.restXRot = target.getXRot();
        data.remaining = REVERSE_TICKS;
        target.setInvisible(true);
        target.setInvulnerable(true);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        mob.setNoAi(true);
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        mob.setPersistenceRequired();
        mob.setInvulnerable(true);
        Vec3 spawn = target.position().add(0, 1.0, 0);
        mob.moveTo(spawn.x, spawn.y, spawn.z, target.getYRot(), target.getXRot());
        level.addFreshEntity(mob);
        mob.setHealth(mob.getMaxHealth());
        data.entityId = mob.getId();
        TRANSFORMS.put(target.getUUID(), data);
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new PacketBlackBirdCamera(mob.getId(), true));
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                new com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiControl(mob.getId()));
    }

    // ============================================================
    //  服务端每 tick：驱动变形实体移动 + 反转倒计时
    // ============================================================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        // 反转生物倒计时
        if (!reverseForms.isEmpty()) {
            reverseForms.entrySet().removeIf(e -> tickReverseForm(server, e.getKey(), e.getValue()));
        }
        // 玩家变形驱动
        if (TRANSFORMS.isEmpty()) return;
        TRANSFORMS.entrySet().removeIf(e -> tickPlayerForm(server, e.getKey(), e.getValue()));
    }

    /** 在服务器所有维度中按实体 id 找实体 */
    private static Entity findEntityById(net.minecraft.server.MinecraftServer server, int entityId) {
        for (var level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e != null) return e;
        }
        return null;
    }

    /** 驱动玩家变形实体；返回 true = 应移除（变形结束） */
    private static boolean tickPlayerForm(net.minecraft.server.MinecraftServer server, UUID playerId,
                                          TransformData data) {
        Entity form = findEntityById(server, data.entityId);
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || !player.isAlive() || form == null || !form.isAlive()) {
            if (player != null) endTransform(player, true);
            return true;
        }
        // 反转限时倒计时
        if (data.remaining > 0) {
            data.remaining--;
            if (data.remaining <= 0) {
                endTransform(player, true);
                return true;
            }
        }
        // 玩家本体持续钉位（防被推动/掉落）
        if (data.restPos != null) {
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(data.restPos.x, data.restPos.y, data.restPos.z);
            player.setYRot(data.restYRot);
            player.setXRot(data.restXRot);
            player.yBodyRot = data.restYRot;
            player.yHeadRot = data.restYRot;
        }
        driveForm(form, data);
        return false;
    }

    /** 反转生物目标倒计时（无玩家操控，纯 AI 生物），到期还原 */
    private static boolean tickReverseForm(net.minecraft.server.MinecraftServer server, UUID formUuid,
                                           TransformData data) {
        Entity form = findEntityById(server, data.entityId);
        data.remaining--;
        if (form == null || !form.isAlive() || data.remaining <= 0) {
            // 还原原生物
            if (form != null) form.discard();
            if (data.revertNbt != null && data.revertNbt.contains("id")) {
                var level = server.getLevel(data.revertNbt.contains("Dimension")
                        ? net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(data.revertNbt.getString("Dimension")))
                        : net.minecraft.world.level.Level.OVERWORLD);
                if (level != null) {
                    // 移除旧 UUID，避免与已删除实体残留冲突
                    CompoundTag reviveTag = data.revertNbt.copy();
                    reviveTag.remove("UUID");
                    reviveTag.remove("UUIDMost");
                    reviveTag.remove("UUIDLeast");
                    Entity revived = EntityType.loadEntityRecursive(reviveTag, level, e -> e);
                    if (revived != null) {
                        Vec3 at = data.restPos != null ? data.restPos : Vec3.ZERO;
                        revived.moveTo(at.x, at.y, at.z, data.restYRot, data.restXRot);
                        level.addFreshEntity(revived);
                    }
                }
            }
            return true;
        }
        return false;
    }

    /** 驱动变形实体移动（黑鸟式：视角移动 + 空格上升/跳跃，含重力近似） */
    private static void driveForm(Entity form, TransformData data) {
        if (!(form instanceof Mob mob)) return;
        // 朝向
        mob.setYRot(data.inputYRot);
        mob.yBodyRot = data.inputYRot;
        mob.yHeadRot = data.inputYRot;
        mob.setXRot(data.inputXRot);
        // 运动：W 朝视线水平方向 / A D 侧移；空格：地面跳跃或空中上升
        Vec3 look = mob.getViewVector(1.0F);
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        Vec3 side = new Vec3(flat.z, 0, -flat.x).normalize();
        double speed = 0.55;
        Vec3 motion = Vec3.ZERO;
        if (data.inputZza != 0) motion = motion.add(flat.scale(data.inputZza * speed));
        if (data.inputXxa != 0) motion = motion.add(side.scale(data.inputXxa * speed * 0.7));
        // 垂直：跳跃/上升
        double vy = mob.getDeltaMovement().y;
        if (data.inputJump) {
            if (mob.onGround()) {
                vy = 0.42;
            } else {
                vy += 0.25;
                if (vy > 1.0) vy = 1.0;
            }
        } else if (!mob.onGround()) {
            vy -= 0.08;
            if (vy < -1.0) vy = -1.0;
        } else {
            vy = 0;
        }
        mob.setDeltaMovement(motion.x, vy, motion.z);
        mob.move(MoverType.SELF, mob.getDeltaMovement());
        mob.fallDistance = 0;
    }

    /** 视线索敌（16 格，含玩家；复用 BaseTechnique 同款射线） */
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

    /** 形态显示名（EntityType 本地化键） */
    private static Component formDisplayName(String entityTypeId) {
        EntityType<?> type = EntityType.byString(entityTypeId).orElse(null);
        if (type == null) return Component.literal(entityTypeId);
        return Component.translatable(type.getDescriptionId());
    }

    /** 供 Technique/客户端校验是否已选中（空 = false） */
    public static boolean hasSelection(ServerPlayer player) {
        return !getSelected(player).isEmpty();
    }
}
