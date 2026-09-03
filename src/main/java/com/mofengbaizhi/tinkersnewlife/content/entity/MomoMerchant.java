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
    /** 雇佣到期：hireDay 日起算，currentDay >= hireDay + 此值 时"新一天结束回来找你" */
    private static final int HIRE_EXPIRE_AFTER_DAYS = 2;
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
    private long hireDay = -1;            // 雇佣当天的 dayCount
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
        tag.putLong("MomoHireDay", hireDay);
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
        hireDay = tag.getLong("MomoHireDay");
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

    public enum HireResult { HIRED, RENEWED, HIRED_BY_OTHER, NO_ITEM, TOO_FAR, DEAD }

    /** 玩家点击雇佣：支付 1 个拉莱耶的呼唤，雇佣一天；重复支付可续期 */
    public HireResult hireFrom(ServerPlayer buyer) {
        if (level().isClientSide) return HireResult.DEAD;
        if (!this.isAlive() || this.isRemoved()) return HireResult.DEAD;
        if (buyer.distanceToSqr(this) > 8.0 * 8.0) return HireResult.TOO_FAR;
        if (hired && employerId != null && !employerId.equals(buyer.getUUID())) {
            return HireResult.HIRED_BY_OTHER;
        }
        if (countItem(buyer, ModItems.RLYEH_CALL.get()) < 1) return HireResult.NO_ITEM;
        consumeItem(buyer, ModItems.RLYEH_CALL.get(), 1);
        boolean renew = hired && employerId != null && employerId.equals(buyer.getUUID());
        employerId = buyer.getUUID();
        hireDay = currentDay();
        hired = true;
        returnGraceTicks = 0; // 续雇成功，取消"天亮消失"宽限
        this.setTarget(null);
        clearPath();
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.6, this.getZ(),
                    6, 0.3, 0.3, 0.3, 0.02);
        }
        return renew ? HireResult.RENEWED : HireResult.HIRED;
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
                e.hurt(this.damageSources().mobAttack(this), dmg);
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

        // 雇佣模式：一同战斗/低血歌唱/到期返回（歌唱时本 tick 独占）
        if (hired && tickHireAndSong()) {
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

    /** 商人声音（无战斗/无目标时偶尔低语；不与任意语音重叠） */
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
        this.getNavigation().moveTo(target, 1.35);

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
            this.getNavigation().moveTo(attacker, 1.4);
            return;
        }
        this.getNavigation().stop();
        if (--counterTimer <= 0) {
            float mult = COUNTER_MULTIPLIERS[Math.min(counterIndex, COUNTER_MULTIPLIERS.length - 1)];
            attacker.invulnerableTime = 0;
            attacker.hurt(this.damageSources().mobAttack(this), attackBase() * mult);
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
        // 到期返回（新一天结束回来找你）
        long now = currentDay();
        if (now >= hireDay + HIRE_EXPIRE_AFTER_DAYS) {
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
            // 主动索敌雇主周围的亡灵生物
            Mob undead = nearestUndeadNear(boss, 12.0);
            if (undead != null) {
                this.setTarget(undead);
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

    /** 雇主周围最近的一只亡灵（主动索敌清怪） */
    @Nullable
    private Mob nearestUndeadNear(LivingEntity center, double radius) {
        Mob best = null;
        double bestDist = radius * radius;
        for (Mob m : this.level().getEntitiesOfClass(Mob.class, center.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != this && e.getMobType() == MobType.UNDEAD)) {
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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || this.isDeadOrDying()) return false;
        // 歌唱被攻击打断
        stopSinging();
        // 格挡窗口内：免疫伤害（格挡成功 → 之后连斩）
        if (isBlockingStance()) {
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity living && attacker != this) {
                aggroSet.add(attacker.getUUID());
                lastBlockedBy = living;
                blockWindowUntil = -1;
                if (this.getTarget() == null) this.setTarget(living);
                counterIndex = 0;
                counterTimer = 0;
                state = S_COUNTER;
                this.getNavigation().stop();
                if (this.level() instanceof ServerLevel sl) {
                    sl.playSound(null, this.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 1.0F, 1.2F);
                    sl.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.3, this.getZ(),
                            10, 0.4, 0.3, 0.4, 0.02);
                }
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
}
