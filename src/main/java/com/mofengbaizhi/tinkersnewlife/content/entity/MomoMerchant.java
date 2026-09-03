package com.mofengbaizhi.tinkersnewlife.content.entity;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import com.mofengbaizhi.tinkersnewlife.content.ModSounds;
import com.mofengbaizhi.tinkersnewlife.content.curse.TechniqueHandler;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.tools.item.ModifierCrystalItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 中立实体「武器商人·墨默」：
 * <ul>
 *   <li>中立商人：满月夜晚由 {@code MomoMerchantHandler} 在教堂（占位：村钟）前刷新；
 *       自然刷新版在白天到来时消失，刷怪蛋召唤的常驻</li>
 *   <li>受击反击：手持格赫罗斯战镰；任何攻击者（玩家/怪物/监守者）都能攻击她，她也会反击；
 *       监守者可正常索敌攻击她</li>
 *   <li>攻击 AI（自定义状态机）：快速接近 → 面前 2 格扇形横斩(80%) → 0.5s 后竖劈(180%) → 拉远；
 *       受击后 1s 内格挡（免疫伤害），格挡成功 → 近身连斩 3 刀(60/80/100%)；
 *       半血 → 高高跃起跳劈(300%，破盾) + 乱蝶大招；生命 ≤5% → 逃跑</li>
 *   <li>属性：HP 200 / 攻击 50 / 护甲 14 / 再生 VIII（常驻）</li>
 *   <li>无玩家时在生成点 20 格内游走；每 500tick 10% 概率主动索敌并击杀 10 格内一只亡灵；
 *       地上有格赫罗斯残骸/矿石（20 格内）会被吸引走过来</li>
 *   <li>不受无为转变影响（变形目标排除）、免疫蛇发女妖石化（由 GorgonImmunityHandler 处理）</li>
 *   <li>秒杀掉落：被一击伤害 ≥ 最大生命击杀时掉落 拉莱耶的呼唤 ×1 + 15 经验</li>
 *   <li>售卖：6 槽位（咒具×2 / 咒术水晶×2 / 旧日遗物×2），货币为格赫罗斯残骸/矿石</li>
 *   <li>客户端套用玩家模型 + momo_common 贴图；语音走本模组自注册音效（占位文件可覆盖）</li>
 * </ul>
 */
public class MomoMerchant extends PathfinderMob {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("TinkersNewlife");

    // ===== 状态 =====
    private static final int S_IDLE = 0;
    private static final int S_ENGAGE = 1;
    private static final int S_SWEEP_WAIT = 2;   // 横斩后等 0.5s 再竖劈
    private static final int S_BACKOFF = 3;      // 打完一套拉远
    private static final int S_COUNTER = 4;      // 格挡成功后连斩 3 刀
    private static final int S_LEAP_UP = 5;      // 高高跃起
    private static final int S_ULT = 6;          // 乱蝶大招
    private static final int S_FLEE = 7;         // ≤5% 血逃跑

    private static final double REACH = 3.0;
    private static final int SWEEP_WAIT_TICKS = 10;
    private static final int BACKOFF_TICKS = 26;
    private static final int COMBO_COOLDOWN = 30;
    private static final int BLOCK_WINDOW = 20;      // 受击后 1s 格挡窗口
    private static final int LEAP_UP_TICKS = 10;
    private static final int ULT_INTERVAL = 12;
    private static final float[] ULT_MULTIPLIERS = {0.8f, 0.8f, 1.2f, 0.9f, 2.0f};
    private static final float[] COUNTER_MULTIPLIERS = {0.6f, 0.8f, 1.0f};

    /** 自然刷新最大游走半径 */
    private static final double WANDER_RADIUS = 20.0;
    /** 亡灵狩猎间隔 / 概率 / 半径 */
    private static final int HUNT_INTERVAL = 500;
    private static final double HUNT_CHANCE = 0.1;
    private static final double HUNT_RADIUS = 10.0;
    /** 空闲移动速度 = 攻击快速接近(1.35) 的 2/3（游荡 / 被货币吸引共用） */
    private static final float IDLE_MOVE_SPEED = 0.9F;
    /** A* 探索上限（防单次卡顿） */
    private static final int ASTAR_MAX_EXPAND = 700;

    // ===== 雇佣 / 歌唱 / 进食 =====
    /** 雇佣时长：一个游戏日（24000 tick），到期"回来找你" */
    private static final long HIRE_DURATION_TICKS = 24000;
    /** 歌唱退后时长 / 歌唱持续 10s / 冷却 120s */
    private static final int SONG_BACKOFF_TICKS = 20;
    private static final int SONG_DURATION_TICKS = 200;
    private static final int SONG_COOLDOWN_TICKS = 2400;
    /** 雇主血量低于 60% 触发歌唱 */
    private static final double EMPLOYER_LOW_HP_RATIO = 0.6;
    /** 再生 III = amplifier 2 */
    private static final int SONG_REGEN_AMPLIFIER = 2;
    /** 单次进食持续 2s = 40 tick；每 100 tick 判定一次 30% 概率开始进食 */
    private static final int EAT_DURATION_TICKS = 40;
    private static final int EAT_CHECK_INTERVAL = 100;
    private static final double EAT_CHANCE = 0.3;
    /** 雇佣到期返回雇主后，留给雇主续雇的宽限（拒绝续雇则到点自然消失；仅自然刷新的墨默） */
    private static final int RETURN_GRACE_TICKS = 400;
    /** 雇佣模式基础攻击 20（未雇佣为 50）；斩杀阈值 <10 血 */
    private static final float HIRED_BASE_ATTACK = 20.0F;
    private static final float EXECUTE_HP = 10.0F;
    /** 雇主距离超过 50 格 → 直接传送到雇主身边 */
    private static final double EMPLOYER_TELEPORT_DIST = 50.0;

    // ===== 通用应急：伤害吟唱 / 低血大斩杀 =====
    /** 5s(100tick) 窗口内累计受伤 > 半血 → 传送安全位并吟唱 1s，记住伤害类型，60s 内对应类型抗性 +60% */
    private static final int DMG_WINDOW_TICKS = 100;
    private static final double CHANT_DAMAGE_THRESHOLD = 0.5;
    private static final int CHANT_TICKS = 20;           // 吟唱 1s
    private static final int RESIST_TICKS = 1200;        // 抗性 60s
    private static final float RESIST_MULTIPLIER = 0.4F; // 受伤降为 40%（抗性提升 60%）
    /** 血量 <20% → 对 50 格内每个目标斩杀（雇主除外） */
    private static final double MASS_EXECUTE_RADIUS = 50.0;
    private static final double MASS_EXECUTE_HP = 0.2;
    private static final int MASS_EXECUTE_COOLDOWN = 600;
    /** 卡死检测：持续受击 ≥6s 且累计移动 ≤1 格 → 传送至目标身后 */
    private static final int STUCK_WINDOW_TICKS = 120;
    private static final double STUCK_MAX_MOVE = 1.0;
    private static final int STUCK_ESCAPE_COOLDOWN = 300;
    /** 对空跳斩：目标悬空(高于墨默>2.2格且3D距离>3.2)且水平20格内 → 蓄力3s后跳至头顶5段连斩 */
    private static final int AIR_CHARGE_TICKS = 60;          // 蓄力 3s
    private static final double AIR_RADIUS = 20.0;
    private static final double AIR_GAP = 2.2;
    private static final int AIR_COMBO_COOLDOWN = 400;
    private static final float[] AIR_HIT_MULTIPLIERS = {0.8f, 0.8f, 1.0f, 1.2f, 1.6f};

    // ===== 语音防重叠（自动解析 assets 内 ogg 时长，按类别最大时长做间隔） =====
    private static final class VoiceTimings {
        final float ambient, hurt, death, trade;
        VoiceTimings(float ambient, float hurt, float death, float trade) {
            this.ambient = ambient;
            this.hurt = hurt;
            this.death = death;
            this.trade = trade;
        }
    }

    private static final VoiceTimings VOICE_TIMINGS = loadVoiceTimings();

    private static VoiceTimings loadVoiceTimings() {
        float amb = maxOgg("entity/momo/momo_ambient1.ogg",
                "entity/momo/momo_ambient2.ogg", "entity/momo/momo_ambient3.ogg");
        float hurt = maxOgg("entity/momo/momo_hurt1.ogg", "entity/momo/momo_hurt2.ogg");
        float death = maxOgg("entity/momo/momo_death.ogg");
        float trade = maxOgg("entity/momo/momo_trade.ogg");
        return new VoiceTimings(amb, hurt, death, trade);
    }

    private static float maxOgg(String... paths) {
        float max = 0;
        for (String p : paths) {
            float s = oggSeconds(p);
            if (s > max) max = s;
        }
        return max;
    }

    /** 解析 OGG 时长（granule / 采样率），失败返回 0 */
    private static float oggSeconds(String resPath) {
        try (java.io.InputStream in = MomoMerchant.class.getResourceAsStream(
                "/assets/tinkersnewlife/sounds/" + resPath)) {
            if (in == null) return 0;
            byte[] data = in.readAllBytes();
            long maxGranule = 0;
            int rate = 44100;
            int i = 0;
            while (i + 27 <= data.length) {
                if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S') {
                    long gran = 0;
                    for (int k = 0; k < 8; k++) gran |= ((long) (data[i + 6 + k] & 0xFF)) << (8 * k);
                    if (gran > 0) maxGranule = gran;
                    int seg = data[i + 26] & 0xFF;
                    int payload = i + 27 + seg;
                    if (payload + 16 <= data.length && data[payload] == 1 && data[payload + 1] == 0x76) {
                        int r = 0;
                        for (int k = 0; k < 4; k++) r |= (data[payload + 12 + k] & 0xFF) << (8 * k);
                        if (r > 0) rate = r;
                    }
                    int total = 0;
                    for (int s = 0; s < seg; s++) total += data[i + 27 + s] & 0xFF;
                    i = payload + total;
                } else {
                    i++;
                }
            }
            return maxGranule > 0 && rate > 0 ? maxGranule / (float) rate : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** 上一次任意墨默语音预计结束的 tick（用于防重叠：间隔 ≥ 该类别最长语音时长） */
    private int lastVoiceEndTick = Integer.MIN_VALUE / 2;

    private boolean voiceReady() {
        return this.tickCount >= lastVoiceEndTick;
    }

    private void voicePlayed(float seconds) {
        this.lastVoiceEndTick = this.tickCount + (int) (seconds * 20f) + 10;
    }

    private int state = S_IDLE;
    private int stateTimer = 0;
    private int comboCooldown = 0;
    private int counterIndex = 0;
    private int counterTimer = 0;
    private int ultIndex = 0;
    private int ultTimer = 0;
    private int blockWindowUntil = -1;
    private boolean leapUsed = false;
    private boolean fleeTriggered = false;
    private int fleeTimer = 0;
    private int regenTick = 0;
    private int aggroPruneTick = 0;

    // ===== 商人行为 =====
    /** 是否为自然刷新（满月）产生的：白天到来时消失；刷怪蛋为 false 常驻 */
    private boolean naturalSpawn = false;
    private boolean dayDespawnDone = false;
    /** 生成点（游走锚点） */
    private BlockPos homePos = null;
    private int wanderTimer = 0;
    private int huntTimer = 0;
    private int ambientVoiceTimer = 0;
    private int wardenProbeTimer = 0;
    private int currencyProbeTimer = 0;

    // ===== A* 空闲寻路状态（游荡 / 货币吸引） =====
    private List<BlockPos> path = new ArrayList<>();
    private BlockPos pathGoalCell = null;
    /** 当前路径属于货币吸引（true）还是游走（false）；清路径时按归属区分 */
    private boolean pathIsLure = false;
    private ItemEntity lureTarget = null;
    private Vec3 lastMovePos = null;
    private int noProgressTicks = 0;
    /** 交易成功语音（空闲2）防重叠 */
    private int tradeSuccessVoiceEnd = Integer.MIN_VALUE / 2;

    // ===== 雇佣 / 歌唱 / 进食 状态 =====
    private boolean hired = false;        // 是否处于雇佣期
    private UUID employerId = null;
    private long hireUntilTick = -1;      // 雇佣到期时刻（gameTime），= 雇佣时 + 24000
    private long offerDay = -1;           // 商品批次对应的 dayCount（每天刷新一批）
    /** 雇佣到期返回雇主后的续雇宽限倒计时（自然刷新版：拒绝续雇则到点随天亮消失） */
    private int returnGraceTicks = 0;
    private boolean singing = false;
    private int songBackoffTicks = 0;
    private int songTicks = 0;
    private int songCooldown = 0;
    /** 进食：剩余 tick（2s=40）与当前食物 */
    private int eatTicks = 0;
    private int eatCheckCooldown = 0;
    private Item eatFood = null;
    private java.util.List<Item> cachedFoods = null;

    // ===== 雇佣战斗（独立 AI）状态 =====
    private int hiredComboPhase = 0;   // 0 接近/起手 | 1 等 0.5s 第二刀 | 2 拉远
    private int hiredComboTimer = 0;
    private int hiredAttackCooldown = 0;
    private boolean finisherActive = false;
    private LivingEntity finisherTarget = null;
    private int finisherIndex = 0;
    private int finisherTimer = 0;

    // ===== 通用应急状态 =====
    private static final class DamageHit {
        final int tick;
        final float amount;
        final String type;
        DamageHit(int tick, float amount, String type) {
            this.tick = tick;
            this.amount = amount;
            this.type = type;
        }
    }

    private final List<DamageHit> dmgWindow = new ArrayList<>();
    private boolean chantRequested = false;
    private int chantTicks = 0;
    private java.util.Set<String> chantTypes = null;   // 吟唱完成时记住的伤害类型
    private long resistUntilTick = 0;
    private final java.util.Set<String> resistTypes = new HashSet<>();
    private boolean execBusy = false;
    private final List<java.util.UUID> execQueue = new ArrayList<>();
    private int execStage = 0;   // 0 瞬移 | 1..3 连斩
    private int execTimer = 0;
    private long execCooldownUntil = 0;

    /** 灼烧清锁节拍（狱焰等灼烧类减速/定身效果会被周期性清除，避免墨默被烧到停住） */
    private int fireCleanseTick = 0;

    // ===== 卡死逃生（持续受击但几乎没移动 → 传送目标身后） =====
    private Vec3 prevTickPos = null;
    private Vec3 stuckCheckPos = null;
    private int stuckCheckTicks = 0;
    private double stuckMoved = 0;
    private long lastDamageAtTick = -1000;
    private long stuckEscapeCooldown = 0;

    // ===== 对空跳斩（蓄力 → 头顶 5 段连斩） =====
    private boolean airComboActive = false;
    private int airChargeTicks = 0;
    private int airHitIndex = 0;
    private int airHitTimer = 0;
    private long airComboCooldown = 0;

    /** 近期攻击过她的实体（反击/大招只打这些人，不伤及无辜） */
    private final Set<UUID> aggroSet = new HashSet<>();

    private ItemStack scytheStack = ItemStack.EMPTY;

    /** 售卖槽位（6 个，跨存档持久化在实体 NBT） */
    public record Offer(ItemStack result, int price) {}

    private final List<Offer> offers = new ArrayList<>();

    public MomoMerchant(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    // ===== 进食动画同步标记（客户端据此收起主手战镰） =====
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_EATING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MomoMerchant.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_EATING, false);
    }

