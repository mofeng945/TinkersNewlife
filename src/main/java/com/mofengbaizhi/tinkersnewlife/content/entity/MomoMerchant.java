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

    /** 保证 6 个售卖槽位已生成（生成时/读档后调用） */
    public void ensureOffers() {
        if (level().isClientSide) return;
        if (offers.size() >= 6) return;
        offers.clear();
        RandomSource random = this.getRandom();

        // 1-2：咒具池任选两个
        List<Item> cursedTools = new ArrayList<>();
        cursedTools.add(ModItems.TIAN_NI_HUO.get());
        cursedTools.add(ModItems.GOURD_JAIL.get());
        cursedTools.add(ModItems.BOUNDARY_FRAGMENT.get());
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
        this.playSound(ModSounds.MOMO_TRADE.get(), 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(true);
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

        // 自然刷新的墨默：白天到来时消失（刷怪蛋召唤的常驻）
        if (naturalSpawn) {
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

        // 有目标但没有进入交战 → 自动开战（玩家/怪物/监守者攻击后都会走到这里）
        if (state == S_IDLE) {
            LivingEntity t = this.getTarget();
            if (t != null && t.isAlive()) {
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

    /** 商人声音（无战斗/无目标时偶尔低语） */
    private void tickAmbientVoice() {
        if (--ambientVoiceTimer > 0) return;
        ambientVoiceTimer = 200 + this.random.nextInt(400);
        if (this.getTarget() == null && !this.isInWater() && !this.isDeadOrDying()) {
            this.playSound(ModSounds.MOMO_AMBIENT.get(), 0.9F, 1.0F);
        }
    }

    private void tickIdle() {
        this.getNavigation().stop();
        tickAmbientVoice();

        // 附近有玩家：站定看玩家（商人待客）
        Player nearest = this.level().getNearestPlayer(this, 16.0);
        if (nearest != null) {
            this.getLookControl().setLookAt(nearest, 10.0F, 10.0F);
            // 20 格内的货币吸引：主动走向格赫罗斯残骸/矿石（及其主人）
            tickCurrencyLure();
            return;
        }
        // 无玩家：在生成点附近游走（≤20 格）+ 偶尔狩猎亡灵
        tickCurrencyLure();
        tickUndeadHunt();
        tickWander();
        // 复位逃跑标记
        if (this.getHealth() >= this.getMaxHealth() * 0.15f) {
            fleeTriggered = false;
            leapUsed = false;
            aggroSet.clear();
        }
    }

    /** 20 格内格赫罗斯残骸/矿石 → 走过去（不拾取） */
    private void tickCurrencyLure() {
        if (++currencyProbeTimer < 10) return;
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
        if (target == null) return;
        double dist = this.distanceTo(target);
        if (dist <= 1.6) {
            this.getNavigation().stop();
            Player p = this.level().getNearestPlayer(this, 8.0);
            if (p != null) {
                this.getLookControl().setLookAt(p, 10.0F, 10.0F);
            }
            if (this.random.nextInt(100) == 0) {
                this.playSound(ModSounds.MOMO_AMBIENT.get(), 0.8F, 1.0F);
            }
        } else {
            this.getNavigation().moveTo(target, 1.05);
        }
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
            this.setTarget(prey);
        }
    }

    /** 无玩家时在生成点 20 格内游走 */
    private void tickWander() {
        if (--wanderTimer > 0) return;
        wanderTimer = 80 + this.random.nextInt(80);
        if (homePos == null) homePos = this.blockPosition();
        double angle = this.random.nextDouble() * Math.PI * 2.0;
        double radius = this.random.nextDouble() * WANDER_RADIUS;
        double x = homePos.getX() + 0.5 + Math.cos(angle) * radius;
        double z = homePos.getZ() + 0.5 + Math.sin(angle) * radius;
        this.getNavigation().moveTo(x, homePos.getY(), z, 0.85);
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
