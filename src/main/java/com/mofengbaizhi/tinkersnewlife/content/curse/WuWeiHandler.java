package com.mofengbaizhi.tinkersnewlife.content.curse;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.network.PacketWuWeiDisguise;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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

    /** 变形限时（反转玩家用）：60 秒（反转生物为永久变形，不受此限制） */
    private static final int REVERSE_TICKS = 60 * 20;

    /** 玩家持久数据：已记录（击杀过）的形态 id 列表 */
    public static final String KEY_RECORDS = "tinkersnewlife.wuwei_records";
    /** 玩家持久数据：当前选中形态（EntityType 注册名，空 = 未选） */
    public static final String KEY_SELECTED = "tinkersnewlife.wuwei_selected";
    /** 守护生物持久数据：主人 UUID（服务器重启后扫描恢复用） */
    public static final String KEY_GUARD_OWNER = "tinkersnewlife.wuwei_guard_owner";
    /** 玩家持久数据：持续变形状态（无为转变 = 持续性术式，登出保留、重进自动恢复） */
    public static final String KEY_MORPH = "tinkersnewlife.wuwei_morph";

    /** 变形玩家：玩家 UUID → 变形数据 */
    private static final Map<UUID, TransformData> TRANSFORMS = new HashMap<>();
    /** 反转生物（永久守护）：化身生物 uuid → 数据（含主人） */
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
    }

    /** 反转生物（永久守护）数据 */
    private static final class ReverseMobData {
        UUID ownerId;
        int formId;
        Vec3 restPos;
        float restYRot, restXRot;
        /** 玉犬式攻击冷却（tick） */
        int attackCooldown = 0;
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
        clearMorphNbt(player);
        restoreAttributes(player, d);
        if (!keepSelected) setSelected(player, "");
        broadcastDisguise(player, "");
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.revert"), true);
    }

    // ============================================================
    //  持续术式持久化（登出保留、重进自动恢复，同无下限·无限）
    // ============================================================

    private static final String TAG_FORM = "Form";
    private static final String TAG_FORCED = "Forced";
    private static final String TAG_REMAIN = "Remain";
    private static final String TAG_ORIG_MH = "OrigMH";
    private static final String TAG_ORIG_HP = "OrigHP";
    private static final String TAG_ORIG_AR = "OrigAR";
    private static final String TAG_ORIG_TO = "OrigTO";
    private static final String TAG_ORIG_SP = "OrigSP";
    private static final String TAG_ORIG_AT = "OrigAT";

    /** 把当前变形数据写入玩家持久数据（登出后重进自动恢复） */
    private static void saveMorphNbt(ServerPlayer player, TransformData d) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString(TAG_FORM, d.formId);
        tag.putBoolean(TAG_FORCED, d.forcedByOther);
        tag.putInt(TAG_REMAIN, d.remaining);
        tag.putDouble(TAG_ORIG_MH, d.origMaxHealth);
        tag.putFloat(TAG_ORIG_HP, d.origHealth);
        tag.putDouble(TAG_ORIG_AR, d.origArmor);
        tag.putDouble(TAG_ORIG_TO, d.origToughness);
        tag.putDouble(TAG_ORIG_SP, d.origSpeed);
        tag.putDouble(TAG_ORIG_AT, d.origAttack);
        player.getPersistentData().put(KEY_MORPH, tag);
    }

    private static void clearMorphNbt(ServerPlayer player) {
        player.getPersistentData().remove(KEY_MORPH);
    }

    /** 玩家登录：若存档有变形状态（持续术式），自动恢复变形 */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var persistent = player.getPersistentData();
        if (!persistent.contains(KEY_MORPH)) return;
        var tag = persistent.getCompound(KEY_MORPH);
        String formId = tag.getString(TAG_FORM);
        if (formId.isEmpty()) {
            clearMorphNbt(player);
            return;
        }
        // 恢复内存态（属性无需重设：属性已随玩家存档为生物数值）
        TransformData d = new TransformData();
        d.playerId = player.getUUID();
        d.formId = formId;
        d.forcedByOther = tag.getBoolean(TAG_FORCED);
        d.remaining = tag.getInt(TAG_REMAIN);
        d.origMaxHealth = tag.getDouble(TAG_ORIG_MH);
        d.origHealth = tag.getFloat(TAG_ORIG_HP);
        d.origArmor = tag.getDouble(TAG_ORIG_AR);
        d.origToughness = tag.getDouble(TAG_ORIG_TO);
        d.origSpeed = tag.getDouble(TAG_ORIG_SP);
        d.origAttack = tag.getDouble(TAG_ORIG_AT);
        // 重新读取当前生物形态数值（玩家属性现已是生物值）
        float[] stats = readFormStats(player.serverLevel(), EntityType.byString(formId).orElse(null));
        if (stats == null) {
            clearMorphNbt(player);
            return;
        }
        d.maxHealth = stats[0];
        d.armor = stats[1];
        d.toughness = stats[2];
        d.speed = stats[3];
        d.attack = stats[4];
        TRANSFORMS.put(player.getUUID(), d);
        broadcastDisguise(player, formId);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.self",
                formDisplayName(formId)), true);
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

    /** 玩家死亡：解除变形并清空选中（死亡视为脱离术式） */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            endTransform(sp, false);
        }
    }

    /** 玩家登出：持续术式 → 保留变形状态（不还原属性、不清 NBT），重进自动恢复 */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // 只停内存 tick；变形数据已在 enterForm 时写入持久 NBT
            TRANSFORMS.remove(sp.getUUID());
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
     * 变形咒力消耗：|(1 - 咒力亲和/100) × (变成生物最大生命 - 被变形者当前生命)|。
     * 亲和越高消耗越低；形态越强（相对当前生命）消耗越高；向下变形同样按差值绝对值计费。
     */
    private static int morphCost(ServerPlayer caster, float formMaxHealth, double victimCurrentHealth) {
        int affinity = CursePowerHelper.getCurseAffinity(caster);
        double raw = (1.0 - affinity / 100.0) * (formMaxHealth - victimCurrentHealth);
        return (int) Math.ceil(Math.abs(raw));
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
        // 持续术式：变形状态写入玩家持久数据（登出保留、重进自动恢复）
        saveMorphNbt(player, d);

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
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.invalid"), true);
            return;
        }
        // 变形咒力消耗：|(1-亲和/100) × (变成生物最大生命 - 当前自身生命)|
        int cost = morphCost(player, readFormStats(player.serverLevel(), type)[0], player.getHealth());
        if (cost > 0 && !CursePowerHelper.isCurseInfinite(player)
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
    //  转变外放（F 开关）：开启后下一次攻击把目标变形成所选生物
    // ============================================================

    /** 玩家持久数据：转变外放是否开启 */
    public static final String KEY_REVERSAL = "tinkersnewlife.wuwei_reversal";

    /** 是否开启转变外放 */
    public static boolean isReversalActive(ServerPlayer player) {
        return player.getPersistentData().getBoolean(KEY_REVERSAL);
    }

    /** 切换转变外放开关，返回切换后状态 */
    public static boolean toggleReversal(ServerPlayer player) {
        boolean now = !isReversalActive(player);
        player.getPersistentData().putBoolean(KEY_REVERSAL, now);
        return now;
    }

    /** 关闭转变外放 */
    public static void setReversal(ServerPlayer player, boolean on) {
        player.getPersistentData().putBoolean(KEY_REVERSAL, on);
    }

    /**
     * 攻击命中目标 → 尝试把目标变形成所选生物（反转 · 外放）。
     * 返回 true = 本次挥击被消耗（变形成功或明确失败），false = 挥空（外放保持）。
     */
    private static boolean tryAttackReversal(ServerPlayer player, Entity target) {
        if (!isReversalActive(player)) return false;
        // 术式熔断中：不触发外放，本次按普通攻击处理（外放保持）
        if (CursePowerHelper.isBurnout(player)) return false;
        if (!(target instanceof LivingEntity) || target == player) {
            // 打空/打自己：外放保持，等待下一次攻击
            return false;
        }
        if (player.distanceToSqr(target) > 16.0 * 16.0) {
            // 超出术式作用距离：外放保持
            return false;
        }
        String formId = getSelected(player);
        if (formId.isEmpty()) {
            setReversal(player, false);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.need_select_p"), true);
            return true;
        }
        EntityType<?> type = EntityType.byString(formId).orElse(null);
        if (type == null) {
            setReversal(player, false);
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.invalid"), true);
            return true;
        }
        LivingEntity victim = (LivingEntity) target;
        // 只对生物/玩家生效；盔甲架等不可变形 → 外放保持，当作普通挥空
        if (!(victim instanceof ServerPlayer) && !(victim instanceof Mob)) {
            return false;
        }
        // 不反转自己的已驯服宠物/已是守护式神的生物（外放保持）
        if (victim.getPersistentData().contains(KEY_GUARD_OWNER)) {
            return false;
        }
        if (victim instanceof TamableAnimal tame
                && player.getUUID().equals(tame.getOwnerUUID())) {
            return false;
        }
        int output = CursePowerHelper.getCurseOutputLevel(player);
        int affinity = CursePowerHelper.getCurseAffinity(player);
        // 血量门槛：目标生命上限 > 玩家血上限 × (1+输出/10+亲和/100) × 输出等级 → 失败（本次挥击落空，外放保持可再试）
        if (victim instanceof Mob targetCheck) {
            double limit = player.getMaxHealth() * (1.0 + output / 10.0 + affinity / 100.0) * output;
            if (targetCheck.getMaxHealth() > limit) {
                player.displayClientMessage(Component.translatable(
                        "message.tinkersnewlife.wu_wei.too_strong", (int) Math.floor(limit)), true);
                return true;
            }
        }
        // 变形咒力消耗：|(1-亲和/100) × (变成生物最大生命 - 被变形目标当前生命)|
        int cost = morphCost(player, readFormStats(player.serverLevel(), type)[0], victim.getHealth());
        if (cost > 0 && !CursePowerHelper.isCurseInfinite(player)
                && CursePowerHelper.payCurseWithSoulFallback(player, cost) < 0) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_curse"), true);
            return true;
        }
        ServerLevel level = player.serverLevel();
        if (victim instanceof ServerPlayer targetPlayer) {
            // 玩家目标：对方本体直接变形（限时 60s、由对方自己操控、禁工具）
            if (enterForm(targetPlayer, formId, true, REVERSE_TICKS)) {
                targetPlayer.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.self",
                        formDisplayName(formId)), true);
            }
        } else if (victim instanceof Mob targetMob) {
            // 生物目标：永久变形（不可恢复）→ 移除原生物，生成所选生物，
            // AI 替换为"玉犬式守护 AI"（跟随/护主/近战）；死亡即真死
            ReverseMobData rd = new ReverseMobData();
            rd.ownerId = player.getUUID();
            rd.restPos = targetMob.position();
            rd.restYRot = targetMob.getYRot();
            rd.restXRot = targetMob.getXRot();
            targetMob.discard();
            Entity form = type.create(level);
            if (form instanceof Mob fm) {
                fm.moveTo(rd.restPos.x, rd.restPos.y, rd.restPos.z, rd.restYRot, rd.restXRot);
                fm.setPersistenceRequired();
                fm.setHealth(fm.getMaxHealth());
                // 认主：可驯服生物真正认主（狼/猫/鹦鹉等），其余记录主人 UUID
                if (fm instanceof TamableAnimal tame) {
                    tame.tame(player);
                }
                // ⭐ AI 替换为玉犬式守护：清空目标生物原生 AI（不自走/不主动攻击/不逃散）
                fm.goalSelector.removeAllGoals(g -> true);
                fm.targetSelector.removeAllGoals(g -> true);
                fm.setCustomName(Component.translatable("entity." + formId.replace(':', '.')).copy()
                        .append(Component.literal("(守护)")));
                fm.setCustomNameVisible(false);
                // owner 持久标记：服务器重启后经扫描恢复守护 AI（永久变形）
                fm.getPersistentData().putUUID(KEY_GUARD_OWNER, player.getUUID());
                level.addFreshEntity(fm);
                rd.formId = fm.getId();
                REVERSE_MOBS.put(fm.getUUID(), rd);
            }
        }
        level.sendParticles(ParticleTypes.SNEEZE,
                victim.getX(), victim.getY() + victim.getBbHeight() / 2, victim.getZ(),
                20, 0.5, 0.8, 0.5, 0.02);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 1.2F);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.wu_wei.reverse",
                formDisplayName(formId)), true);
        // 变形成功：关闭外放（一次性）
        setReversal(player, false);
        return true;
    }

    // ============================================================
    //  攻击 / 工具拦截（变形玩家）
    // ============================================================

    /** 变形玩家攻击：取消默认攻击，改为按生物攻击力造成伤害（玩家本体仍是攻击者） */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // ⭐ 转变外放：开启时下一次攻击尝试把目标变形成所选生物（成功则消耗本次挥击）
        if (isReversalActive(player) && tryAttackReversal(player, event.getTarget())) {
            event.setCanceled(true);
            return;
        }
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

    /** 反转生物（永久守护）：死亡真死；主人离线则原地待命；主人在线则玉犬式守护。
     *  返回 true = 从表中移除（仅当实体已消失/死亡）。 */
    private static boolean tickReverseMob(MinecraftServer server, ReverseMobData rd) {
        Entity form = findEntity(server, rd.formId);
        if (form == null || !form.isAlive()) {
            // 变形期间死亡 / 已卸载：真死（或区块卸载后实体仍在，由扫描重挂）
            if (form != null && !form.isAlive()) {
                form.discard();
            }
            return true;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(rd.ownerId);
        if (owner == null || !owner.isAlive()) {
            // 主人离线/死亡：原地待命，不消失也不还原（永久变形）
            ((Mob) form).getNavigation().stop();
            return false;
        }
        // 玉犬式守护 AI（每 tick 驱动）
        driveGuardDog(server, (Mob) form, owner, rd);
        return false;
    }

    /**
     * 反转守护生物重挂：带 owner 持久标记的生物每次进入世界（服务器重启读档 / 区块重新加载）
     * 时自动重新注册进守护表并清掉重建的原生 AI——永久变形跨重启保留。
     * （比按超大 AABB 遍历实体安全：坐标范围受世界区块限制，不会溢出实体分区块键）
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!mob.getPersistentData().contains(KEY_GUARD_OWNER)) return;
        // 重载后原生 AI 会随构造器重建：再次清空，保持"玉犬式守护"（不自走/不主动攻击/不逃散）
        mob.goalSelector.removeAllGoals(g -> true);
        mob.targetSelector.removeAllGoals(g -> true);
        UUID ownerId = mob.getPersistentData().getUUID(KEY_GUARD_OWNER);
        ReverseMobData rd = REVERSE_MOBS.get(mob.getUUID());
        if (rd == null) {
            rd = new ReverseMobData();
            REVERSE_MOBS.put(mob.getUUID(), rd);
        }
        rd.ownerId = ownerId;
        rd.formId = mob.getId();
        rd.restPos = mob.position();
        rd.restYRot = mob.getYRot();
        rd.restXRot = mob.getXRot();
    }

    /** 玉犬式守护 AI：跟随主人、追击主人目标/伤害主人的实体、近战攻击（等价式神玉犬行为） */
    private static void driveGuardDog(MinecraftServer server, Mob self, ServerPlayer owner, ReverseMobData rd) {
        if (self.distanceToSqr(owner) > 256.0 * 256.0) {
            // 过远直接传回主人身边（防丢失）
            self.moveTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0);
            return;
        }
        if (rd.attackCooldown > 0) rd.attackCooldown--;
        // 目标：主人最后攻击的实体 > 主人受击来源 > 主人附近对主人有敌意的生物
        LivingEntity target = null;
        LivingEntity lastHurt = owner.getLastHurtMob();
        if (lastHurt != null && lastHurt.isAlive() && lastHurt != self && !isFriendlyToOwner(lastHurt, owner)) {
            target = lastHurt;
        }
        if (target == null) {
            LivingEntity lastBy = owner.getLastHurtByMob();
            if (lastBy != null && lastBy.isAlive() && lastBy != self && !isFriendlyToOwner(lastBy, owner)) {
                target = lastBy;
            }
        }
        if (target == null) {
            // 附近敌对生物（距离主人 24 格内最近的一个）
            double best = 24.0 * 24.0;
            for (LivingEntity e : self.level().getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class,
                    net.minecraft.world.phys.AABB.ofSize(owner.position(), 48, 48, 48),
                    e -> e.isAlive() && e != owner && e != self
                            && e instanceof net.minecraft.world.entity.monster.Enemy
                            && !isFriendlyToOwner(e, owner))) {
                double d = e.distanceToSqr(owner);
                if (d < best) {
                    best = d;
                    target = e;
                }
            }
        }
        if (target == null) {
            // 无目标：跟随主人（保持 3~6 格）
            double d = self.distanceToSqr(owner);
            if (d > 6.0 * 6.0) {
                self.getNavigation().moveTo(owner, 1.2);
            } else if (d < 2.0 * 2.0) {
                self.getNavigation().stop();
            }
            return;
        }
        // 追击目标
        double reachSq = 2.0 * 2.0;
        if (self.distanceToSqr(target) > reachSq) {
            self.getNavigation().moveTo(target, 1.4);
        } else {
            self.getNavigation().stop();
            self.lookAt(target, 30.0F, 30.0F);
        }
        // 近战攻击（玉犬扑咬：冷却 20 tick）
        if (rd.attackCooldown <= 0 && self.distanceToSqr(target) <= reachSq) {
            rd.attackCooldown = 20;
            double dmg = 3.0;
            var attr = self.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
            if (attr != null) dmg = Math.max(2.0, attr.getValue());
            target.invulnerableTime = 0;
            target.hurt(self.damageSources().mobAttack(self), (float) dmg);
            // 令目标反击指向自己（原版 AI 行为）
            if (target instanceof Mob tm && tm.getTarget() == null) {
                tm.setTarget(self);
            }
            // 扑咬粒子/音效
            ((ServerLevel) self.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                    4, 0.2, 0.2, 0.2, 0);
        }
    }

    /** 是否与主人友好的实体（其它式神/主人自身不攻击） */
    private static boolean isFriendlyToOwner(LivingEntity e, ServerPlayer owner) {
        if (e == owner) return true;
        if (e instanceof TamableAnimal tame) {
            UUID o = tame.getOwnerUUID();
            return o != null && o.equals(owner.getUUID());
        }
        return false;
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