    /** 进食中（客户端据此收起主手战镰） */
    public boolean isEating() {
        return this.entityData.get(DATA_EATING);
    }

    private void setEatingFlag(boolean eating) {
        this.entityData.set(DATA_EATING, eating);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.ATTACK_DAMAGE, 50.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    // ============================================================
    //  生命周期 / 持久化
    // ============================================================

    @Override
    protected void registerGoals() {
        // 受击反击：把攻击者设为目标（玩家/怪物/监守者均可）
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    public void setNaturalSpawn(boolean natural) {
        this.naturalSpawn = natural;
    }

    public boolean isNaturalSpawn() {
        return naturalSpawn;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, net.minecraft.world.DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag tag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);
        this.setPersistenceRequired();
        if (homePos == null) homePos = this.blockPosition();
        equipScythe();
        ensureOffers();
        return data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("MomoState", state);
        tag.putBoolean("MomoLeapUsed", leapUsed);
        tag.putBoolean("MomoNatural", naturalSpawn);
        tag.putBoolean("MomoHired", hired);
        if (employerId != null) {
            tag.putUUID("MomoEmployer", employerId);
        }
        tag.putLong("MomoHireUntil", hireUntilTick);
        tag.putLong("MomoOfferDay", offerDay);
        if (homePos != null) {
            tag.putIntArray("MomoHome", new int[]{homePos.getX(), homePos.getY(), homePos.getZ()});
        }
        ListTag offerList = new ListTag();
        for (Offer offer : offers) {
            CompoundTag entry = new CompoundTag();
            entry.put("Result", offer.result().save(new CompoundTag()));
            entry.putInt("Price", offer.price());
            offerList.add(entry);
        }
        tag.put("MomoOffers", offerList);
        if (!scytheStack.isEmpty()) {
            tag.put("MomoScythe", scytheStack.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        state = tag.getInt("MomoState");
        leapUsed = tag.getBoolean("MomoLeapUsed");
        naturalSpawn = tag.getBoolean("MomoNatural");
        hired = tag.getBoolean("MomoHired");
        if (tag.contains("MomoEmployer")) {
            employerId = tag.getUUID("MomoEmployer");
        }
        hireUntilTick = tag.getLong("MomoHireUntil");
        offerDay = tag.getLong("MomoOfferDay");
        if (tag.contains("MomoHome")) {
            int[] h = tag.getIntArray("MomoHome");
            if (h.length == 3) homePos = new BlockPos(h[0], h[1], h[2]);
        }
        offers.clear();
        if (tag.contains("MomoOffers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("MomoOffers", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                ItemStack result = ItemStack.of(entry.getCompound("Result"));
                if (!result.isEmpty()) {
                    offers.add(new Offer(result, entry.getInt("Price")));
                }
            }
        }
        if (tag.contains("MomoScythe")) {
            scytheStack = ItemStack.of(tag.getCompound("MomoScythe"));
        }
        if (!level().isClientSide) {
            this.setPersistenceRequired();
            equipScythe();
            ensureOffers();
        }
    }

    /** 主手拿格赫罗斯战镰（渲染可见）；材质用格赫罗斯残骸 */
    private void equipScythe() {
        if (level().isClientSide) return;
        if (!scytheStack.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, scytheStack);
            return;
        }
        try {
            ItemStack stack = new ItemStack(ModItems.WAR_SCYTHE.get());
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool != null) {
                slimeknights.tconstruct.library.materials.definition.IMaterial material = null;
                MaterialId want = new MaterialId(new ResourceLocation("tinkersnewlife", "gheloth_remains"));
                for (slimeknights.tconstruct.library.materials.definition.IMaterial m
                        : MaterialRegistry.getInstance().getAllMaterials()) {
                    if (m.getIdentifier().equals(want)) {
                        material = m;
                        break;
                    }
                }
                if (material == null) {
                    for (slimeknights.tconstruct.library.materials.definition.IMaterial m
                            : MaterialRegistry.getInstance().getAllMaterials()) {
                        if (m.getIdentifier().getNamespace().equals("tinkersnewlife")) {
                            material = m;
                            break;
                        }
                    }
                }
                if (material == null) return;
                MaterialVariant[] variants = new MaterialVariant[5];
                for (int i = 0; i < variants.length; i++) {
                    variants[i] = MaterialVariant.of(material);
                }
                tool.setMaterials(MaterialNBT.of(variants));
                tool.rebuildStats();
                scytheStack = tool.createStack();
                this.setItemSlot(EquipmentSlot.MAINHAND, scytheStack);
            }
        } catch (Exception ignored) {
            // 材质未加载等极端情况：空手也可（攻击力来自属性，不影响战斗）
        }
    }

    // ============================================================
    //  售卖槽位
    // ============================================================

    /** 保证 6 个售卖槽位已生成；每天（dayCount 变化）自动刷新一批新商品（无交易上限） */
    public void ensureOffers() {
        if (level().isClientSide) return;
        long day = currentDay();
        if (!offers.isEmpty() && offerDay == day) return;
        offers.clear();
        offerDay = day;
        RandomSource random = this.getRandom();

        // 1-2：咒具池任选两个（天逆鉾 / 狱门疆[未封印]；结界碎片不算咒具）
        List<Item> cursedTools = new ArrayList<>();
        cursedTools.add(ModItems.TIAN_NI_HUO.get());
        cursedTools.add(ModItems.GOURD_JAIL.get());
        Collections.shuffle(cursedTools, new java.util.Random(random.nextInt()));
        offers.add(new Offer(new ItemStack(cursedTools.get(0)), 10 + random.nextInt(11)));   // 10-20
        offers.add(new Offer(new ItemStack(cursedTools.get(1)), 10 + random.nextInt(11)));

        // 3-4：咒术水晶（roll 两个咒术）
        List<slimeknights.tconstruct.library.modifiers.ModifierId> techniques =
                new ArrayList<>(TechniqueHandler.getAllTechniqueIds());
        Collections.shuffle(techniques, new java.util.Random(random.nextInt()));
        for (int i = 0; i < 2 && i < techniques.size(); i++) {
            ItemStack crystal = ModifierCrystalItem.withModifier(techniques.get(i));
            if (!crystal.isEmpty()) {
                offers.add(new Offer(crystal, 40 + random.nextInt(21))); // 40-60
            }
        }
        while (offers.size() < 4) {
            offers.add(new Offer(new ItemStack(ModItems.GHELOTH_REMAINS.get()), 40));
        }

        // 5-6：旧日遗物任选两个（一次卖一组）
        List<Item> relics = new ArrayList<>();
        relics.add(ModItems.NICHOLAS_BLESSING.get());
        relics.add(ModItems.YELLOW_KING_REMNANT.get());
        relics.add(ModItems.RLYEH_CALL.get());
        relics.add(ModItems.ECHO_OF_THE_VOID.get());
        relics.add(ModItems.ASTRAL_ANCHOR.get());
        relics.add(ModItems.NEXUS_OF_SPACETIME.get());
        relics.add(ModItems.YOG_SOTHOTH_GATE_KEY.get());
        relics.add(ModItems.NYARLATHOTEP_DESIRE.get());
        relics.add(ModItems.DURANDAL_SHARD.get());
        Collections.shuffle(relics, new java.util.Random(random.nextInt()));
        offers.add(new Offer(new ItemStack(relics.get(0), relics.get(0).getMaxStackSize()), 5 + random.nextInt(9)));   // 5-13
        offers.add(new Offer(new ItemStack(relics.get(1), relics.get(1).getMaxStackSize()), 5 + random.nextInt(9)));
    }

    public List<Offer> getOffers() {
        return offers;
    }

    /** 槽位对应货币：0-3 格赫罗斯残骸，4-5 格赫罗斯矿石 */
    public static Item currencyForSlot(int slot) {
        return slot >= 4 ? ModItems.GHELOTH_ORE.get() : ModItems.GHELOTH_REMAINS.get();
    }

    public enum BuyResult { OK, NO_OFFER, INSUFFICIENT, TOO_FAR, DEAD }

    public enum HireResult { HIRED, ALREADY_HIRED, HIRED_BY_OTHER, NO_ITEM, TOO_FAR, DEAD }

    /** 玩家点击雇佣：支付 1 个拉莱耶的呼唤，雇佣一天。雇佣期间再次点击不扣费、直接拒绝，防止重复上交 */
    public HireResult hireFrom(ServerPlayer buyer) {
        if (level().isClientSide) return HireResult.DEAD;
        if (!this.isAlive() || this.isRemoved()) return HireResult.DEAD;
        if (buyer.distanceToSqr(this) > 8.0 * 8.0) return HireResult.TOO_FAR;
        // 已处于雇佣期（无论是否本人）：拒绝再次支付
        if (hired) {
            if (employerId != null && !employerId.equals(buyer.getUUID())) {
                return HireResult.HIRED_BY_OTHER;
            }
            return HireResult.ALREADY_HIRED;
        }
        if (countItem(buyer, ModItems.RLYEH_CALL.get()) < 1) return HireResult.NO_ITEM;
        consumeItem(buyer, ModItems.RLYEH_CALL.get(), 1);
        employerId = buyer.getUUID();
        hireUntilTick = this.level().getGameTime() + HIRE_DURATION_TICKS; // 雇佣一个游戏日
        hired = true;
        returnGraceTicks = 0; // 雇佣成功，取消"天亮消失"宽限
        this.setTarget(null);
        clearPath();
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.6, this.getZ(),
                    6, 0.3, 0.3, 0.3, 0.02);
        }
        return HireResult.HIRED;
    }

    /** 玩家点击交易：校验并执行购买（货币从玩家背包/副手扣除） */
    public BuyResult buyFrom(Player buyer, int slot) {
        if (level().isClientSide) return BuyResult.DEAD;
        if (!this.isAlive() || this.isRemoved()) return BuyResult.DEAD;
        if (buyer.distanceToSqr(this) > 8.0 * 8.0) return BuyResult.TOO_FAR;
        ensureOffers();
        if (slot < 0 || slot >= offers.size()) return BuyResult.NO_OFFER;
        Offer offer = offers.get(slot);
        Item currency = currencyForSlot(slot);
        if (countItem(buyer, currency) < offer.price()) return BuyResult.INSUFFICIENT;
        consumeItem(buyer, currency, offer.price());
        ItemStack give = offer.result().copy();
        if (!buyer.getInventory().add(give)) {
            buyer.drop(give, false);
        }
        return BuyResult.OK;
    }

    private static int countItem(Player p, Item item) {
        int total = 0;
        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) total += s.getCount();
        }
        if (p.getOffhandItem().is(item)) total += p.getOffhandItem().getCount();
        return total;
    }

    private static void consumeItem(Player p, Item item, int amount) {
        for (ItemStack s : p.getInventory().items) {
            if (amount <= 0) break;
            if (s.is(item)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
        if (amount > 0) {
            ItemStack off = p.getOffhandItem();
            if (off.is(item)) {
                int take = Math.min(amount, off.getCount());
                off.shrink(take);
            }
        }
    }

    // ============================================================
    //  交互：右键打开交易
    // ============================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.sidedSuccess(true);
        if (this.getTarget() == player) return InteractionResult.PASS; // 正在反击该玩家
        ensureOffers();
        com.mofengbaizhi.tinkersnewlife.network.PacketMomoOpen.sendTo((ServerPlayer) player, this);
        // 交易语音不与其他语音重叠（间隔 ≥ 语音时长）
        if (voiceReady()) {
            voicePlayed(VOICE_TIMINGS.trade);
            this.playSound(ModSounds.MOMO_TRADE.get(), 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(true);
    }

    /** 交易成功：播放固定"空闲2"语音（替代村民高兴声），只防与自身连续播放重叠 */
    public void playTradeSuccessSound() {
        if (level().isClientSide) return;
        if (this.tickCount < tradeSuccessVoiceEnd) return;
        tradeSuccessVoiceEnd = this.tickCount + (int) (VOICE_TIMINGS.ambient * 20f) + 10;
        this.playSound(ModSounds.MOMO_TRADE_SUCCESS.get(), 1.0F, 1.0F);
    }

    // ============================================================
    //  战斗辅助（伤害/范围）
    // ============================================================

    private float attackBase() {
        AttributeInstance attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null ? 50.0F : (float) attr.getValue();
    }

    /** 该实体是否为墨默的"敌对目标"（只攻击伤害过她的实体与其当前目标） */
    private boolean isAggroTarget(LivingEntity e) {
        if (e == this || e.isSpectator()) return false;
        if (e == this.getTarget()) return true;
        if (this.getLastHurtByMob() == e) return true;
        return aggroSet.contains(e.getUUID());
    }

    /** 面前扇形内的敌人 */
    private List<LivingEntity> sectorTargets(double radius, float halfAngleDeg) {
        AABB box = this.getBoundingBox().inflate(radius, 2.0, radius);
        List<LivingEntity> list = new ArrayList<>();
        Vec3 look = this.getLookAngle();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && isAggroTarget(e))) {
            Vec3 to = e.position().subtract(this.position()).normalize();
            double angle = Math.toDegrees(Math.acos(Math.min(1.0, look.dot(to))));
            if (this.distanceTo(e) <= radius && angle <= halfAngleDeg) {
                list.add(e);
            }
        }
        return list;
    }

    private void hurtAll(List<LivingEntity> targets, float multiplier, boolean isLeap) {
        if (this.level().isClientSide) return;
        float dmg = attackBase() * multiplier;
        for (LivingEntity e : targets) {
            if (e.isAlive()) {
                if (isLeap && e instanceof Player p) {
                    breakShield(p);
                }
                e.invulnerableTime = 0;
                applyHurt(e, dmg);
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, 0.15, 0.1, 0.15, 0);
                }
            }
        }
    }

    /** 破盾：打断玩家格挡并让盾牌进入冷却 */
    private void breakShield(Player p) {
        if (p.isBlocking()) {
            Item used = p.getUseItem().getItem();
            p.stopUsingItem();
            if (used != net.minecraft.world.item.Items.AIR) {
                p.getCooldowns().addCooldown(used, 100);
            }
        }
    }

    private void playSwingFx(float f) {
        this.swing(InteractionHand.MAIN_HAND);
        if (this.level() instanceof ServerLevel sl) {
            Vec3 pos = this.position().add(0, 1.2, 0);
            sl.sendParticles(f >= 1.5f ? ParticleTypes.CRIT : ParticleTypes.SWEEP_ATTACK,
                    pos.x, pos.y, pos.z, 8, 0.4, 0.3, 0.4, 0.05);
            sl.playSound(null, this.blockPosition(), f >= 1.5f ? SoundEvents.PLAYER_ATTACK_STRONG
                    : SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
    }

    // ============================================================
    //  服务端 AI（状态机，tick 驱动）
    // ============================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        tickServer();
    }

    private void tickServer() {
        if (homePos == null) homePos = this.blockPosition();

        // 自然刷新的墨默：白天到来时消失（雇佣中/到期返回后的续雇宽限内不消失；
        // 刷怪蛋召唤的常驻；拒绝续雇则宽限结束随天亮消失）
        if (naturalSpawn && !hired && returnGraceTicks <= 0) {
            long dayTime = level().getDayTime() % 24000;
            if (dayTime < 13000) {
                if (!dayDespawnDone && level() instanceof ServerLevel sl) {
                    dayDespawnDone = true;
                    sl.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.2, this.getZ(),
                            16, 0.4, 0.5, 0.4, 0.02);
                    sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.9F, 1.4F);
                }
                this.discard();
                return;
            }
        }

        // 续雇宽限倒计时（自然刷新版到期未续雇 → 到点天亮消失）
        if (returnGraceTicks > 0 && !hired) {
            returnGraceTicks--;
        }

        // 再生 VIII（常驻，覆盖旧版再生 V）
        if (++regenTick % 20 == 0) {
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 7, false, false));
        }

        // 灼烧清锁：狱焰等灼烧类移动限制效果会让她停住——周期性清除（着火时连通用减速也清）
        if (++fireCleanseTick % 10 == 0) {
            cleanseMovementLockEffects();
        }

        // 卡死逃生：持续受击但几乎没移动 → 传送至目标身后（不在吟唱/斩杀/歌唱/逃跑中时）
        if (chantTicks <= 0 && !execBusy && !finisherActive && !singing && !fleeTriggered) {
            tickStuckEscape();
        }

        // 蜘蛛式爬墙
        tickWallClimb();

        // 水下：溺尸式憋气 + 游泳
        tickWaterSwim();

        // 仇恨清理
        if (++aggroPruneTick >= 100) {
            aggroPruneTick = 0;
            if (!aggroSet.isEmpty()) {
                aggroSet.removeIf(uuid -> {
                    Entity e = ((ServerLevel) this.level()).getEntity(uuid);
                    return e == null || !e.isAlive();
                });
            }
        }

        // 监守者可索敌墨默：附近 16 格内有监守者 → 让它盯上她（她也会反击）
        if (++wardenProbeTimer >= 40) {
            wardenProbeTimer = 0;
            for (Warden warden : this.level().getEntitiesOfClass(Warden.class,
                    this.getBoundingBox().inflate(16.0), e -> e.isAlive())) {
                if (warden.getTarget() != this) {
                    warden.setTarget(this);
                }
            }
        }

        // 下界亚波伦"死亡箭雨"格挡：Apollyon 快速连射期间自动举盾（箭矢被格挡 → 整段箭雨全免）
        tickBarrageGuard();

        // 低血量逃跑
        if (!fleeTriggered && this.getHealth() <= this.getMaxHealth() * 0.05f && state != S_LEAP_UP && state != S_ULT) {
            fleeTriggered = true;
            fleeTimer = 60;
            this.setTarget(null);
            state = S_FLEE;
            stateTimer = 0;
        }
        if (fleeTriggered) {
            tickFlee();
            return;
        }

        // 通用应急：伤害吟唱 / 低血大斩杀（雇佣与未雇佣通用）
        if (tickResponseCore()) {
            return;
        }

        // 雇佣模式：独立 AI（跟随雇主/清亡灵/两刀必中/斩杀/歌唱/进食）
        if (hired) {
            tickHiredAI();
            return;
        }

        // 有目标但没有进入交战 → 自动开战（玩家/怪物/监守者攻击后都会走到这里）
        if (state == S_IDLE) {
            LivingEntity t = this.getTarget();
            if (t != null && t.isAlive()) {
                stopEating(); // 开战打断进食
                state = S_ENGAGE;
            }
        }

        // 目标消失 → 回到中立
        if (state != S_FLEE && state != S_ULT) {
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()) {
                if (state != S_IDLE) {
                    state = S_IDLE;
                    stateTimer = 0;
                    this.getNavigation().stop();
                }
                tickIdle();
                return;
            }
        }

        // 格挡窗口倒计时
        if (blockWindowUntil > 0 && this.tickCount > blockWindowUntil) {
            blockWindowUntil = -1;
        }

        if (comboCooldown > 0) comboCooldown--;

        switch (state) {
            case S_IDLE -> tickIdle();
            case S_ENGAGE -> tickEngage();
            case S_SWEEP_WAIT -> tickSweepWait();
            case S_BACKOFF -> tickBackoff();
            case S_COUNTER -> tickCounter();
            case S_LEAP_UP -> tickLeapUp();
            case S_ULT -> tickUlt();
            default -> state = S_IDLE;
        }
    }

    /** 商人声音（无战斗/无目标时偶尔低语；不与任意语音重叠；雇佣状态下同样会说话） */
    private void tickAmbientVoice() {
        if (--ambientVoiceTimer > 0) return;
        ambientVoiceTimer = 200 + this.random.nextInt(400);
        if (voiceReady() && this.getTarget() == null && !this.isInWater() && !this.isDeadOrDying()) {
            voicePlayed(VOICE_TIMINGS.ambient);
            this.playSound(ModSounds.MOMO_AMBIENT.get(), 0.9F, 1.0F);
        }
    }

    /** 空闲主逻辑：货币吸引优先；否则默认持续游走（近身玩家时才站定待客） */
    private void tickIdle() {
        tickAmbientVoice();
        tickEatIfIdle();

        boolean moving = tickCurrencyLure();
        if (!moving) {
            // 雇佣中：雇主拉太远 → 跟上雇主（保镖）
            if (hired) {
                ServerPlayer boss = getEmployer();
                if (boss != null && boss.isAlive() && boss.level() == this.level()) {
                    if (this.distanceToSqr(boss) > 14.0 * 14.0) {
                        if (this.distanceToSqr(boss) > 64.0 * 64.0 && this.tickCount % 200 == 0) {
                            this.moveTo(boss.getX(), boss.getY(), boss.getZ(), this.getYRot(), this.getXRot());
                        } else {
                            this.getNavigation().moveTo(boss, 1.15);
                        }
                        return;
                    }
                }
            }
            Player nearest = this.level().getNearestPlayer(this, 16.0);
            if (nearest != null && this.distanceTo(nearest) <= 2.5) {
                // 近身（可交互距离）：站定看玩家，方便交易
                this.getLookControl().setLookAt(nearest, 10.0F, 10.0F);
            } else {
                // 周围有玩家也默认游走；附近无玩家时才触发亡灵狩猎
                tickWanderPath();
                if (nearest == null) {
                    tickUndeadHunt();
                }
            }
        }
        // 复位逃跑标记
        if (this.getHealth() >= this.getMaxHealth() * 0.15f) {
            fleeTriggered = false;
            leapUsed = false;
            aggroSet.clear();
        }
    }

    /** 20 格内格赫罗斯残骸/矿石 → A* 走过去（不拾取）；返回是否还在移动 */
    private boolean tickCurrencyLure() {
        if (++currencyProbeTimer < 10) {
            // 探测冷却中：正在追货币 → 继续沿路径走；否则不动（不动游走路径）
            if (lureTarget != null && !path.isEmpty() && pathIsLure) {
                return followPath(IDLE_MOVE_SPEED);
            }
            return false;
        }
        currencyProbeTimer = 0;
        ItemEntity target = null;
        double best = WANDER_RADIUS * WANDER_RADIUS;
        for (ItemEntity ie : this.level().getEntitiesOfClass(ItemEntity.class,
                this.getBoundingBox().inflate(WANDER_RADIUS), e -> e.isAlive() && !e.getItem().isEmpty())) {
            ItemStack stack = ie.getItem();
            if (!stack.is(ModItems.GHELOTH_REMAINS.get()) && !stack.is(ModItems.GHELOTH_ORE.get())) continue;
            double d = this.distanceToSqr(ie);
            if (d < best) {
                best = d;
                target = ie;
            }
        }
        if (target == null) {
            // 没有货币：只清货币路径，绝不清正在走的游走路径
            if (pathIsLure) {
                clearPath();
            }
            lureTarget = null;
            return false;
        }
        // 已到跟前：停下看货币主人
        if (this.distanceTo(target) <= 1.6) {
            clearPath();
            Player p = this.level().getNearestPlayer(this, 8.0);
            if (p != null) {
                this.getLookControl().setLookAt(p, 10.0F, 10.0F);
            }
            if (this.random.nextInt(100) == 0 && voiceReady()) {
                voicePlayed(VOICE_TIMINGS.ambient);
                this.playSound(ModSounds.MOMO_AMBIENT.get(), 0.8F, 1.0F);
            }
            return false;
        }
        if (lureTarget != target) {
            lureTarget = target;
            clearPath();
        }
        BlockPos goal = groundCell(target.blockPosition());
        if (goal == null) {
            clearPath();
            return false;
        }
        if (path.isEmpty() || !goal.equals(pathGoalCell)) {
            List<BlockPos> p = aStarPath(this.blockPosition(), goal);
            if (p == null) {
                clearPath();
                return false;
            }
            path = p;
            pathGoalCell = goal;
            pathIsLure = true;
        }
        return followPath(IDLE_MOVE_SPEED);
    }

    /** 无玩家时在生成点 20 格内游走（A* 寻路）；返回是否在移动 */
    private boolean tickWanderPath() {
        if (!path.isEmpty()) {
            boolean moving = followPath(IDLE_MOVE_SPEED);
            if (!moving) {
                wanderTimer = 100 + this.random.nextInt(140); // 走完一段歇一会
            }
            return moving;
        }
        if (--wanderTimer > 0) return false;
        if (homePos == null) homePos = this.blockPosition();
        // 锚点：附近有玩家 → 在玩家周围小范围踱步；无玩家 → 生成点 20 格内游走
        Player near = this.level().getNearestPlayer(this, 16.0);
        BlockPos center = near != null ? near.blockPosition() : homePos;
        double radius = near != null ? 6.0 : WANDER_RADIUS;
        for (int tries = 0; tries < 8; tries++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double r = this.random.nextDouble() * radius;
            BlockPos col = new BlockPos(
                    center.getX() + (int) Math.round(Math.cos(angle) * r),
                    this.blockPosition().getY(), // 以当前所在高度为基准
                    center.getZ() + (int) Math.round(Math.sin(angle) * r));
            // 目标点被方块覆盖无法抵达 → y+1 继续向上，直到找到可抵达点
            BlockPos goal = ascendToReachable(col);
            if (goal == null) continue;
            List<BlockPos> p = aStarPath(this.blockPosition(), goal);
            if (p != null && !p.isEmpty()) {
                path = p;
                pathGoalCell = goal;
                pathIsLure = false;
                return true;
            }
        }
        wanderTimer = 60; // 找不到路，稍后再试
        return false;
    }

    /** 从该列 base 高度开始：若被方块覆盖（不可站立）则 y+1 向上，直到找到可抵达格 */
    private BlockPos ascendToReachable(BlockPos base) {
        int maxY = Math.min(base.getY() + 12, this.level().getMaxBuildHeight() - 3);
        for (int y = Math.max(base.getY(), this.level().getMinBuildHeight() + 2); y <= maxY; y++) {
            BlockPos cell = new BlockPos(base.getX(), y, base.getZ());
            if (isWalkableCell(cell)) {
                return cell;
            }
        }
        return null;
    }

    // ============================================================
    //  A* 地面寻路（游荡 / 货币吸引专用；战斗仍用原版导航快速接近）
    // ============================================================

    private void clearPath() {
        path.clear();
        pathGoalCell = null;
        lureTarget = null;
        pathIsLure = false;
        lastMovePos = null;
        noProgressTicks = 0;
    }

    /** 该格可作为站立格：脚下是完整方块、身体两格内无碰撞、非流体 */
    private boolean isWalkableCell(int x, int y, int z) {
        if (y < this.level().getMinBuildHeight() + 1 || y > this.level().getMaxBuildHeight() - 3) return false;
        BlockPos below = new BlockPos(x, y - 1, z);
        if (!this.level().getBlockState(below).isCollisionShapeFullBlock(this.level(), below)) return false;
        BlockPos here = new BlockPos(x, y, z);
        if (!this.level().getFluidState(here).isEmpty() || !this.level().getFluidState(here.above()).isEmpty()) {
            return false;
        }
        AABB body = new AABB(x + 0.01, y, z + 0.01, x + 0.99, y + 1.9, z + 0.99);
        return this.level().noCollision(body);
    }

    private boolean isWalkableCell(BlockPos p) {
        return isWalkableCell(p.getX(), p.getY(), p.getZ());
    }

    /** 在 p 所在列上下找到可站立格（先下探再上探），找不到返回 null */
    private BlockPos groundCell(BlockPos p) {
        int y = Math.max(this.level().getMinBuildHeight() + 2, p.getY());
        for (int dy = 0; dy <= 6 && y - dy >= this.level().getMinBuildHeight() + 2; dy++) {
            if (isWalkableCell(p.getX(), y - dy, p.getZ())) {
                return new BlockPos(p.getX(), y - dy, p.getZ());
            }
        }
        for (int dy = 1; dy <= 4 && y + dy <= this.level().getMaxBuildHeight() - 3; dy++) {
            if (isWalkableCell(p.getX(), y + dy, p.getZ())) {
                return new BlockPos(p.getX(), y + dy, p.getZ());
            }
        }
        return null;
    }

    /** 沿当前 A* 路径前进（MoveControl 平滑转向）；返回是否仍在移动 */
    private boolean followPath(float speed) {
        if (path.isEmpty()) return false;
        if (lastMovePos != null) {
            double moved = lastMovePos.distanceToSqr(this.position());
            if (moved < 0.02 * 0.02) {
                if (++noProgressTicks > 60) {
                    clearPath();
                    return false;
                }
            } else {
                noProgressTicks = 0;
            }
        }
        lastMovePos = this.position();
        while (!path.isEmpty()) {
            BlockPos wp = path.get(0);
            double dx = wp.getX() + 0.5 - this.getX();
            double dz = wp.getZ() + 0.5 - this.getZ();
            if (dx * dx + dz * dz < 0.35 * 0.35) {
                path.remove(0);
                continue;
            }
            // 下一节点高一格：接近时起跳
            if (wp.getY() > this.getY() + 0.1 && this.onGround()) {
                this.getJumpControl().jump();
            }
            this.getMoveControl().setWantedPosition(wp.getX() + 0.5, this.getY(), wp.getZ() + 0.5, speed);
            return true;
        }
        return false;
    }

    private static final class ANode {
        final long key;
        final BlockPos pos;
        final double g;
        final double f;
        final long came;
        ANode(long key, BlockPos pos, double g, double f, long came) {
            this.key = key;
            this.pos = pos;
            this.g = g;
            this.f = f;
            this.came = came;
        }
    }

    private static double hCost(BlockPos a, BlockPos b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dz = Math.abs(a.getZ() - b.getZ());
        int dy = Math.abs(a.getY() - b.getY());
        return Math.max(dx, dz) + (1.41421356 - 1.0) * Math.min(dx, dz) + dy;
    }

    /** A*：从 start（站立格）到 goal（站立格），返回路径节点（不含起点）；失败返回 null */
    private List<BlockPos> aStarPath(BlockPos start, BlockPos goal) {
        if (!isWalkableCell(goal)) return null;
        java.util.HashMap<Long, ANode> open = new java.util.HashMap<>();
        java.util.HashSet<Long> closed = new java.util.HashSet<>();
        java.util.PriorityQueue<ANode> queue = new java.util.PriorityQueue<>(
                (a, b) -> a.f == b.f ? Double.compare(a.g, b.g) : Double.compare(a.f, b.f));
        long startKey = start.asLong();
        ANode s = new ANode(startKey, start, 0, hCost(start, goal), 0);
        open.put(startKey, s);
        queue.add(s);
        int expanded = 0;
        while (!queue.isEmpty()) {
            ANode cur = queue.poll();
            if (closed.contains(cur.key)) continue;
            if (cur.pos.equals(goal)) {
                // 回溯路径
                List<BlockPos> result = new ArrayList<>();
                ANode node = cur;
                while (node.came != 0) {
                    result.add(node.pos);
                    ANode prev = open.get(node.came);
                    if (prev == null) break;
                    node = prev;
                }
                Collections.reverse(result);
                return result;
            }
            closed.add(cur.key);
            if (++expanded > ASTAR_MAX_EXPAND) return null;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    double step = (dx != 0 && dz != 0) ? 1.41421356 : 1.0;
                    // 同高度 → 上跳一级 → 下走一级
                    for (int dy : new int[]{0, 1, -1}) {
                        BlockPos nb = new BlockPos(cur.pos.getX() + dx, cur.pos.getY() + dy, cur.pos.getZ() + dz);
                        if (!isWalkableCell(nb)) continue;
                        long key = nb.asLong();
                        if (closed.contains(key)) continue;
                        double g = cur.g + step;
                        ANode old = open.get(key);
                        if (old != null && old.g <= g) continue;
                        ANode next = new ANode(key, nb, g, g + hCost(nb, goal), cur.key);
                        open.put(key, next);
                        queue.add(next);
                    }
                }
            }
        }
        return null;
    }

    /** 每 500tick 10% 概率：索敌 10 格内一只亡灵并击杀（进入交战状态由状态机处理） */
    private void tickUndeadHunt() {
        if (++huntTimer < HUNT_INTERVAL) return;
        huntTimer = 0;
        if (this.random.nextFloat() >= HUNT_CHANCE) return;
        List<Mob> undead = this.level().getEntitiesOfClass(Mob.class,
                this.getBoundingBox().inflate(HUNT_RADIUS),
                e -> e.isAlive() && e != this && e.getMobType() == MobType.UNDEAD);
        if (undead.isEmpty()) return;
        Mob prey = undead.get(this.random.nextInt(undead.size()));
        if (this.getTarget() == null) {
            clearPath();
            this.setTarget(prey);
        }
    }

    private void tickEngage() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            state = S_IDLE;
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double dist = this.distanceToSqr(target);
        if (dist <= REACH * REACH) {
            if (comboCooldown <= 0) {
                // 横斩（80%）→ 0.5s 后竖劈（180%）
                this.getNavigation().stop();
                List<LivingEntity> hits = sectorTargets(2.4, 70);
                hurtAll(hits, 0.8f, false);
                playSwingFx(0.8f);
                state = S_SWEEP_WAIT;
                stateTimer = SWEEP_WAIT_TICKS;
                return;
            }
            this.getNavigation().stop();
            return;
        }
        if (dist > 48.0 * 48.0) {
            this.setTarget(null);
            state = S_IDLE;
            return;
        }
        // 快速接近
        this.getNavigation().moveTo(target, chaseSpeedWithBlock(1.35));

        // 半血触发：高高跃起 → 跳劈（300%，破盾）→ 乱蝶大招
        if (!leapUsed && this.getHealth() <= this.getMaxHealth() * 0.5f && dist < 18.0 * 18.0) {
            leapUsed = true;
            state = S_LEAP_UP;
            stateTimer = LEAP_UP_TICKS;
            this.getNavigation().stop();
        }
    }

    private void tickSweepWait() {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive()) {
            this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        }
        if (--stateTimer <= 0) {
            // 竖劈（180%）
            List<LivingEntity> hits = sectorTargets(2.6, 70);
            hurtAll(hits, 1.8f, false);
            playSwingFx(1.8f);
            state = S_BACKOFF;
            stateTimer = BACKOFF_TICKS;
        }
    }

    private void tickBackoff() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            comboCooldown = 20;
            state = S_ENGAGE;
            return;
        }
        if (--stateTimer <= 0 || this.distanceToSqr(target) > 8.0 * 8.0) {
            comboCooldown = COMBO_COOLDOWN;
            state = S_ENGAGE;
            return;
        }
        Vec3 away = this.position().subtract(target.position()).normalize();
        Vec3 goal = this.position().add(away.scale(6.0));
        this.getNavigation().moveTo(goal.x, this.getY(), goal.z, 1.2);
    }

    /** 格挡成功后的连斩（60/80/100%） */
    private void tickCounter() {
        LivingEntity attacker = getLastBlockedBy();
        if (attacker == null || !attacker.isAlive() || this.distanceToSqr(attacker) > 5.0 * 5.0) {
            state = S_ENGAGE;
            counterIndex = 0;
            if (this.getTarget() == null) this.setTarget(attacker);
            return;
        }
        this.getLookControl().setLookAt(attacker, 30.0F, 30.0F);
        if (this.distanceToSqr(attacker) > 2.4 * 2.4) {
            this.getNavigation().moveTo(attacker, chaseSpeedWithBlock(1.4));
            return;
        }
        this.getNavigation().stop();
        if (--counterTimer <= 0) {
            float mult = COUNTER_MULTIPLIERS[Math.min(counterIndex, COUNTER_MULTIPLIERS.length - 1)];
            applyHurt(attacker, attackBase() * mult);
            playSwingFx(mult);
            counterIndex++;
            if (counterIndex >= 3) {
                counterIndex = 0;
                comboCooldown = COMBO_COOLDOWN;
                state = S_ENGAGE;
            } else {
                counterTimer = 5;
            }
        }
    }

    private void tickLeapUp() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            state = S_IDLE;
            return;
        }
        if (stateTimer == LEAP_UP_TICKS) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, 1.4, 0));
            this.playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.6F);
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--stateTimer <= 0) {
            // 直接跳向目标位置落地 → 跳劈（300%，破盾）
            this.setDeltaMovement(Vec3.ZERO);
            this.fallDistance = 0;
            Vec3 pos = target.position();
            this.moveTo(pos.x, pos.y, pos.z, this.getYRot(), this.getXRot());
            this.fallDistance = 0;
            AABB aabb = this.getBoundingBox().inflate(3.0, 1.5, 3.0);
            List<LivingEntity> hits = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
                    e -> e.isAlive() && isAggroTarget(e));
            hurtAll(hits, 3.0f, true);
            playSwingFx(3.0f);
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY() + 1.0, this.getZ(), 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 24, 1.6, 0.6, 1.6, 0.1);
                sl.playSound(null, this.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 0.8F);
            }
            // 乱蝶大招
            ultIndex = 0;
            ultTimer = 10;
            state = S_ULT;
        }
    }

    private void tickUlt() {
        this.getNavigation().stop();
        if (--ultTimer > 0) return;
        if (ultIndex >= ULT_MULTIPLIERS.length) {
            comboCooldown = 60;
            state = this.getTarget() != null && this.getTarget().isAlive() ? S_ENGAGE : S_IDLE;
            return;
        }
        AABB aabb = this.getBoundingBox().inflate(4.0, 2.0, 4.0);
        List<LivingEntity> hits = this.level().getEntitiesOfClass(LivingEntity.class, aabb,
                e -> e.isAlive() && isAggroTarget(e));
        hurtAll(hits, ULT_MULTIPLIERS[ultIndex], false);
        if (!hits.isEmpty()) playSwingFx(ULT_MULTIPLIERS[ultIndex]);
        ultIndex++;
        ultTimer = ULT_INTERVAL;
    }

    private void tickFlee() {
        this.getNavigation().stop();
        if (--fleeTimer <= 0 || this.getHealth() >= this.getMaxHealth() * 0.15f) {
            fleeTriggered = false;
            this.setTarget(null);
            state = S_IDLE;
            return;
        }
        LivingEntity runner = this.getLastHurtByMob();
        if (runner == null || !runner.isAlive()) {
            Player p = this.level().getNearestPlayer(this, 24.0);
            if (p != null) runner = p;
        }
        if (runner != null) {
            Vec3 away = this.position().subtract(runner.position()).normalize();
            Vec3 goal = this.position().add(away.scale(10.0));
            this.getNavigation().moveTo(goal.x, this.getY(), goal.z, 1.6);
        }
    }

    // ============================================================
    //  雇佣 / 歌唱 / 进食
    // ============================================================

    public boolean isHired() {
        return hired;
    }

    /** 雇主显示名（GUI 用） */
    public String employerDisplayName() {
        ServerPlayer boss = getEmployer();
        if (boss != null) return boss.getName().getString();
        return "";
    }

    private long currentDay() {
        return this.level().getDayTime() / 24000;
    }

    @Nullable
    public ServerPlayer getEmployer() {
        if (employerId == null) return null;
        if (!(this.level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(employerId);
    }

    /** 雇主当前威胁：攻击雇主的实体，或雇主正在攻击的非玩家生物（24 格内） */
    @Nullable
    private LivingEntity threatOfEmployer(ServerPlayer boss) {
        LivingEntity threat = boss.getLastHurtByMob();
        if (threat != null && threat.isAlive() && threat != this && boss.distanceToSqr(threat) <= 24.0 * 24.0) {
            return threat;
        }
        LivingEntity bossTarget = boss.getLastHurtMob();
        if (bossTarget != null && bossTarget.isAlive() && bossTarget != this
                && !(bossTarget instanceof Player) && boss.distanceToSqr(bossTarget) <= 24.0 * 24.0) {
            return bossTarget;
        }
        return null;
    }

    /**
     * 雇佣期 tick：到期（新一天结束）回到雇主身边并解除雇佣；
     * 与雇主一同战斗：帮打威胁，雇主血量 <60% 且歌唱冷却好 → 远离战场站定歌唱。
     * 返回 true = 正在退后/歌唱（本 tick 独占）。
     */
    private boolean tickHireAndSong() {
        ServerPlayer boss = getEmployer();
        if (boss == null || !boss.isAlive()) {
            return false; // 雇主离线/死亡：挂起等待
        }
        // 到期返回（雇佣一个游戏日后回来找你）
        if (this.level().getGameTime() >= hireUntilTick) {
            hired = false;
            employerId = null;
            singing = false;
            songTicks = 0;
            songBackoffTicks = 0;
            // 自然刷新的墨默：给雇主短暂续雇宽限，拒绝续雇则随天亮消失
            returnGraceTicks = naturalSpawn ? RETURN_GRACE_TICKS : 0;
            if (boss.level() == this.level()) {
                this.moveTo(boss.getX(), boss.getY(), boss.getZ(), this.getYRot(), this.getXRot());
                boss.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.tinkersnewlife.momo.returned"), true);
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.5, this.getZ(),
                            12, 0.3, 0.4, 0.3, 0.02);
                }
            }
            return false;
        }
        if (boss.level() != this.level()) return false; // 异维度暂不处理
        // 歌唱流程
        if (singing || songBackoffTicks > 0) {
            tickSongBody(boss);
            return true;
        }
        if (songCooldown > 0) songCooldown--;
        // 索敌优先级：攻击雇主者 > 雇主正在攻击的生物 > 雇主周围亡灵（主动清亡灵）
        boolean bossThreatened = false;
        LivingEntity attacker = boss.getLastHurtByMob();
        LivingEntity fightTarget = boss.getLastHurtMob();
        if (attacker != null && attacker.isAlive() && attacker != this
                && boss.distanceToSqr(attacker) <= 24.0 * 24.0) {
            bossThreatened = true;
            if (this.getTarget() != attacker) {
                this.setTarget(attacker);
            }
        } else if (fightTarget != null && fightTarget.isAlive() && fightTarget != this
                && !(fightTarget instanceof Player) && boss.distanceToSqr(fightTarget) <= 24.0 * 24.0) {
            bossThreatened = true;
            if (this.getTarget() == null) {
                this.setTarget(fightTarget);
            }
        } else if (this.getTarget() == null) {
            // 主动索敌雇主周围的亡灵/灾厄村民
            Mob prey = nearestHostileNear(boss, 12.0);
            if (prey != null) {
                this.setTarget(prey);
            }
        }
        // 雇主血量低于 60% → 远离战场，随后站定歌唱（再生 III）
        if (bossThreatened && boss.getHealth() <= boss.getMaxHealth() * EMPLOYER_LOW_HP_RATIO
                && songCooldown <= 0 && this.getHealth() > this.getMaxHealth() * 0.2f) {
            this.setTarget(null);
            clearPath();
            stopEating();
            state = S_IDLE;
            singing = true;
            songBackoffTicks = SONG_BACKOFF_TICKS;
            songTicks = SONG_DURATION_TICKS;
            return true;
        }
        return false;
    }

    /** 雇主周围最近的一只亡灵或灾厄村民（主动索敌清怪） */
    @Nullable
    private Mob nearestHostileNear(LivingEntity center, double radius) {
        Mob best = null;
        double bestDist = radius * radius;
        for (Mob m : this.level().getEntitiesOfClass(Mob.class, center.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != this)) {
            net.minecraft.world.entity.MobType mt = m.getMobType();
            if (mt != net.minecraft.world.entity.MobType.UNDEAD
                    && mt != net.minecraft.world.entity.MobType.ILLAGER) {
                continue;
            }
            double d = center.distanceToSqr(m);
            if (d < bestDist) {
                bestDist = d;
                best = m;
            }
        }
        return best;
    }

    /** 歌唱体：先退后 1s，再站定歌唱 10s（雇主获得再生 III），被攻击或超时结束 */
    private void tickSongBody(ServerPlayer boss) {
        if (songBackoffTicks > 0) {
            songBackoffTicks--;
            this.getLookControl().setLookAt(boss, 20.0F, 20.0F);
            LivingEntity threat = threatOfEmployer(boss);
            if (threat != null && this.distanceToSqr(threat) < 9.0 * 9.0) {
                Vec3 away = this.position().subtract(threat.position()).normalize();
                Vec3 goal = this.position().add(away.scale(8.0));
                this.getNavigation().moveTo(goal.x, this.getY(), goal.z, 1.2);
            } else {
                this.getNavigation().stop();
            }
            return;
        }
        // 站定歌唱
        this.getNavigation().stop();
        this.getLookControl().setLookAt(boss, 20.0F, 20.0F);
        if (songTicks > 0) {
            songTicks--;
            if (songTicks % 40 == 0 && this.distanceToSqr(boss) <= 32.0 * 32.0) {
                boss.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120,
                        SONG_REGEN_AMPLIFIER, false, true));
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.NOTE, this.getX(), this.getY() + 1.9, this.getZ(),
                            3, 0.3, 0.2, 0.3, 0);
                }
            }
            if (songTicks % 80 == 0) {
                this.playSound(ModSounds.MOMO_AMBIENT.get(), 0.7F, 1.0F);
            }
            if (songTicks <= 0) {
                singing = false;
                songCooldown = SONG_COOLDOWN_TICKS;
            }
        }
    }

    /** 被攻击打断歌唱（进食也会被打断） */
    private void stopSinging() {
        if (singing || songBackoffTicks > 0) {
            singing = false;
            songTicks = 0;
            songBackoffTicks = 0;
            songCooldown = SONG_COOLDOWN_TICKS;
        }
        stopEating();
    }

    /** 非索敌时进食：判定开始 → 持续 2s（40tick）进食动画（纯动画，无增益；进食时收起战镰） */
    private void tickEatIfIdle() {
        if (this.getTarget() != null || singing || this.isInWater() || this.isDeadOrDying()) {
            if (eatTicks > 0) stopEating();
            return;
        }
        if (eatTicks > 0) {
            eatTicks--;
            if (eatFood != null && eatTicks % 10 == 0 && this.level() instanceof ServerLevel sl) {
                sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                                net.minecraft.core.particles.ParticleTypes.ITEM, new ItemStack(eatFood)),
                        this.getX(), this.getY() + 1.7, this.getZ(), 3, 0.2, 0.1, 0.2, 0.01);
            }
            if (eatTicks <= 0) {
                stopEating();
                this.playSound(SoundEvents.GENERIC_EAT, 0.5F, 0.8F + this.random.nextFloat() * 0.3F);
            }
            return;
        }
        if (eatCheckCooldown > 0) {
            eatCheckCooldown--;
            return;
        }
        eatCheckCooldown = EAT_CHECK_INTERVAL;
        if (this.random.nextDouble() >= EAT_CHANCE) return;
        Item food = randomFood();
        if (food == null) return;
        eatFood = food;
        eatTicks = EAT_DURATION_TICKS;
        setEatingFlag(true);
        this.swing(InteractionHand.MAIN_HAND);
        this.playSound(SoundEvents.GENERIC_EAT, 0.6F, 0.8F + this.random.nextFloat() * 0.4F);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new net.minecraft.core.particles.ItemParticleOption(
                            net.minecraft.core.particles.ParticleTypes.ITEM, new ItemStack(food)),
                    this.getX(), this.getY() + 1.7, this.getZ(), 5, 0.2, 0.1, 0.2, 0.01);
        }
    }

    private void stopEating() {
        eatTicks = 0;
        eatFood = null;
        setEatingFlag(false);
    }

    private Item randomFood() {
        if (cachedFoods == null) {
            cachedFoods = new ArrayList<>();
            for (Item it : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                if (it != null && it.isEdible()) {
                    cachedFoods.add(it);
                }
            }
        }
        if (cachedFoods.isEmpty()) return null;
        return cachedFoods.get(this.random.nextInt(cachedFoods.size()));
    }

    // ============================================================
    //  通用应急：伤害吟唱抗性 / 低血大斩杀（雇佣与未雇佣通用）
    // ============================================================

    public boolean isResistantTo(String type) {
        return this.tickCount < resistUntilTick && resistTypes.contains(type);
    }

    /** 灼烧定身类负面效果对墨默无效（狱焰/BURN_HEX 等：施加即拒绝，杜绝被烧到无法移动） */
    @Override
    public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance instance) {
        if (!super.canBeAffected(instance)) return false;
        net.minecraft.resources.ResourceLocation key =
                net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(instance.getEffect());
        if (key != null) {
            String path = key.getPath().toLowerCase();
            if (path.contains("inferno") || path.contains("burn") || path.contains("bind")
                    || path.contains("root") || path.contains("freeze") || path.contains("stun")
                    || path.contains("paralysis")) {
                return false;
            }
        }
        return true;
    }

    /** 伤害事件回调（服务端）：入 5s 窗口，累计过半血 → 请求吟唱 */
    public void recordHit(String type, float amount) {
        if (this.level().isClientSide) return;
        if (this.isDeadOrDying() || fleeTriggered || singing || chantTicks > 0) return;
        dmgWindow.add(new DamageHit(this.tickCount, amount, type));
        dmgWindow.removeIf(h -> this.tickCount - h.tick > DMG_WINDOW_TICKS);
        if (isResistantTo(type)) return;
        float sum = 0;
        java.util.Set<String> types = new HashSet<>();
        for (DamageHit h : dmgWindow) {
            sum += h.amount;
            types.add(h.type);
        }
        if (sum > this.getMaxHealth() * CHANT_DAMAGE_THRESHOLD) {
            chantTypes = types;
            chantRequested = true;
        }
    }

    /** 返回 true = 本 tick 被应急动作占用（吟唱 / 大斩杀 / 对空跳斩） */
    private boolean tickResponseCore() {
        if (chantTicks > 0) {
            tickChant();
            return true;
        }
        if (chantRequested) {
            startChant();
            return true;
        }
        if (execBusy) {
            tickMassExecute();
            return true;
        }
        if (!execBusy && !singing && !fleeTriggered
                && this.getHealth() < this.getMaxHealth() * MASS_EXECUTE_HP
                && this.tickCount > execCooldownUntil) {
            tryStartMassExecute();
            if (execBusy) return true;
        }
        // 对空跳斩：目标悬空够不到 → 蓄力 3s 后跳到其头顶 5 段连斩
        if (airComboActive) {
            tickAirCombo();
            return true;
        }
        if (chantTicks <= 0 && !execBusy && !finisherActive && !singing && !fleeTriggered
                && this.tickCount > airComboCooldown) {
            LivingEntity t = this.getTarget();
            if (t != null && t.isAlive() && t != this
                    && !t.onGround()
                    && t.getY() - this.getY() > AIR_GAP
                    && this.distanceTo(t) > 3.2
                    && this.distanceToSqr(t) <= AIR_RADIUS * AIR_RADIUS) {
                startAirCombo(t);
                return true;
            }
        }
        return false;
    }

    /** 蓄力 3s：站定蓄力，随后跳至目标头顶 */
    private void startAirCombo(LivingEntity target) {
        airComboActive = true;
        airChargeTicks = AIR_CHARGE_TICKS;
        airHitIndex = 0;
        airHitTimer = 0;
        state = S_IDLE;
        this.getNavigation().stop();
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.8F, 0.6F);
        }
    }

    private void tickAirCombo() {
        LivingEntity t = this.getTarget();
        if (t == null || !t.isAlive() || t.level() != this.level()) {
            cancelAirCombo();
            return;
        }
        this.getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (airChargeTicks > 0) {
            // 蓄力：站定 + 蓄力粒子
            this.getNavigation().stop();
            airChargeTicks--;
            if (this.level() instanceof ServerLevel sl && airChargeTicks % 6 == 0) {
                sl.sendParticles(ParticleTypes.CRIT,
                        this.getX(), this.getY() + 1.4, this.getZ(),
                        4, 0.3, 0.3, 0.3, 0.02);
            }
            if (airChargeTicks <= 0) {
                leapAboveTarget(t);
            }
            return;
        }
        // 头顶 5 段连斩
        if (airHitIndex >= AIR_HIT_MULTIPLIERS.length) {
            // 收尾：落地并结束连段
            landOnGround();
            cancelAirCombo();
            return;
        }
        if (--airHitTimer > 0) return;
        applyHurt(t, combatBase() * AIR_HIT_MULTIPLIERS[airHitIndex]);
        this.swing(InteractionHand.MAIN_HAND);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(1.0F, 0.05F, 0.05F), 1.0F),
                    t.getX(), t.getY() + 1.2, t.getZ(), 8, 0.4, 0.5, 0.4, 0.01);
            sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        airHitIndex++;
        airHitTimer = 4;
    }

    /** 跳到目标头顶（找可站立/无碰撞的头顶位置） */
    private void leapAboveTarget(LivingEntity t) {
        if (!(this.level() instanceof ServerLevel sl)) {
            cancelAirCombo();
            return;
        }
        double[] offsets = {3.6, 2.8, 4.4, 2.2};
        boolean placed = false;
        for (double off : offsets) {
            double y = Math.min(t.getY() + off, this.level().getMaxBuildHeight() - 2.0);
            this.moveTo(t.getX(), y, t.getZ(), this.getYRot(), this.getXRot());
            if (sl.noCollision(this)) {
                placed = true;
                break;
            }
        }
        if (!placed) {
            // 头顶无位置：放弃该连段（普通追击）
            cancelAirCombo();
            return;
        }
        this.fallDistance = 0;
        this.setNoGravity(true);
        airHitIndex = 0;
        airHitTimer = 6;
        sl.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.0, this.getZ(),
                14, 0.4, 0.5, 0.4, 0.02);
        sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.4F);
    }

    private void cancelAirCombo() {
        airComboActive = false;
        airChargeTicks = 0;
        airHitIndex = 0;
        airHitTimer = 0;
        if (!this.onGround()) {
            landOnGround();
        }
        this.setNoGravity(false);
        this.fallDistance = 0;
        airComboCooldown = this.tickCount + AIR_COMBO_COOLDOWN;
    }

    /** 直接落到本列最近的地面上（防高空坠落） */
    private void landOnGround() {
        if (this.onGround()) return;
        if (!(this.level() instanceof ServerLevel sl)) return;
        int x = (int) Math.floor(this.getX());
        int z = (int) Math.floor(this.getZ());
        for (int y = (int) Math.floor(this.getY()); y >= sl.getMinBuildHeight() + 2; y--) {
            BlockPos below = new BlockPos(x, y - 1, z);
            if (sl.getBlockState(below).isCollisionShapeFullBlock(sl, below)) {
                this.moveTo(x + 0.5, y, z + 0.5, this.getYRot(), this.getXRot());
                this.fallDistance = 0;
                return;
            }
        }
    }

    /** 传送至安全位置并开始 1s 吟唱 */
    private void startChant() {
        chantRequested = false;
        teleportToSafety();
        chantTicks = CHANT_TICKS;
        state = S_IDLE;
        this.getNavigation().stop();
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /** 吟唱通道：站定 1s，结束记住伤害类型并获得 60% 抗性 60s */
    private void tickChant() {
        this.getNavigation().stop();
        chantTicks--;
        if (this.level() instanceof ServerLevel sl && chantTicks % 5 == 0) {
            sl.sendParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 1.5, this.getZ(),
                    10, 0.4, 0.3, 0.4, 0.2);
        }
        if (chantTicks <= 0) {
            resistTypes.clear();
            if (chantTypes != null) {
                resistTypes.addAll(chantTypes);
            }
            resistUntilTick = this.tickCount + RESIST_TICKS;
            chantTypes = null;
            dmgWindow.clear();
        }
    }

    /** 随机传送至附近无碰撞的安全落点 */
    private void teleportToSafety() {
        if (!(this.level() instanceof ServerLevel sl)) return;
        for (int i = 0; i < 8; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double r = 6.0 + this.random.nextDouble() * 10.0;
            double x = this.getX() + Math.cos(a) * r;
            double z = this.getZ() + Math.sin(a) * r;
            this.moveTo(x, this.getY(), z, this.getYRot(), this.getXRot());
            if (sl.noCollision(this)) {
                this.fallDistance = 0;
                break;
            }
        }
        sl.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.2, this.getZ(),
                14, 0.4, 0.5, 0.4, 0.02);
        sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** 该实体是否为墨默的可斩杀目标（50 格大斩杀用） */
    private boolean isMomoCombatTarget(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isSpectator()) return false;
        if (e == getEmployer()) return false;
        if (e instanceof Player p && !aggroSet.contains(p.getUUID())) return false;
        if (e == this.getTarget()) return true;
        if (aggroSet.contains(e.getUUID())) return true;
        if (e instanceof Mob m) {
            net.minecraft.world.entity.MobType mt = m.getMobType();
            return mt == net.minecraft.world.entity.MobType.UNDEAD
                    || mt == net.minecraft.world.entity.MobType.ILLAGER;
        }
        return false;
    }

    private void tryStartMassExecute() {
        execQueue.clear();
        List<LivingEntity> all = this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(MASS_EXECUTE_RADIUS), this::isMomoCombatTarget);
        all.sort(java.util.Comparator.comparingDouble(this::distanceToSqr));
        for (int i = 0; i < all.size() && execQueue.size() < 8; i++) {
            execQueue.add(all.get(i).getUUID());
        }
        if (execQueue.isEmpty()) return;
        execBusy = true;
        execStage = 0;
        execTimer = 0;
    }

    /** 大斩杀：逐个瞬移到目标身后连斩（红色粒子） */
    private void tickMassExecute() {
        if (execQueue.isEmpty()) {
            execBusy = false;
            execCooldownUntil = this.tickCount + MASS_EXECUTE_COOLDOWN;
            return;
        }
        UUID id = execQueue.get(0);
        Entity e = ((ServerLevel) this.level()).getEntity(id);
        if (!(e instanceof LivingEntity t) || !t.isAlive() || t.level() != this.level()) {
            execQueue.remove(0);
            execStage = 0;
            return;
        }
        if (execStage == 0) {
            this.getNavigation().stop();
            Vec3 dir = t.position().subtract(t.getLookAngle().scale(2.0));
            this.moveTo(dir.x, t.getY(), dir.z, this.getYRot(), this.getXRot());
            this.fallDistance = 0;
            this.getLookControl().setLookAt(t, 360.0F, 360.0F);
            execStage = 1;
            execTimer = 2;
            if (this.level() instanceof ServerLevel sl) {
                sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.6F);
            }
            return;
        }
        if (--execTimer > 0) return;
        float[] mults = {1.0f, 1.2f, 1.5f};
        float dmg = combatBase() * mults[execStage - 1];
        applyHurt(t, dmg);
        this.swing(InteractionHand.MAIN_HAND);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(1.0F, 0.05F, 0.05F), 1.0F),
                    t.getX(), t.getY() + 1.2, t.getZ(), 10, 0.4, 0.5, 0.4, 0.01);
        }
        execStage++;
        if (execStage > 3) {
            execQueue.remove(0);
            execStage = 0;
        } else {
            execTimer = 2;
        }
    }

    /** 当前基础攻击：雇佣 20，未雇佣 50 */
    private float combatBase() {
        return hired ? HIRED_BASE_ATTACK : attackBase();
    }

    /** 格挡期间追击速度：减速至 60%（格挡时也能前进，只是变慢） */
    private double chaseSpeedWithBlock(double base) {
        return isBlockingStance() ? base * 0.6 : base;
    }

    /** 受击标记（MomoMerchantHandler 回调） */
    public void markDamaged() {
        this.lastDamageAtTick = this.tickCount;
    }

    /** 卡死逃生：最近 2s 内有受击 + 目标存在，且 6s 窗口累计移动 ≤1 格 → 传送至目标身后 */
    private void tickStuckEscape() {
        LivingEntity target = this.getTarget();
        boolean underAttack = this.tickCount - lastDamageAtTick <= 120;
        boolean recentAttackValid = target != null && target.isAlive() && target != this;
        if (!recentAttackValid || !underAttack || this.tickCount < stuckEscapeCooldown) {
            stuckCheckPos = null;
            stuckCheckTicks = 0;
            stuckMoved = 0;
            prevTickPos = this.position();
            return;
        }
        // 累计本 tick 位移（忽略传送类大位移）
        if (prevTickPos != null && stuckCheckPos != null) {
            double d = Math.hypot(this.getX() - prevTickPos.x, this.getZ() - prevTickPos.z);
            if (d >= 0.001 && d < 1.5) {
                stuckMoved += d;
            }
        }
        if (stuckCheckPos == null) {
            stuckCheckPos = this.position();
            stuckMoved = 0;
            stuckCheckTicks = 0;
        } else {
            stuckCheckTicks++;
        }
        if (stuckCheckTicks >= STUCK_WINDOW_TICKS && stuckMoved <= STUCK_MAX_MOVE) {
            teleportBehindTarget(target);
            stuckCheckPos = null;
            stuckCheckTicks = 0;
            stuckMoved = 0;
            stuckEscapeCooldown = this.tickCount + STUCK_ESCAPE_COOLDOWN;
        }
        prevTickPos = this.position();
    }

    /** 传送到目标身后（碰撞则退回随机安全落点） */
    private void teleportBehindTarget(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 dir = target.position().subtract(target.getLookAngle().scale(2.2));
        Vec3 behind = new Vec3(dir.x, target.getY(), dir.z);
        this.moveTo(behind.x, behind.y, behind.z, this.getYRot(), this.getXRot());
        this.fallDistance = 0;
        if (!sl.noCollision(this)) {
            teleportNearEntity(target); // 身后被占：随机传送附近
            return;
        }
        sl.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.2, this.getZ(),
                14, 0.4, 0.5, 0.4, 0.02);
        sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    /**
     * 清除"灼烧锁移动"类负面效果：
     * 效果注册名含 inferno/burn/bind/root/freeze/stun/paralysis/slow 等（狱焰类）→ 任何时刻清；
     * 其他负面效果若带移动速度负修正（减速类）→ 仅在墨默着火时清。
     */
    private void cleanseMovementLockEffects() {
        if (this.level().isClientSide || this.isDeadOrDying()) return;
        boolean burning = this.isOnFire();
        java.util.List<net.minecraft.world.effect.MobEffect> toRemove = new ArrayList<>();
        for (net.minecraft.world.effect.MobEffectInstance inst : this.getActiveEffects()) {
            net.minecraft.world.effect.MobEffect eff = inst.getEffect();
            if (eff.isBeneficial()) continue;
            boolean lock = false;
            net.minecraft.resources.ResourceLocation key =
                    net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(eff);
            if (key != null) {
                String path = key.getPath().toLowerCase();
                lock = path.contains("inferno") || path.contains("burn") || path.contains("flame")
                        || path.contains("bind") || path.contains("root") || path.contains("freeze")
                        || path.contains("stun") || path.contains("paralysis") || path.contains("slow");
            }
            if (!lock && burning) {
                // 着火时额外清除带移动速度负修正的效果（通用减速）
                Object mods = eff.getAttributeModifiers().get(
                        net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (mods instanceof net.minecraft.world.entity.ai.attributes.AttributeModifier single) {
                    if (single.getAmount() < 0) lock = true;
                } else if (mods instanceof Iterable<?> iter) {
                    for (Object o : iter) {
                        if (o instanceof net.minecraft.world.entity.ai.attributes.AttributeModifier am
                                && am.getAmount() < 0) {
                            lock = true;
                            break;
                        }
                    }
                }
            }
            if (lock) {
                toRemove.add(eff);
            }
        }
        for (net.minecraft.world.effect.MobEffect eff : toRemove) {
            this.removeEffect(eff);
        }
    }

    // ============================================================
    //  雇佣模式独立 AI
    // ============================================================

    /** 雇佣状态主循环（与未雇佣 AI 完全分开）：始终跟随雇主、清亡灵、战斗两刀必中+斩杀、保留歌唱/进食 */
    private void tickHiredAI() {
        // 低血逃跑
        if (!fleeTriggered && this.getHealth() <= this.getMaxHealth() * 0.05f) {
            fleeTriggered = true;
            fleeTimer = 60;
            this.setTarget(null);
            state = S_IDLE;
        }
        if (fleeTriggered) {
            tickFlee();
            return;
        }
        ServerPlayer boss = getEmployer();
        if (boss == null || !boss.isAlive()) return; // 雇主离线/死亡：原地挂起
        // 到期返回（雇佣一个游戏日后回来找你）
        if (this.level().getGameTime() >= hireUntilTick) {
            returnToEmployer(boss);
            return;
        }
        if (boss.level() != this.level()) return; // 异维度暂不处理
        // 歌唱（保留）
        if (singing || songBackoffTicks > 0) {
            tickSongBody(boss);
            return;
        }
        if (songCooldown > 0) songCooldown--;
        // 始终跟随雇主：>50 格直接传送
        if (this.distanceToSqr(boss) > EMPLOYER_TELEPORT_DIST * EMPLOYER_TELEPORT_DIST) {
            teleportNearEntity(boss);
        }
        // 斩杀进行中
        if (finisherActive) {
            tickFinisher();
            return;
        }
        LivingEntity target = this.getTarget();
        // 雇主被攻击 → 优先索敌攻击者
        LivingEntity attacker = boss.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && attacker != this
                && boss.distanceToSqr(attacker) <= 24.0 * 24.0) {
            if (target != attacker) {
                this.setTarget(attacker);
                target = attacker;
            }
        }
        // 诡厄巫法保护链（环境有 Goety 时）：目标是受限 Boss 且被黑曜石柱保护 → 先打邪教徒，其次黑曜石柱，最后 Boss
        if (target != null && target.isAlive()
                && com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isDamageLimitedBoss(target)) {
            LivingEntity chain = pickGoetyChainTarget(boss);
            if (chain != null && chain != target) {
                if (this.tickCount % 200 == 0) {
                    LOGGER.info("[墨默] 保护链切目标 → {}", describeTargetRole(chain));
                }
                engageChainTarget(chain); // 柱太远（>12格）直接瞬移到柱边
                target = chain;
            }
        }
        if (target != null && target.isAlive()) {
            tickHiredCombat(boss, target);
            return;
        }
        // 诡厄巫法：主动攻击黑曜石柱/邪教徒/受限 Boss（破保护链优先）
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isAvailable()) {
            LivingEntity goetyTarget = pickGoetyChainTarget(boss);
            if (goetyTarget != null) {
                engageChainTarget(goetyTarget);
                return;
            }
        }
        // 主动索敌雇主周围的亡灵/灾厄村民
        Mob prey = nearestHostileNear(boss, 12.0);
        if (prey != null) {
            this.setTarget(prey);
            return;
        }
        // 歌唱触发：雇主低血且有威胁
        LivingEntity threat = threatOfEmployer(boss);
        if (threat != null && boss.getHealth() <= boss.getMaxHealth() * EMPLOYER_LOW_HP_RATIO
                && songCooldown <= 0 && this.getHealth() > this.getMaxHealth() * 0.2f) {
            this.setTarget(null);
            clearPath();
            stopEating();
            state = S_IDLE;
            singing = true;
            songBackoffTicks = SONG_BACKOFF_TICKS;
            songTicks = SONG_DURATION_TICKS;
            return;
        }
        // 空闲：低语 + 进食 + 以雇主为中心游走
        tickAmbientVoice();
        tickEatIfIdle();
        if (this.distanceToSqr(boss) > 14.0 * 14.0) {
            this.getNavigation().moveTo(boss, 1.15);
        } else {
            tickWanderPath(); // 游走锚点 = 雇主（半径 6 格）
        }
    }

    /** 诊断：目标在 Goety 保护链里的角色 */
    private String describeTargetRole(LivingEntity e) {
        if (e == null) return "null";
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isPillar(e)) return "黑曜石柱 " + e.getType();
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isCultist(e)) return "邪教徒 " + e.getType();
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isDamageLimitedBoss(e)) return "受限Boss " + e.getType();
        return "其他 " + e.getType();
    }

    /**
     * 诡厄巫法（可选）：以雇主为中心选目标——保护链 邪教徒 > 黑曜石柱 > 受限 Boss；
     * 附近存在受限 Boss(32格)时：查其黑曜石柱(64格内视为保护柱，柱常被召在12~24格外
     * 且可能因场地不贴身)→柱旁邪教徒优先；无 Boss 时也主动打 40 格内的邪教徒/黑曜石柱。
     * 环境无 Goety 返回 null。
     */
    @Nullable
    private LivingEntity pickGoetyChainTarget(LivingEntity boss) {
        if (!com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isAvailable()) return null;
        LivingEntity limited = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestLimitedBoss(boss, 32.0);
        if (limited != null) {
            LivingEntity pillar = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestPillar(limited, 64.0);
            if (pillar != null) {
                LivingEntity cult = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.cultistNearPillar(pillar);
                if (cult != null) return cult;                       // ① 邪教徒（保护柱者）
                return pillar;                                       // ② 黑曜石柱（破保护）
            }
            return limited;                                          // ③ 目标（无柱保护则直接打）
        }
        LivingEntity cult = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestCultist(boss, 40.0);
        if (cult != null) return cult;
        return com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestPillar(boss, 40.0);
    }

    /** 切到保护链目标：目标为黑曜石柱且离得远（>12格）时直接瞬移到柱边，避免走不过去/绕路 */
    private void engageChainTarget(LivingEntity chain) {
        if (chain == null) return;
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isPillar(chain)
                && this.distanceToSqr(chain) > 12.0 * 12.0) {
            blinkBeside(chain);
        }
        this.setTarget(chain);
        this.getNavigation().stop();
    }

    /** 瞬移到目标身旁（破远处黑曜石柱用） */
    private void blinkBeside(LivingEntity target) {
        if (target == null || target.level().isClientSide) return;
        Vec3 away = this.position().subtract(target.position());
        double h = Math.sqrt(away.x * away.x + away.z * away.z);
        double reach = 1.4 + target.getBbWidth() * 0.5;
        double dx = h > 0.001 ? away.x / h * reach : reach;
        double dz = h > 0.001 ? away.z / h * reach : 0;
        this.moveTo(target.getX() + dx, target.getY(), target.getZ() + dz,
                this.getYRot(), this.getXRot());
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.6F);
            sl.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(),
                    10, 0.3, 0.4, 0.3, 0.01);
        }
    }

    /** 雇佣战斗：两刀（80%/180%×20 必中）→ 拉远；受击格挡 → 反击连斩；目标 <10 血 → 瞬移身后斩杀 */
    private void tickHiredCombat(ServerPlayer boss, LivingEntity target) {
        // 格挡窗口到期
        if (blockWindowUntil > 0 && this.tickCount > blockWindowUntil) {
            blockWindowUntil = -1;
        }
        // 受击格挡成功 → 连斩反击
        if (state == S_COUNTER) {
            tickHiredCounter();
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 斩杀：目标生命 < 10
        if (target.getHealth() < EXECUTE_HP) {
            startFinisher(target);
            return;
        }
        if (this.distanceToSqr(target) > EMPLOYER_TELEPORT_DIST * EMPLOYER_TELEPORT_DIST) {
            this.setTarget(null);
            return;
        }
        if (hiredAttackCooldown > 0) hiredAttackCooldown--;
        switch (hiredComboPhase) {
            case 0 -> {
                if (this.distanceToSqr(target) <= 2.6 * 2.6) {
                    this.getNavigation().stop();
                    if (hiredAttackCooldown <= 0) {
                        // 砍一刀（80% × 20，必中）
                        hiredHit(target, 0.8f);
                        hiredComboPhase = 1;
                        hiredComboTimer = 10; // 0.5s 后第二刀
                    }
                } else {
                    this.getNavigation().moveTo(target, chaseSpeedWithBlock(1.4));
                }
            }
            case 1 -> {
                // 等 0.5s 后竖劈（180% × 20，必中）
                if (--hiredComboTimer <= 0) {
                    hiredHit(target, 1.8f);
                    hiredComboPhase = 2;
                    hiredComboTimer = 24; // 砍完拉远
                }
            }
            default -> {
                // 拉远
                if (--hiredComboTimer <= 0 || this.distanceToSqr(target) > 8.0 * 8.0) {
                    hiredComboPhase = 0;
                    hiredAttackCooldown = 25;
                } else {
                    Vec3 away = this.position().subtract(target.position()).normalize();
                    Vec3 goal = this.position().add(away.scale(6.0));
                    this.getNavigation().moveTo(goal.x, this.getY(), goal.z, 1.2);
                }
            }
        }
    }

    /** 雇佣必中一击（无视距离/面向，直接结算） */
    private void hiredHit(LivingEntity target, float multiplier) {
        this.swing(InteractionHand.MAIN_HAND);
        applyHurt(target, HIRED_BASE_ATTACK * multiplier);
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                    3, 0.2, 0.1, 0.2, 0);
            sl.playSound(null, this.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }

    /**
     * 墨默的伤害统一入口：
     * 凋灵出生无敌 → 无视；
     * 诡厄巫法受限 Boss（亚波伦/使徒）处于黑曜石柱保护（obsidianInvul>0，全程免伤）时 →
     * 墨默打不出伤害，必须先破柱（索敌链已切到柱/邪教徒），此处只做格挡反馈；
     * 柱破后 → 对其限伤（启示录 apollyon_hurt_limit=20 等事件级 clamp）用多段拆分突破。
     */
    private void applyHurt(LivingEntity target, float dmg) {
        witherInvulnBypass(target);
        target.invulnerableTime = 0;
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isPillar(target)) {
            // 黑曜石柱：墨默直接多段穿透击碎（绕护甲/empowered 免伤，柱通常 50 血 → 一两刀碎）
            pierceDamageDirect(target, dmg);
            return;
        }
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isDamageLimitedBoss(target)) {
            // 柱保护以 Boss 实体的 obsidianInvul 计时为准（存活黑曜石柱每 tick 置 10），
            // 比按距离找柱更接近真实免伤判定（柱瞬移贴身后必然 >0）
            int shield = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.readObsidianInvul(target);
            if (shield > 0) {
                if (this.tickCount % 100 == 0) {
                    LOGGER.info("[墨默] 目标被柱保护挡住 dmg={} {}", dmg,
                            com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.debugDescribe(target));
                }
                if (this.level() instanceof ServerLevel sl) {
                    sl.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8F, 1.2F);
                    sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2,
                            target.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                }
                return; // 柱未破：本次伤害被挡
            }
            pierceDamageDirect(target, dmg); // 柱已破：多段拆分突破限伤
        } else {
            target.hurt(this.damageSources().mobAttack(this), dmg);
        }
    }

    /**
     * 突破"每次伤害上限"（启示录 apollyon_hurt_limit=20；主 Goety apostleDamageCap=20
     * 因 genericKill 带 bypasses_invulnerability 天然绕过）：
     * 不走直改血（会废掉受击事件/阶段逻辑），而是把伤害拆成 ≤19 的多段连续 hurt——
     * 每段都在上限之下、走完整伤害管线（事件/Boss 阶段照常触发），累计总和突破单次上限。
     * 每段前顺带清零：使徒受击无敌帧 moddedInvul（其他带直接实体的攻击会留下 15tick 挡伤）
     * 与启示录下界 Apollyon 的受击冷却（每次 actuallyHurt 置 30、期间 hurt() 被直接取消）。
     * 目标为使徒时按 apostleDamageCompensation 放大送出量，抵消下界减伤(50%)与
     * 附近玩家时非玩家伤害减半——落血仍是名义伤害。
     */
    private static final float PIERCE_CHUNK = 19.0F;

    private void pierceDamageDirect(LivingEntity target, float dmg) {
        if (target.level().isClientSide || target.isRemoved()) return;
        if (this.tickCount % 100 == 0) {
            LOGGER.info("[墨默] 多段穿透 dmg={} {}", dmg,
                    com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.debugDescribe(target));
        }
        // 全额穿透：hurt 事件 + 差额直补（总量精确全额、不受免疫窗/单次上限影响）
        com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.pierceFullDamage(target, dmg);
    }

    /** 受击格挡成功后的反击（雇佣版：60/80/100% × 20，必中） */
    private void tickHiredCounter() {
        LivingEntity atk = getLastBlockedBy();
        if (atk == null || !atk.isAlive() || this.distanceToSqr(atk) > 5.0 * 5.0) {
            counterIndex = 0;
            hiredComboPhase = 0;
            state = S_IDLE;
            if (this.getTarget() == null) this.setTarget(atk);
            return;
        }
        this.getLookControl().setLookAt(atk, 30.0F, 30.0F);
        if (this.distanceToSqr(atk) > 2.4 * 2.4) {
            this.getNavigation().moveTo(atk, chaseSpeedWithBlock(1.4));
            return;
        }
        this.getNavigation().stop();
        if (--counterTimer <= 0) {
            float mult = COUNTER_MULTIPLIERS[Math.min(counterIndex, COUNTER_MULTIPLIERS.length - 1)];
            hiredHit(atk, mult);
            counterIndex++;
            if (counterIndex >= 3) {
                counterIndex = 0;
                hiredComboPhase = 0;
                hiredAttackCooldown = 30;
                state = S_IDLE;
            } else {
                counterTimer = 5;
            }
        }
    }

    /** 斩杀：瞬移到敌人身后，红色粒子连斩收尾 */
    private void startFinisher(LivingEntity target) {
        this.getNavigation().stop();
        Vec3 dir = target.position().subtract(target.getLookAngle().scale(2.0));
        Vec3 behind = new Vec3(dir.x, target.getY(), dir.z);
        this.moveTo(behind.x, behind.y, behind.z, this.getYRot(), this.getXRot());
        this.getLookControl().setLookAt(target, 360.0F, 360.0F);
        finisherTarget = target;
        finisherActive = true;
        finisherIndex = 0;
        finisherTimer = 3;
        if (this.level() instanceof ServerLevel sl) {
            sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8F, 1.6F);
            sl.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 1.0, this.getZ(), 10, 0.3, 0.4, 0.3, 0.01);
        }
    }

    private void tickFinisher() {
        LivingEntity t = finisherTarget;
        if (t == null || !t.isAlive() || t.level() != this.level()) {
            finisherActive = false;
            finisherTarget = null;
            this.setTarget(null);
            state = S_IDLE;
            hiredComboPhase = 0;
            return;
        }
        this.getLookControl().setLookAt(t, 360.0F, 360.0F);
        if (--finisherTimer > 0) return;
        float[] mults = {0.9f, 1.1f, 1.4f};
        applyHurt(t, HIRED_BASE_ATTACK * mults[Math.min(finisherIndex, 2)]);
        this.swing(InteractionHand.MAIN_HAND);
        if (this.level() instanceof ServerLevel sl) {
            // 红色粒子（斩杀特效）
            sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(1.0F, 0.05F, 0.05F), 1.0F),
                    t.getX(), t.getY() + 1.2, t.getZ(), 12, 0.4, 0.5, 0.4, 0.01);
            sl.playSound(null, t.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        finisherIndex++;
        if (finisherIndex >= 3) {
            finisherActive = false;
            finisherTarget = null;
            this.setTarget(null);
            state = S_IDLE;
            hiredComboPhase = 0;
            hiredAttackCooldown = 15;
        } else {
            finisherTimer = 3;
        }
    }

    /** 无视凋灵出生无敌（清零其无敌计时字段，反射兜底） */
    private static java.lang.reflect.Field WITHER_INVULN_FIELD = null;

    private void witherInvulnBypass(LivingEntity target) {
        if (!(target instanceof net.minecraft.world.entity.boss.wither.WitherBoss w)) return;
        w.invulnerableTime = 0;
        try {
            if (WITHER_INVULN_FIELD == null) {
                try {
                    WITHER_INVULN_FIELD = net.minecraft.world.entity.boss.wither.WitherBoss.class
                            .getDeclaredField("invulnerableTime");
                } catch (NoSuchFieldException e) {
                    WITHER_INVULN_FIELD = net.minecraft.world.entity.boss.wither.WitherBoss.class
                            .getDeclaredField("invulnTime");
                }
                if (WITHER_INVULN_FIELD != null) {
                    WITHER_INVULN_FIELD.setAccessible(true);
                }
            }
            if (WITHER_INVULN_FIELD != null) {
                WITHER_INVULN_FIELD.setInt(w, 0);
            }
        } catch (Throwable ignored) {
            // 找不到字段则忽略（退化：普通攻击仍可命中非无敌窗口）
        }
        // 出生无敌期间一并解除实体无敌标记（若其基于 isInvulnerable 实现）
        try {
            w.setInvulnerable(false);
        } catch (Throwable ignored) {
        }
    }

    /** 传送至目标附近的落点（寻找可站立位置，带回响粒子） */
    private void teleportNearEntity(LivingEntity e) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        for (int i = 0; i < 6; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            double x = e.getX() + Math.cos(a) * 1.8;
            double z = e.getZ() + Math.sin(a) * 1.8;
            this.moveTo(x, e.getY(), z, this.getYRot(), this.getXRot());
            if (sl.noCollision(this)) {
                break;
            }
        }
        this.moveTo(e.getX(), e.getY(), e.getZ(), this.getYRot(), this.getXRot());
        this.fallDistance = 0;
        sl.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.5, this.getZ(),
                12, 0.3, 0.4, 0.3, 0.02);
        sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** 雇佣到期：回来找雇主并解除雇佣（自然刷新版给续雇宽限） */
    private void returnToEmployer(ServerPlayer boss) {
        hired = false;
        employerId = null;
        singing = false;
        songTicks = 0;
        songBackoffTicks = 0;
        returnGraceTicks = naturalSpawn ? RETURN_GRACE_TICKS : 0;
        if (boss.level() == this.level()) {
            teleportNearEntity(boss);
            boss.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.tinkersnewlife.momo.returned"), true);
        }
    }

    // ============================================================
    //  受击 / 格挡 / 秒杀
    // ============================================================

    private LivingEntity lastBlockedBy;

    private LivingEntity getLastBlockedBy() {
        if (lastBlockedBy == null || !lastBlockedBy.isAlive() || lastBlockedBy.isRemoved()) {
            return null;
        }
        return lastBlockedBy;
    }

    /** 是否处于格挡窗口 */
    public boolean isBlockingStance() {
        return blockWindowUntil > 0 && this.tickCount <= blockWindowUntil && state != S_FLEE;
    }

    /**
     * 下界亚波伦"死亡箭雨"格挡：
     * 当前目标（或 32 格内低频扫描到的受限 Boss）正处于启示录 Apollyon 的箭雨施放
     * （isShooting，约 100 tick、每 tick 一箭）时，持续刷新格挡窗口——
     * 整段箭雨期间举盾免疫：箭矢 hurt 被格挡 → 其后续 5% 最大生命的虚空扣血也不会触发。
     */
    private void tickBarrageGuard() {
        if (this.isDeadOrDying() || this.isRemoved() || level().isClientSide) return;
        LivingEntity boss = null;
        LivingEntity t = this.getTarget();
        if (t != null && t.isAlive()
                && com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isDamageLimitedBoss(t)) {
            boss = t;
        } else if (this.tickCount % 10 == 0) {
            boss = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestLimitedBoss(this, 32.0);
        }
        if (boss == null) return;
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isApollyonBarraging(boss)) {
            if (this.tickCount % 100 == 0) {
                LOGGER.info("[墨默] 箭雨格挡持续中（Boss 仍在射击）");
            }
            blockWindowUntil = this.tickCount + BLOCK_WINDOW;
        }
    }

    /**
     * 格挡期间连"虚空/真伤"也一并格挡：下界亚波伦箭矢命中会额外
     * heal(-5%最大生命)（无视护甲/无敌帧/格挡的直接扣血）——举盾时免疫。
     */
    @Override
    public void heal(float amount) {
        if (amount < 0.0F && isBlockingStance()) {
            return;
        }
        super.heal(amount);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || this.isDeadOrDying()) return false;
        // 歌唱被攻击打断
        stopSinging();
        // 格挡窗口内：免疫伤害。近身（≤5格）格挡成功 → 连斩反击并收起格挡；
        // 远程/投射物格挡 → 刷新格挡窗口（持续射击的箭雨保持举盾），减速推进不打断追击
        if (isBlockingStance()) {
            // 攻击者判定：直接近战实体 / 投射物的发射者 / 兜底取伤害源实体
            LivingEntity living = null;
            Entity direct = source.getDirectEntity();
            if (direct instanceof LivingEntity le && le != this) {
                living = le;
            } else if (direct instanceof net.minecraft.world.entity.projectile.Projectile pr
                    && pr.getOwner() instanceof LivingEntity owner && owner != this) {
                living = owner;
            } else if (source.getEntity() instanceof LivingEntity le2 && le2 != this) {
                living = le2;
            }
            if (living != null) {
                aggroSet.add(living.getUUID());
                lastBlockedBy = living;
                if (this.getTarget() == null) this.setTarget(living);
                if (this.distanceToSqr(living) <= 5.0 * 5.0) {
                    // 近身格挡成功：连斩反击
                    blockWindowUntil = -1;
                    counterIndex = 0;
                    counterTimer = 0;
                    state = S_COUNTER;
                    this.getNavigation().stop();
                    if (this.level() instanceof ServerLevel sl) {
                        sl.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.2F);
                        sl.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.3, this.getZ(),
                                10, 0.4, 0.3, 0.4, 0.02);
                    }
                } else {
                    // 远程攻击被格挡：保持举盾（刷新窗口）+ 继续追击（以较慢速度推进），避免原地愣住
                    blockWindowUntil = this.tickCount + BLOCK_WINDOW;
                    if (this.state == S_COUNTER) {
                        counterIndex = 0;
                        state = S_ENGAGE;
                    }
                    if (this.level() instanceof ServerLevel sl) {
                        sl.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8F, 1.3F);
                    }
                }
            } else {
                // 未知来源（环境伤害等）：仍视为格挡，刷新窗口
                blockWindowUntil = this.tickCount + BLOCK_WINDOW;
            }
            return false;
        }
        // 正常受击：记录仇恨 + 开启 1s 格挡窗口
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity living && attacker != this) {
            aggroSet.add(attacker.getUUID());
            if (this.getTarget() == null && !(living instanceof MomoMerchant)) {
                this.setTarget(living);
            }
        }
        if (this.getTarget() != null) {
            blockWindowUntil = this.tickCount + BLOCK_WINDOW;
        }
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.isDeadOrDying() && this.getTarget() != null) {
            blockWindowUntil = this.tickCount + BLOCK_WINDOW;
        }
        return hurt;
    }

    /** 是否被"秒杀"（最后一击伤害 ≥ 最大生命） */
    private float lastDamageTaken = 0;

    public void recordDamageTaken(float amount) {
        this.lastDamageTaken = amount;
    }

    public boolean isOneShotKill() {
        return this.lastDamageTaken >= this.getMaxHealth();
    }

    // ============================================================
    //  死亡 / 掉落 / 语音
    // ============================================================

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int lootingLevel, boolean recentlyHitIn) {
        super.dropCustomDeathLoot(source, lootingLevel, recentlyHitIn);
        if (isOneShotKill() && level() instanceof ServerLevel sl) {
            this.spawnAtLocation(new ItemStack(ModItems.RLYEH_CALL.get()), 0.5F);
            net.minecraft.world.entity.ExperienceOrb orb = new net.minecraft.world.entity.ExperienceOrb(sl,
                    this.getX(), this.getY() + 0.5, this.getZ(), 15);
            sl.addFreshEntity(orb);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return null; // 低语由 tickAmbientVoice 控制
    }

    /** 受击语音：避免与其他语音重叠（连打时只播一次） */
    @Override
    protected void playHurtSound(DamageSource source) {
        if (voiceReady()) {
            voicePlayed(VOICE_TIMINGS.hurt);
            super.playHurtSound(source);
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.MOMO_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MOMO_DEATH.get();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    /** 蜘蛛式爬墙：水平贴墙即可攀爬（原版蜘蛛同款判定） */
    @Override
    public boolean onClimbable() {
        return this.horizontalCollision;
    }

    /** 贴墙时减少下滑；目标/雇主在更高处且紧贴墙 → 向上攀爬 */
    private void tickWallClimb() {
        if (this.level().isClientSide || this.isInWater() || !this.horizontalCollision) return;
        // 贴墙：限制下滑速度
        if (this.getDeltaMovement().y < -0.12) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0, 0.0, 1.0).add(0.0, -0.12, 0.0));
        }
        LivingEntity up = this.getTarget();
        if (up == null && hired) {
            up = getEmployer();
        }
        if (up == null || !up.isAlive() || up.getY() <= this.getY() + 0.8) return;
        double hd = Math.hypot(up.getX() - this.getX(), up.getZ() - this.getZ());
        if (hd <= 5.0) {
            // 头顶有空间才上爬（防挤进方块）
            if (this.level().noCollision(this.getBoundingBox().move(0.0, 1.0, 0.0))) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.28, 0.0));
                this.fallDistance = 0;
            }
        }
    }

    /** 水下：溺尸式憋气呼吸（不会窒息）+ 游泳（朝目标游；无目标时浮向水面） */
    private void tickWaterSwim() {
        if (this.level().isClientSide) return;
        if (!this.isInWater()) {
            // 出水：关闭游泳姿态
            if (this.isSwimming()) {
                this.setSwimming(false);
            }
            return;
        }
        // 游泳姿态（玩家模型会播放游泳动画：前伸划水+双腿打水）；贴地/能站时不摆姿势
        this.setSwimming(!this.onGround());
        // 憋气：周期性补满空气，永不窒息
        if (this.tickCount % 20 == 0 && this.getAirSupply() < this.getMaxAirSupply()) {
            this.setAirSupply(this.getMaxAirSupply());
        }
        // 微浮力：避免一直沉底
        if (this.getDeltaMovement().y < -0.12 && this.tickCount % 2 == 0) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.05, 0.0));
        }
        LivingEntity aim = this.getTarget();
        if (aim == null && hired) {
            aim = getEmployer();
        }
        if (aim != null && aim.isAlive() && aim.level() == this.level()) {
            double dx = aim.getX() - this.getX();
            double dy = aim.getY() - this.getY();
            double dz = aim.getZ() - this.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 1.8) {
                double sp = 0.5; // 游泳速度（块/tick）：快于玩家（0.15）
                Vec3 want = new Vec3(dx / dist * sp, dy / dist * sp, dz / dist * sp);
                Vec3 cur = this.getDeltaMovement();
                Vec3 next = cur.add(want.subtract(cur).scale(0.1));
                if (next.y < -0.25) {
                    next = new Vec3(next.x, -0.25, next.z);
                }
                this.setDeltaMovement(next);
                this.fallDistance = 0;
            }
        } else {
            // 无目标：浮向水面附近，避免呆在水底
            double surface = this.level().getSeaLevel();
            if (this.getY() < surface - 1.0 && this.getDeltaMovement().y < 0.1) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.04, 0.0));
            }
        }
    }
}
