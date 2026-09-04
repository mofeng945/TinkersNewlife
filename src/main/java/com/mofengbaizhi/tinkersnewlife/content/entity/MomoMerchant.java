package com.mofengbaizhi.tinkersnewlife.content.entity;
import com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen;

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
 * 涓珛瀹炰綋銆屾鍣ㄥ晢浜郝峰ⅷ榛樸€嶏細
 * <ul>
 *   <li>涓珛鍟嗕汉锛氭弧鏈堝鏅氱敱 {@code MomoMerchantHandler} 鍦ㄦ暀鍫傦紙鍗犱綅锛氭潙閽燂級鍓嶅埛鏂帮紱
 *       鑷劧鍒锋柊鐗堝湪鐧藉ぉ鍒版潵鏃舵秷澶憋紝鍒锋€泲鍙敜鐨勫父椹?/li>
 *   <li>鍙楀嚮鍙嶅嚮锛氭墜鎸佹牸璧綏鏂垬闀帮紱浠讳綍鏀诲嚮鑰咃紙鐜╁/鎬墿/鐩戝畧鑰咃級閮借兘鏀诲嚮濂癸紝濂逛篃浼氬弽鍑伙紱
 *       鐩戝畧鑰呭彲姝ｅ父绱㈡晫鏀诲嚮濂?/li>
 *   <li>鏀诲嚮 AI锛堣嚜瀹氫箟鐘舵€佹満锛夛細蹇€熸帴杩?鈫?闈㈠墠 2 鏍兼墖褰㈡í鏂?80%) 鈫?0.5s 鍚庣珫鍔?180%) 鈫?鎷夎繙锛?
 *       鍙楀嚮鍚?1s 鍐呮牸鎸★紙鍏嶇柅浼ゅ锛夛紝鏍兼尅鎴愬姛 鈫?杩戣韩杩炴柀 3 鍒€(60/80/100%)锛?
 *       鍗婅 鈫?楂橀珮璺冭捣璺冲妶(300%锛岀牬鐩? + 涔辫澏澶ф嫑锛涚敓鍛?鈮?% 鈫?閫冭窇</li>
 *   <li>灞炴€э細HP 200 / 鏀诲嚮 50 / 鎶ょ敳 14 / 鍐嶇敓 VIII锛堝父椹伙級</li>
 *   <li>鏃犵帺瀹舵椂鍦ㄧ敓鎴愮偣 20 鏍煎唴娓歌蛋锛涙瘡 500tick 10% 姒傜巼涓诲姩绱㈡晫骞跺嚮鏉€ 10 鏍煎唴涓€鍙骸鐏碉紱
 *       鍦颁笂鏈夋牸璧綏鏂畫楠?鐭跨煶锛?0 鏍煎唴锛変細琚惛寮曡蛋杩囨潵</li>
 *   <li>涓嶅彈鏃犱负杞彉褰卞搷锛堝彉褰㈢洰鏍囨帓闄わ級銆佸厤鐤泧鍙戝コ濡栫煶鍖栵紙鐢?GorgonImmunityHandler 澶勭悊锛?/li>
 *   <li>绉掓潃鎺夎惤锛氳涓€鍑讳激瀹?鈮?鏈€澶х敓鍛藉嚮鏉€鏃舵帀钀?鎷夎幈鑰剁殑鍛煎敜 脳1 + 15 缁忛獙</li>
 *   <li>鍞崠锛? 妲戒綅锛堝拻鍏访? / 鍜掓湳姘存櫠脳2 / 鏃ф棩閬楃墿脳2锛夛紝璐у竵涓烘牸璧綏鏂畫楠?鐭跨煶</li>
 *   <li>瀹㈡埛绔鐢ㄧ帺瀹舵ā鍨?+ momo_common 璐村浘锛涜闊宠蛋鏈ā缁勮嚜娉ㄥ唽闊虫晥锛堝崰浣嶆枃浠跺彲瑕嗙洊锛?/li>
 * </ul>
 */
public class MomoMerchant extends PathfinderMob implements MomoConst {

    // ===== 鐘舵€?=====
    private static final int S_SWEEP_WAIT = 2;   // 妯柀鍚庣瓑 0.5s 鍐嶇珫鍔?
    private static final int S_BACKOFF = 3;      // 鎵撳畬涓€濂楁媺杩?
    private static final int S_COUNTER = 4;      // 鏍兼尅鎴愬姛鍚庤繛鏂?3 鍒€
    private static final int S_LEAP_UP = 5;      // 楂橀珮璺冭捣
    private static final int S_ULT = 6;          // 涔辫澏澶ф嫑
    private static final int S_FLEE = 7;         // 鈮?% 琛€閫冭窇

    private static final int BLOCK_WINDOW = 20;      // 鍙楀嚮鍚?1s 鏍兼尅绐楀彛

    /** 鑷劧鍒锋柊鏈€澶ф父璧板崐寰?*/
    /** 浜＄伒鐙╃寧闂撮殧 / 姒傜巼 / 鍗婂緞 */
    /** 绌洪棽绉诲姩閫熷害 = 鏀诲嚮蹇€熸帴杩?1.35) 鐨?2/3锛堟父鑽?/ 琚揣甯佸惛寮曞叡鐢級 */
    /** A* 鎺㈢储涓婇檺锛堥槻鍗曟鍗￠】锛?*/

    // ===== 闆囦剑 / 姝屽敱 / 杩涢 =====
    /** 闆囦剑鏃堕暱锛氫竴涓父鎴忔棩锛?4000 tick锛夛紝鍒版湡"鍥炴潵鎵句綘" */
    /** 姝屽敱閫€鍚庢椂闀?/ 姝屽敱鎸佺画 10s / 鍐峰嵈 120s */
    /** 闆囦富琛€閲忎綆浜?60% 瑙﹀彂姝屽敱 */
    /** 鍐嶇敓 III = amplifier 2 */
    /** 鍗曟杩涢鎸佺画 2s = 40 tick锛涙瘡 100 tick 鍒ゅ畾涓€娆?30% 姒傜巼寮€濮嬭繘椋?*/
    /** 闆囦剑鍒版湡杩斿洖闆囦富鍚庯紝鐣欑粰闆囦富缁泧鐨勫闄愶紙鎷掔粷缁泧鍒欏埌鐐硅嚜鐒舵秷澶憋紱浠呰嚜鐒跺埛鏂扮殑澧ㄩ粯锛?*/
    /** 闆囦剑妯″紡鍩虹鏀诲嚮 20锛堟湭闆囦剑涓?50锛夛紱鏂╂潃闃堝€?<10 琛€ */
    /** 闆囦富璺濈瓒呰繃 50 鏍?鈫?鐩存帴浼犻€佸埌闆囦富韬竟 */

    // ===== 閫氱敤搴旀€ワ細浼ゅ鍚熷敱 / 浣庤澶ф柀鏉€ =====
    /** 5s(100tick) 绐楀彛鍐呯疮璁″彈浼?> 鍗婅 鈫?浼犻€佸畨鍏ㄤ綅骞跺悷鍞?1s锛岃浣忎激瀹崇被鍨嬶紝60s 鍐呭搴旂被鍨嬫姉鎬?+60% */
    private static final int CHANT_TICKS = 20;           // 鍚熷敱 1s
    private static final int RESIST_TICKS = 1200;        // 鎶楁€?60s
    private static final float RESIST_MULTIPLIER = 0.4F; // 鍙椾激闄嶄负 40%锛堟姉鎬ф彁鍗?60%锛?
    /** 琛€閲?<20% 鈫?瀵?50 鏍煎唴姣忎釜鐩爣鏂╂潃锛堥泧涓婚櫎澶栵級 */
    /** 鍗℃妫€娴嬶細鎸佺画鍙楀嚮 鈮?s 涓旂疮璁＄Щ鍔?鈮? 鏍?鈫?浼犻€佽嚦鐩爣韬悗 */
    /** 瀵圭┖璺虫柀锛氱洰鏍囨偓绌?楂樹簬澧ㄩ粯>2.2鏍间笖3D璺濈>3.2)涓旀按骞?0鏍煎唴 鈫?钃勫姏3s鍚庤烦鑷冲ご椤?娈佃繛鏂?*/
    private static final int AIR_CHARGE_TICKS = 60;          // 钃勫姏 3s

    // ===== 璇煶闃查噸鍙狅紙鑷姩瑙ｆ瀽 assets 鍐?ogg 鏃堕暱锛屾寜绫诲埆鏈€澶ф椂闀垮仛闂撮殧锛?=====
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

    /** 瑙ｆ瀽 OGG 鏃堕暱锛坓ranule / 閲囨牱鐜囷級锛屽け璐ヨ繑鍥?0 */
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

    /** 涓婁竴娆′换鎰忓ⅷ榛樿闊抽璁＄粨鏉熺殑 tick锛堢敤浜庨槻閲嶅彔锛氶棿闅?鈮?璇ョ被鍒渶闀胯闊虫椂闀匡級 */
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
    private int curseCleanseTick = 0;    private int aggroPruneTick = 0;

    // ===== 鍟嗕汉琛屼负 =====
    /** 鏄惁涓鸿嚜鐒跺埛鏂帮紙婊℃湀锛変骇鐢熺殑锛氱櫧澶╁埌鏉ユ椂娑堝け锛涘埛鎬泲涓?false 甯搁┗ */
    private boolean naturalSpawn = false;
    private boolean dayDespawnDone = false;
    /** 鐢熸垚鐐癸紙娓歌蛋閿氱偣锛?*/
    private BlockPos homePos = null;
    private int wanderTimer = 0;
    private int huntTimer = 0;
    private int ambientVoiceTimer = 0;
    private int wardenProbeTimer = 0;
    private int currencyProbeTimer = 0;

    // ===== A* 绌洪棽瀵昏矾鐘舵€侊紙娓歌崱 / 璐у竵鍚稿紩锛?=====
    private List<BlockPos> path = new ArrayList<>();
    private BlockPos pathGoalCell = null;
    /** 褰撳墠璺緞灞炰簬璐у竵鍚稿紩锛坱rue锛夎繕鏄父璧帮紙false锛夛紱娓呰矾寰勬椂鎸夊綊灞炲尯鍒?*/
    private boolean pathIsLure = false;
    private ItemEntity lureTarget = null;
    private Vec3 lastMovePos = null;
    private int noProgressTicks = 0;
    /** 浜ゆ槗鎴愬姛璇煶锛堢┖闂?锛夐槻閲嶅彔 */
    private int tradeSuccessVoiceEnd = Integer.MIN_VALUE / 2;

    // ===== 闆囦剑 / 姝屽敱 / 杩涢 鐘舵€?=====
    private boolean hired = false;        // 鏄惁澶勪簬闆囦剑鏈?
    private UUID employerId = null;
    private long hireUntilTick = -1;      // 闆囦剑鍒版湡鏃跺埢锛坓ameTime锛夛紝= 闆囦剑鏃?+ 24000
    private long offerDay = -1;           // 鍟嗗搧鎵规瀵瑰簲鐨?dayCount锛堟瘡澶╁埛鏂颁竴鎵癸級
    /** 闆囦剑鍒版湡杩斿洖闆囦富鍚庣殑缁泧瀹介檺鍊掕鏃讹紙鑷劧鍒锋柊鐗堬細鎷掔粷缁泧鍒欏埌鐐归殢澶╀寒娑堝け锛?*/
    private int returnGraceTicks = 0;
    private boolean singing = false;
    private int songBackoffTicks = 0;
    private int songTicks = 0;
    private int songCooldown = 0;
    /** 杩涢锛氬墿浣?tick锛?s=40锛変笌褰撳墠椋熺墿 */
    private int eatTicks = 0;
    private int eatCheckCooldown = 0;
    private Item eatFood = null;
    private java.util.List<Item> cachedFoods = null;

    // ===== 闆囦剑鎴樻枟锛堢嫭绔?AI锛夌姸鎬?=====
    private int hiredComboPhase = 0;   // 0 鎺ヨ繎/璧锋墜 | 1 绛?0.5s 绗簩鍒€ | 2 鎷夎繙
    private int hiredComboTimer = 0;
    private int hiredAttackCooldown = 0;
    private LivingEntity assistTarget = null;   // 闆囦富鏈€杩戞敾鍑荤殑鐩爣锛堝崗鍔╅泦鐏級
    private long assistTargetExpireAt = -1;     // 鍗忓姪鏈夋晥鏈燂紙娓告垙鏃堕棿锛屾寔缁敾鍑诲埛鏂帮級
    private boolean finisherActive = false;
    private LivingEntity finisherTarget = null;
    private int finisherIndex = 0;
    private int finisherTimer = 0;

    // ===== 閫氱敤搴旀€ョ姸鎬?=====
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
    private java.util.Set<String> chantTypes = null;   // 鍚熷敱瀹屾垚鏃惰浣忕殑浼ゅ绫诲瀷
    private long resistUntilTick = 0;
    private final java.util.Set<String> resistTypes = new HashSet<>();
    private boolean execBusy = false;
    private final List<java.util.UUID> execQueue = new ArrayList<>();
    private int execStage = 0;   // 0 鐬Щ | 1..3 杩炴柀
    private int execTimer = 0;
    private long execCooldownUntil = 0;

    /** 鐏肩儳娓呴攣鑺傛媿锛堢嫳鐒扮瓑鐏肩儳绫诲噺閫?瀹氳韩鏁堟灉浼氳鍛ㄦ湡鎬ф竻闄わ紝閬垮厤澧ㄩ粯琚儳鍒板仠浣忥級 */
    private int fireCleanseTick = 0;

    // ===== 鍗℃閫冪敓锛堟寔缁彈鍑讳絾鍑犱箮娌＄Щ鍔?鈫?浼犻€佺洰鏍囪韩鍚庯級 =====
    private Vec3 prevTickPos = null;
    private Vec3 stuckCheckPos = null;
    private int stuckCheckTicks = 0;
    private double stuckMoved = 0;
    private long lastDamageAtTick = -1000;
    private long stuckEscapeCooldown = 0;

    // ===== 瀵圭┖璺虫柀锛堣搫鍔?鈫?澶撮《 5 娈佃繛鏂╋級 =====
    private boolean airComboActive = false;
    private int airChargeTicks = 0;
    private int airHitIndex = 0;
    private int airHitTimer = 0;
    private long airComboCooldown = 0;

    /** 杩戞湡鏀诲嚮杩囧ス鐨勫疄浣擄紙鍙嶅嚮/澶ф嫑鍙墦杩欎簺浜猴紝涓嶄激鍙婃棤杈滐級 */
    private final Set<UUID> aggroSet = new HashSet<>();

    private ItemStack scytheStack = ItemStack.EMPTY;

    /** 鍞崠妲戒綅锛? 涓紝璺ㄥ瓨妗ｆ寔涔呭寲鍦ㄥ疄浣?NBT锛?*/
    public record Offer(ItemStack result, int price) {}

    private final List<Offer> offers = new ArrayList<>();

    public MomoMerchant(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    // ===== 杩涢鍔ㄧ敾鍚屾鏍囪锛堝鎴风鎹鏀惰捣涓绘墜鎴橀暟锛?=====
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_EATING =
            net.minecraft.network.syncher.SynchedEntityData.defineId(MomoMerchant.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_EATING, false);
    }

    /** 杩涢涓紙瀹㈡埛绔嵁姝ゆ敹璧蜂富鎵嬫垬闀帮級 */
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
    //  鐢熷懡鍛ㄦ湡 / 鎸佷箙鍖?
    // ============================================================

    @Override
    protected void registerGoals() {
        // 鍙楀嚮鍙嶅嚮锛氭妸鏀诲嚮鑰呰涓虹洰鏍囷紙鐜╁/鎬墿/鐩戝畧鑰呭潎鍙級
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

    /** 涓绘墜鎷挎牸璧綏鏂垬闀帮紙娓叉煋鍙锛夛紱鏉愯川鐢ㄦ牸璧綏鏂畫楠?*/
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
            // 鏉愯川鏈姞杞界瓑鏋佺鎯呭喌锛氱┖鎵嬩篃鍙紙鏀诲嚮鍔涙潵鑷睘鎬э紝涓嶅奖鍝嶆垬鏂楋級
        }
    }

    // ============================================================
    //  鍞崠妲戒綅
    // ============================================================

    /** 淇濊瘉 6 涓敭鍗栨Ы浣嶅凡鐢熸垚锛涙瘡澶╋紙dayCount 鍙樺寲锛夎嚜鍔ㄥ埛鏂颁竴鎵规柊鍟嗗搧锛堟棤浜ゆ槗涓婇檺锛?*/
    public void ensureOffers() {
        if (level().isClientSide) return;
        long day = currentDay();
        if (!offers.isEmpty() && offerDay == day) return;
        offers.clear();
        offerDay = day;
        RandomSource random = this.getRandom();

        // 1-2锛氬拻鍏锋睜浠婚€変袱涓紙澶╅€嗛壘 / 鐙遍棬鐤哰鏈皝鍗癩锛涚粨鐣岀鐗囦笉绠楀拻鍏凤級
        List<Item> cursedTools = new ArrayList<>();
        cursedTools.add(ModItems.TIAN_NI_HUO.get());
        cursedTools.add(ModItems.GOURD_JAIL.get());
        Collections.shuffle(cursedTools, new java.util.Random(random.nextInt()));
        offers.add(new Offer(new ItemStack(cursedTools.get(0)), 10 + random.nextInt(11)));   // 10-20
        offers.add(new Offer(new ItemStack(cursedTools.get(1)), 10 + random.nextInt(11)));

        // 3-4锛氬拻鏈按鏅讹紙roll 涓や釜鍜掓湳锛?
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

        // 5-6锛氭棫鏃ラ仐鐗╀换閫変袱涓紙涓€娆″崠涓€缁勶級
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

    /** 妲戒綅瀵瑰簲璐у竵锛?-3 鏍艰但缃楁柉娈嬮锛?-5 鏍艰但缃楁柉鐭跨煶 */
    public static Item currencyForSlot(int slot) {
        return slot >= 4 ? ModItems.GHELOTH_ORE.get() : ModItems.GHELOTH_REMAINS.get();
    }

    public enum BuyResult { OK, NO_OFFER, INSUFFICIENT, TOO_FAR, DEAD }

    public enum HireResult { HIRED, ALREADY_HIRED, HIRED_BY_OTHER, NO_ITEM, TOO_FAR, DEAD }

    /** 鐜╁鐐瑰嚮闆囦剑锛氭敮浠?1 涓媺鑾辫€剁殑鍛煎敜锛岄泧浣ｄ竴澶┿€傞泧浣ｆ湡闂村啀娆＄偣鍑讳笉鎵ｈ垂銆佺洿鎺ユ嫆缁濓紝闃叉閲嶅涓婁氦 */
    public HireResult hireFrom(ServerPlayer buyer) {
        if (level().isClientSide) return HireResult.DEAD;
        if (!this.isAlive() || this.isRemoved()) return HireResult.DEAD;
        if (buyer.distanceToSqr(this) > 8.0 * 8.0) return HireResult.TOO_FAR;
        // 宸插浜庨泧浣ｆ湡锛堟棤璁烘槸鍚︽湰浜猴級锛氭嫆缁濆啀娆℃敮浠?
        if (hired) {
            if (employerId != null && !employerId.equals(buyer.getUUID())) {
                return HireResult.HIRED_BY_OTHER;
            }
            return HireResult.ALREADY_HIRED;
        }
        if (countItem(buyer, ModItems.RLYEH_CALL.get()) < 1) return HireResult.NO_ITEM;
        consumeItem(buyer, ModItems.RLYEH_CALL.get(), 1);
        employerId = buyer.getUUID();
        hireUntilTick = this.level().getGameTime() + HIRE_DURATION_TICKS; // 闆囦剑涓€涓父鎴忔棩
        hired = true;
        returnGraceTicks = 0; // 闆囦剑鎴愬姛锛屽彇娑?澶╀寒娑堝け"瀹介檺
        this.setTarget(null);
        clearPath();
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.6, this.getZ(),
                    6, 0.3, 0.3, 0.3, 0.02);
        }
        return HireResult.HIRED;
    }

    /** 鐜╁鐐瑰嚮浜ゆ槗锛氭牎楠屽苟鎵ц璐拱锛堣揣甯佷粠鐜╁鑳屽寘/鍓墜鎵ｉ櫎锛?*/
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
    //  浜や簰锛氬彸閿墦寮€浜ゆ槗
    // ============================================================

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.sidedSuccess(true);
        if (this.getTarget() == player) return InteractionResult.PASS; // 姝ｅ湪鍙嶅嚮璇ョ帺瀹?
        ensureOffers();
        com.mofengbaizhi.tinkersnewlife.network.momo.PacketMomoOpen.sendTo((ServerPlayer) player, this);
        // 浜ゆ槗璇煶涓嶄笌鍏朵粬璇煶閲嶅彔锛堥棿闅?鈮?璇煶鏃堕暱锛?
        if (voiceReady()) {
            voicePlayed(VOICE_TIMINGS.trade);
            this.playSound(ModSounds.MOMO_TRADE.get(), 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(true);
    }

    /** 浜ゆ槗鎴愬姛锛氭挱鏀惧浐瀹?绌洪棽2"璇煶锛堟浛浠ｆ潙姘戦珮鍏村０锛夛紝鍙槻涓庤嚜韬繛缁挱鏀鹃噸鍙?*/
    public void playTradeSuccessSound() {
        if (level().isClientSide) return;
        if (this.tickCount < tradeSuccessVoiceEnd) return;
        tradeSuccessVoiceEnd = this.tickCount + (int) (VOICE_TIMINGS.ambient * 20f) + 10;
        this.playSound(ModSounds.MOMO_TRADE_SUCCESS.get(), 1.0F, 1.0F);
    }

    // ============================================================
    //  鎴樻枟杈呭姪锛堜激瀹?鑼冨洿锛?
    // ============================================================

    private float attackBase() {
        AttributeInstance attr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        return attr == null ? 50.0F : (float) attr.getValue();
    }

    /** 璇ュ疄浣撴槸鍚︿负澧ㄩ粯鐨?鏁屽鐩爣"锛堝彧鏀诲嚮浼ゅ杩囧ス鐨勫疄浣撲笌鍏跺綋鍓嶇洰鏍囷級 */
    private boolean isAggroTarget(LivingEntity e) {
        if (e == this || e.isSpectator()) return false;
        if (e == this.getTarget()) return true;
        if (this.getLastHurtByMob() == e) return true;
        return aggroSet.contains(e.getUUID());
    }

    /** 闈㈠墠鎵囧舰鍐呯殑鏁屼汉 */
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

    /** 鐮寸浘锛氭墦鏂帺瀹舵牸鎸″苟璁╃浘鐗岃繘鍏ュ喎鍗?*/
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
    //  鏈嶅姟绔?AI锛堢姸鎬佹満锛宼ick 椹卞姩锛?
    // ============================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        tickServer();
    }

    private void tickServer() {
        if (homePos == null) homePos = this.blockPosition();

        // 鑷劧鍒锋柊鐨勫ⅷ榛橈細鐧藉ぉ鍒版潵鏃舵秷澶憋紙闆囦剑涓?鍒版湡杩斿洖鍚庣殑缁泧瀹介檺鍐呬笉娑堝け锛?
        // 鍒锋€泲鍙敜鐨勫父椹伙紱鎷掔粷缁泧鍒欏闄愮粨鏉熼殢澶╀寒娑堝け锛?
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

        // 缁泧瀹介檺鍊掕鏃讹紙鑷劧鍒锋柊鐗堝埌鏈熸湭缁泧 鈫?鍒扮偣澶╀寒娑堝け锛?
        if (returnGraceTicks > 0 && !hired) {
            returnGraceTicks--;
        }

        // 鍐嶇敓 VIII锛堝父椹伙紝瑕嗙洊鏃х増鍐嶇敓 V锛?
        if (++regenTick % 20 == 0) {
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 7, false, false));
        }

        // 璇″巹璇呭拻(goety:cursed)娓呴櫎锛氫笅鐣屼簹娉鸡浼氱粰鐩爣鎸傝瘏鍜掞紝璇″巹浼氬彇娑堝甫璇呭拻瀹炰綋鐨勬墍鏈夊洖琛€
        // 锛堝ⅷ榛樼殑鍐嶇敓 VIII 浼氳绂侊級鈥斺€旀瘡 5 tick 娓呬竴娆★紝璁╁啀鐢熷缁堢敓鏁?
        if (++curseCleanseTick % 5 == 0) {
            net.minecraft.world.effect.MobEffect cursed = goetyCursedEffect();
            if (cursed != null && this.hasEffect(cursed)) {
                this.removeEffect(cursed);
            }
        }

        // 鐏肩儳娓呴攣锛氱嫳鐒扮瓑鐏肩儳绫荤Щ鍔ㄩ檺鍒舵晥鏋滀細璁╁ス鍋滀綇鈥斺€斿懆鏈熸€ф竻闄わ紙鐫€鐏椂杩為€氱敤鍑忛€熶篃娓咃級
        if (++fireCleanseTick % 10 == 0) {
            cleanseMovementLockEffects();
        }

        // 鍗℃閫冪敓锛氭寔缁彈鍑讳絾鍑犱箮娌＄Щ鍔?鈫?浼犻€佽嚦鐩爣韬悗锛堜笉鍦ㄥ悷鍞?鏂╂潃/姝屽敱/閫冭窇涓椂锛?
        if (chantTicks <= 0 && !execBusy && !finisherActive && !singing && !fleeTriggered) {
            tickStuckEscape();
        }

        // 铚樿洓寮忕埇澧?
        tickWallClimb();

        // 姘翠笅锛氭汉灏稿紡鎲嬫皵 + 娓告吵
        tickWaterSwim();

        // 浠囨仺娓呯悊
        if (++aggroPruneTick >= 100) {
            aggroPruneTick = 0;
            if (!aggroSet.isEmpty()) {
                aggroSet.removeIf(uuid -> {
                    Entity e = ((ServerLevel) this.level()).getEntity(uuid);
                    return e == null || !e.isAlive();
                });
            }
        }

        // 鐩戝畧鑰呭彲绱㈡晫澧ㄩ粯锛氶檮杩?16 鏍煎唴鏈夌洃瀹堣€?鈫?璁╁畠鐩笂濂癸紙濂逛篃浼氬弽鍑伙級
        if (++wardenProbeTimer >= 40) {
            wardenProbeTimer = 0;
            for (Warden warden : this.level().getEntitiesOfClass(Warden.class,
                    this.getBoundingBox().inflate(16.0), e -> e.isAlive())) {
                if (warden.getTarget() != this) {
                    warden.setTarget(this);
                }
            }
        }

        // 涓嬬晫浜氭尝浼?姝讳骸绠洦"鏍兼尅锛欰pollyon 蹇€熻繛灏勬湡闂磋嚜鍔ㄤ妇鐩撅紙绠煝琚牸鎸?鈫?鏁存绠洦鍏ㄥ厤锛?
        tickBarrageGuard();

        // 浣庤閲忛€冭窇
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

        // 閫氱敤搴旀€ワ細浼ゅ鍚熷敱 / 浣庤澶ф柀鏉€锛堥泧浣ｄ笌鏈泧浣ｉ€氱敤锛?
        if (tickResponseCore()) {
            return;
        }

        // 闆囦剑妯″紡锛氱嫭绔?AI锛堣窡闅忛泧涓?娓呬骸鐏?涓ゅ垁蹇呬腑/鏂╂潃/姝屽敱/杩涢锛?
        if (hired) {
            tickHiredAI();
            return;
        }

        // 鏈夌洰鏍囦絾娌℃湁杩涘叆浜ゆ垬 鈫?鑷姩寮€鎴橈紙鐜╁/鎬墿/鐩戝畧鑰呮敾鍑诲悗閮戒細璧板埌杩欓噷锛?
        if (state == S_IDLE) {
            LivingEntity t = this.getTarget();
            if (t != null && t.isAlive()) {
                stopEating(); // 寮€鎴樻墦鏂繘椋?
                state = S_ENGAGE;
            }
        }

        // 鐩爣娑堝け 鈫?鍥炲埌涓珛
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

        // 鏍兼尅绐楀彛鍊掕鏃?
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

    /** 鍟嗕汉澹伴煶锛堟棤鎴樻枟/鏃犵洰鏍囨椂鍋跺皵浣庤锛涗笉涓庝换鎰忚闊抽噸鍙狅紱闆囦剑鐘舵€佷笅鍚屾牱浼氳璇濓級 */
    private void tickAmbientVoice() {
        if (--ambientVoiceTimer > 0) return;
        ambientVoiceTimer = 200 + this.random.nextInt(400);
        if (voiceReady() && this.getTarget() == null && !this.isInWater() && !this.isDeadOrDying()) {
            voicePlayed(VOICE_TIMINGS.ambient);
            this.playSound(ModSounds.MOMO_AMBIENT.get(), 0.9F, 1.0F);
        }
    }

    /** 绌洪棽涓婚€昏緫锛氳揣甯佸惛寮曚紭鍏堬紱鍚﹀垯榛樿鎸佺画娓歌蛋锛堣繎韬帺瀹舵椂鎵嶇珯瀹氬緟瀹級 */
    private void tickIdle() {
        tickAmbientVoice();
        tickEatIfIdle();

        boolean moving = tickCurrencyLure();
        if (!moving) {
            // 闆囦剑涓細闆囦富鎷夊お杩?鈫?璺熶笂闆囦富锛堜繚闀栵級
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
                // 杩戣韩锛堝彲浜や簰璺濈锛夛細绔欏畾鐪嬬帺瀹讹紝鏂逛究浜ゆ槗
                this.getLookControl().setLookAt(nearest, 10.0F, 10.0F);
            } else {
                // 鍛ㄥ洿鏈夌帺瀹朵篃榛樿娓歌蛋锛涢檮杩戞棤鐜╁鏃舵墠瑙﹀彂浜＄伒鐙╃寧
                tickWanderPath();
                if (nearest == null) {
                    tickUndeadHunt();
                }
            }
        }
        // 澶嶄綅閫冭窇鏍囪
        if (this.getHealth() >= this.getMaxHealth() * 0.15f) {
            fleeTriggered = false;
            leapUsed = false;
            aggroSet.clear();
        }
    }

    /** 20 鏍煎唴鏍艰但缃楁柉娈嬮/鐭跨煶 鈫?A* 璧拌繃鍘伙紙涓嶆嬀鍙栵級锛涜繑鍥炴槸鍚﹁繕鍦ㄧЩ鍔?*/
    private boolean tickCurrencyLure() {
        if (++currencyProbeTimer < 10) {
            // 鎺㈡祴鍐峰嵈涓細姝ｅ湪杩借揣甯?鈫?缁х画娌胯矾寰勮蛋锛涘惁鍒欎笉鍔紙涓嶅姩娓歌蛋璺緞锛?
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
            // 娌℃湁璐у竵锛氬彧娓呰揣甯佽矾寰勶紝缁濅笉娓呮鍦ㄨ蛋鐨勬父璧拌矾寰?
            if (pathIsLure) {
                clearPath();
            }
            lureTarget = null;
            return false;
        }
        // 宸插埌璺熷墠锛氬仠涓嬬湅璐у竵涓讳汉
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

    /** 鏃犵帺瀹舵椂鍦ㄧ敓鎴愮偣 20 鏍煎唴娓歌蛋锛圓* 瀵昏矾锛夛紱杩斿洖鏄惁鍦ㄧЩ鍔?*/
    private boolean tickWanderPath() {
        if (!path.isEmpty()) {
            boolean moving = followPath(IDLE_MOVE_SPEED);
            if (!moving) {
                wanderTimer = 100 + this.random.nextInt(140); // 璧板畬涓€娈垫瓏涓€浼?
            }
            return moving;
        }
        if (--wanderTimer > 0) return false;
        if (homePos == null) homePos = this.blockPosition();
        // 閿氱偣锛氶檮杩戞湁鐜╁ 鈫?鍦ㄧ帺瀹跺懆鍥村皬鑼冨洿韪辨锛涙棤鐜╁ 鈫?鐢熸垚鐐?20 鏍煎唴娓歌蛋
        Player near = this.level().getNearestPlayer(this, 16.0);
        BlockPos center = near != null ? near.blockPosition() : homePos;
        double radius = near != null ? 6.0 : WANDER_RADIUS;
        for (int tries = 0; tries < 8; tries++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0;
            double r = this.random.nextDouble() * radius;
            BlockPos col = new BlockPos(
                    center.getX() + (int) Math.round(Math.cos(angle) * r),
                    this.blockPosition().getY(), // 浠ュ綋鍓嶆墍鍦ㄩ珮搴︿负鍩哄噯
                    center.getZ() + (int) Math.round(Math.sin(angle) * r));
            // 鐩爣鐐硅鏂瑰潡瑕嗙洊鏃犳硶鎶佃揪 鈫?y+1 缁х画鍚戜笂锛岀洿鍒版壘鍒板彲鎶佃揪鐐?
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
        wanderTimer = 60; // 鎵句笉鍒拌矾锛岀◢鍚庡啀璇?
        return false;
    }

    /** 浠庤鍒?base 楂樺害寮€濮嬶細鑻ヨ鏂瑰潡瑕嗙洊锛堜笉鍙珯绔嬶級鍒?y+1 鍚戜笂锛岀洿鍒版壘鍒板彲鎶佃揪鏍?*/
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
    //  A* 鍦伴潰瀵昏矾锛堟父鑽?/ 璐у竵鍚稿紩涓撶敤锛涙垬鏂椾粛鐢ㄥ師鐗堝鑸揩閫熸帴杩戯級
    // ============================================================

    private void clearPath() {
        path.clear();
        pathGoalCell = null;
        lureTarget = null;
        pathIsLure = false;
        lastMovePos = null;
        noProgressTicks = 0;
    }

    /** 璇ユ牸鍙綔涓虹珯绔嬫牸锛氳剼涓嬫槸瀹屾暣鏂瑰潡銆佽韩浣撲袱鏍煎唴鏃犵鎾炪€侀潪娴佷綋 */
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

    /** 鍦?p 鎵€鍦ㄥ垪涓婁笅鎵惧埌鍙珯绔嬫牸锛堝厛涓嬫帰鍐嶄笂鎺級锛屾壘涓嶅埌杩斿洖 null */
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

    /** 娌垮綋鍓?A* 璺緞鍓嶈繘锛圡oveControl 骞虫粦杞悜锛夛紱杩斿洖鏄惁浠嶅湪绉诲姩 */
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
            // 涓嬩竴鑺傜偣楂樹竴鏍硷細鎺ヨ繎鏃惰捣璺?
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

    /** A*锛氫粠 start锛堢珯绔嬫牸锛夊埌 goal锛堢珯绔嬫牸锛夛紝杩斿洖璺緞鑺傜偣锛堜笉鍚捣鐐癸級锛涘け璐ヨ繑鍥?null */
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
                // 鍥炴函璺緞
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
                    // 鍚岄珮搴?鈫?涓婅烦涓€绾?鈫?涓嬭蛋涓€绾?
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

    /** 姣?500tick 10% 姒傜巼锛氱储鏁?10 鏍煎唴涓€鍙骸鐏靛苟鍑绘潃锛堣繘鍏ヤ氦鎴樼姸鎬佺敱鐘舵€佹満澶勭悊锛?*/
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
                // 妯柀锛?0%锛夆啋 0.5s 鍚庣珫鍔堬紙180%锛?
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
        // 蹇€熸帴杩?
        this.getNavigation().moveTo(target, chaseSpeedWithBlock(1.35));

        // 鍗婅瑙﹀彂锛氶珮楂樿穬璧?鈫?璺冲妶锛?00%锛岀牬鐩撅級鈫?涔辫澏澶ф嫑
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
            // 绔栧妶锛?80%锛?
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

    /** 鏍兼尅鎴愬姛鍚庣殑杩炴柀锛?0/80/100%锛?*/
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
            // 鐩存帴璺冲悜鐩爣浣嶇疆钀藉湴 鈫?璺冲妶锛?00%锛岀牬鐩撅級
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
            // 涔辫澏澶ф嫑
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
    //  闆囦剑 / 姝屽敱 / 杩涢
    // ============================================================

    public boolean isHired() {
        return hired;
    }

    /** 闆囦富鏀诲嚮鐩爣閫氱煡锛堟湇鍔″櫒渚э紝鏀诲嚮浜嬩欢/绌块€忕粨绠楁椂璋冪敤锛夛細鍗忓姪闆嗙伀 4 绉掞紝鎸佺画鏀诲嚮浼氬埛鏂?*/
    public void notifyEmployerAttack(LivingEntity victim) {
        if (victim == null || victim.isRemoved() || !victim.isAlive()) return;
        if (victim == this || victim == getEmployer() || victim instanceof MomoMerchant) return;
        this.assistTarget = victim;
        this.assistTargetExpireAt = this.level().getGameTime() + 80;
    }

    /** 鍗忓姪鐩爣鏄惁浠嶆湁鏁堬紙瀛樻椿/鍦?64 鏍煎唴/鍦ㄦ湁鏁堟湡鍐咃級 */
    @Nullable
    private LivingEntity validAssistTarget() {
        if (assistTarget == null) return null;
        if (this.level().getGameTime() > assistTargetExpireAt
                || !assistTarget.isAlive() || assistTarget.isRemoved()
                || assistTarget == this || assistTarget == getEmployer()) {
            assistTarget = null;
            return null;
        }
        if (this.distanceToSqr(assistTarget) > 64.0 * 64.0) return null; // 澶繙涓嶈拷
        return assistTarget;
    }

    /** 闆囦富鏄剧ず鍚嶏紙GUI 鐢級 */
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

    /** 闆囦富褰撳墠濞佽儊锛氭敾鍑婚泧涓荤殑瀹炰綋锛屾垨闆囦富姝ｅ湪鏀诲嚮鐨勯潪鐜╁鐢熺墿锛?4 鏍煎唴锛?*/
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
     * 闆囦剑鏈?tick锛氬埌鏈燂紙鏂颁竴澶╃粨鏉燂級鍥炲埌闆囦富韬竟骞惰В闄ら泧浣ｏ紱
     * 涓庨泧涓讳竴鍚屾垬鏂楋細甯墦濞佽儊锛岄泧涓昏閲?<60% 涓旀瓕鍞卞喎鍗村ソ 鈫?杩滅鎴樺満绔欏畾姝屽敱銆?
     * 杩斿洖 true = 姝ｅ湪閫€鍚?姝屽敱锛堟湰 tick 鐙崰锛夈€?
     */
    private boolean tickHireAndSong() {
        ServerPlayer boss = getEmployer();
        if (boss == null || !boss.isAlive()) {
            return false; // 闆囦富绂荤嚎/姝讳骸锛氭寕璧风瓑寰?
        }
        // 鍒版湡杩斿洖锛堥泧浣ｄ竴涓父鎴忔棩鍚庡洖鏉ユ壘浣狅級
        if (this.level().getGameTime() >= hireUntilTick) {
            hired = false;
            employerId = null;
            singing = false;
            songTicks = 0;
            songBackoffTicks = 0;
            // 鑷劧鍒锋柊鐨勫ⅷ榛橈細缁欓泧涓荤煭鏆傜画闆囧闄愶紝鎷掔粷缁泧鍒欓殢澶╀寒娑堝け
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
        if (boss.level() != this.level()) return false; // 寮傜淮搴︽殏涓嶅鐞?
        // 姝屽敱娴佺▼
        if (singing || songBackoffTicks > 0) {
            tickSongBody(boss);
            return true;
        }
        if (songCooldown > 0) songCooldown--;
        // 绱㈡晫浼樺厛绾э細鏀诲嚮闆囦富鑰?> 闆囦富姝ｅ湪鏀诲嚮鐨勭敓鐗?> 闆囦富鍛ㄥ洿浜＄伒锛堜富鍔ㄦ竻浜＄伒锛?
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
            // 涓诲姩绱㈡晫闆囦富鍛ㄥ洿鐨勪骸鐏?鐏惧巹鏉戞皯
            Mob prey = nearestHostileNear(boss, 12.0);
            if (prey != null) {
                this.setTarget(prey);
            }
        }
        // 闆囦富琛€閲忎綆浜?60% 鈫?杩滅鎴樺満锛岄殢鍚庣珯瀹氭瓕鍞憋紙鍐嶇敓 III锛?
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

    /** 闆囦富鍛ㄥ洿鏈€杩戠殑涓€鍙骸鐏垫垨鐏惧巹鏉戞皯锛堜富鍔ㄧ储鏁屾竻鎬級 */
    @Nullable
    private Mob nearestHostileNear(LivingEntity center, double radius) {
        Mob best = null;
        double bestDist = radius * radius;
        ServerPlayer boss = center instanceof ServerPlayer sp ? sp : getEmployer();
        for (Mob m : this.level().getEntitiesOfClass(Mob.class, center.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != this && !PuppetUtil.isAllyOf(e, boss))) {
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

    /** 姝屽敱浣擄細鍏堥€€鍚?1s锛屽啀绔欏畾姝屽敱 10s锛堥泧涓昏幏寰楀啀鐢?III锛夛紝琚敾鍑绘垨瓒呮椂缁撴潫 */
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
        // 绔欏畾姝屽敱
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

    /** 琚敾鍑绘墦鏂瓕鍞憋紙杩涢涔熶細琚墦鏂級 */
    private void stopSinging() {
        if (singing || songBackoffTicks > 0) {
            singing = false;
            songTicks = 0;
            songBackoffTicks = 0;
            songCooldown = SONG_COOLDOWN_TICKS;
        }
        stopEating();
    }

    /** 闈炵储鏁屾椂杩涢锛氬垽瀹氬紑濮?鈫?鎸佺画 2s锛?0tick锛夎繘椋熷姩鐢伙紙绾姩鐢伙紝鏃犲鐩婏紱杩涢鏃舵敹璧锋垬闀帮級 */
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
    //  閫氱敤搴旀€ワ細浼ゅ鍚熷敱鎶楁€?/ 浣庤澶ф柀鏉€锛堥泧浣ｄ笌鏈泧浣ｉ€氱敤锛?
    // ============================================================

    public boolean isResistantTo(String type) {
        return this.tickCount < resistUntilTick && resistTypes.contains(type);
    }

    /** 鐏肩儳瀹氳韩绫昏礋闈㈡晥鏋滃澧ㄩ粯鏃犳晥锛堢嫳鐒?BURN_HEX 绛夛細鏂藉姞鍗虫嫆缁濓紝鏉滅粷琚儳鍒版棤娉曠Щ鍔級 */
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

    /** 浼ゅ浜嬩欢鍥炶皟锛堟湇鍔＄锛夛細鍏?5s 绐楀彛锛岀疮璁¤繃鍗婅 鈫?璇锋眰鍚熷敱 */
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

    /** 杩斿洖 true = 鏈?tick 琚簲鎬ュ姩浣滃崰鐢紙鍚熷敱 / 澶ф柀鏉€ / 瀵圭┖璺虫柀锛?*/
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
        // 瀵圭┖璺虫柀锛氱洰鏍囨偓绌哄涓嶅埌 鈫?钃勫姏 3s 鍚庤烦鍒板叾澶撮《 5 娈佃繛鏂?
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

    /** 钃勫姏 3s锛氱珯瀹氳搫鍔涳紝闅忓悗璺宠嚦鐩爣澶撮《 */
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
            // 钃勫姏锛氱珯瀹?+ 钃勫姏绮掑瓙
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
        // 澶撮《 5 娈佃繛鏂?
        if (airHitIndex >= AIR_HIT_MULTIPLIERS.length) {
            // 鏀跺熬锛氳惤鍦板苟缁撴潫杩炴
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

    /** 璺冲埌鐩爣澶撮《锛堟壘鍙珯绔?鏃犵鎾炵殑澶撮《浣嶇疆锛?*/
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
            // 澶撮《鏃犱綅缃細鏀惧純璇ヨ繛娈碉紙鏅€氳拷鍑伙級
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

    /** 鐩存帴钀藉埌鏈垪鏈€杩戠殑鍦伴潰涓婏紙闃查珮绌哄潬钀斤級 */
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

    /** 浼犻€佽嚦瀹夊叏浣嶇疆骞跺紑濮?1s 鍚熷敱 */
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

    /** 鍚熷敱閫氶亾锛氱珯瀹?1s锛岀粨鏉熻浣忎激瀹崇被鍨嬪苟鑾峰緱 60% 鎶楁€?60s */
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

    /** 闅忔満浼犻€佽嚦闄勮繎鏃犵鎾炵殑瀹夊叏钀界偣 */
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

    /** 璇ュ疄浣撴槸鍚︿负澧ㄩ粯鐨勫彲鏂╂潃鐩爣锛?0 鏍煎ぇ鏂╂潃鐢級 */
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

    /** 澶ф柀鏉€锛氶€愪釜鐬Щ鍒扮洰鏍囪韩鍚庤繛鏂╋紙绾㈣壊绮掑瓙锛?*/
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

    /** 褰撳墠鍩虹鏀诲嚮锛氶泧浣?20锛屾湭闆囦剑 50 */
    private float combatBase() {
        return hired ? HIRED_BASE_ATTACK : attackBase();
    }

    /** 鏍兼尅鏈熼棿杩藉嚮閫熷害锛氬噺閫熻嚦 60%锛堟牸鎸℃椂涔熻兘鍓嶈繘锛屽彧鏄彉鎱級 */
    private double chaseSpeedWithBlock(double base) {
        return isBlockingStance() ? base * 0.6 : base;
    }

    /** 鍙楀嚮鏍囪锛圡omoMerchantHandler 鍥炶皟锛?*/
    public void markDamaged() {
        this.lastDamageAtTick = this.tickCount;
    }

    /** 鍗℃閫冪敓锛氭渶杩?2s 鍐呮湁鍙楀嚮 + 鐩爣瀛樺湪锛屼笖 6s 绐楀彛绱绉诲姩 鈮? 鏍?鈫?浼犻€佽嚦鐩爣韬悗 */
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
        // 绱鏈?tick 浣嶇Щ锛堝拷鐣ヤ紶閫佺被澶т綅绉伙級
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

    /** 浼犻€佸埌鐩爣韬悗锛堢鎾炲垯閫€鍥為殢鏈哄畨鍏ㄨ惤鐐癸級 */
    private void teleportBehindTarget(LivingEntity target) {
        if (!(this.level() instanceof ServerLevel sl)) return;
        Vec3 dir = target.position().subtract(target.getLookAngle().scale(2.2));
        Vec3 behind = new Vec3(dir.x, target.getY(), dir.z);
        this.moveTo(behind.x, behind.y, behind.z, this.getYRot(), this.getXRot());
        this.fallDistance = 0;
        if (!sl.noCollision(this)) {
            teleportNearEntity(target); // 韬悗琚崰锛氶殢鏈轰紶閫侀檮杩?
            return;
        }
        sl.sendParticles(ParticleTypes.SNEEZE, this.getX(), this.getY() + 1.2, this.getZ(),
                14, 0.4, 0.5, 0.4, 0.02);
        sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.2F);
    }

    /**
     * 娓呴櫎"鐏肩儳閿佺Щ鍔?绫昏礋闈㈡晥鏋滐細
     * 鏁堟灉娉ㄥ唽鍚嶅惈 inferno/burn/bind/root/freeze/stun/paralysis/slow 绛夛紙鐙辩劙绫伙級鈫?浠讳綍鏃跺埢娓咃紱
     * 鍏朵粬璐熼潰鏁堟灉鑻ュ甫绉诲姩閫熷害璐熶慨姝ｏ紙鍑忛€熺被锛夆啋 浠呭湪澧ㄩ粯鐫€鐏椂娓呫€?
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
                // 鐫€鐏椂棰濆娓呴櫎甯︾Щ鍔ㄩ€熷害璐熶慨姝ｇ殑鏁堟灉锛堥€氱敤鍑忛€燂級
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
    //  闆囦剑妯″紡鐙珛 AI
    // ============================================================

    /** 闆囦剑鐘舵€佷富寰幆锛堜笌鏈泧浣?AI 瀹屽叏鍒嗗紑锛夛細濮嬬粓璺熼殢闆囦富銆佹竻浜＄伒銆佹垬鏂椾袱鍒€蹇呬腑+鏂╂潃銆佷繚鐣欐瓕鍞?杩涢 */
    private void tickHiredAI() {
        // 浣庤閫冭窇
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
        if (boss == null || !boss.isAlive()) return; // 闆囦富绂荤嚎/姝讳骸锛氬師鍦版寕璧?
        // 鍒版湡杩斿洖锛堥泧浣ｄ竴涓父鎴忔棩鍚庡洖鏉ユ壘浣狅級
        if (this.level().getGameTime() >= hireUntilTick) {
            returnToEmployer(boss);
            return;
        }
        if (boss.level() != this.level()) return; // 寮傜淮搴︽殏涓嶅鐞?
        // 姝屽敱锛堜繚鐣欙級
        if (singing || songBackoffTicks > 0) {
            tickSongBody(boss);
            return;
        }
        if (songCooldown > 0) songCooldown--;
        // 濮嬬粓璺熼殢闆囦富锛?50 鏍肩洿鎺ヤ紶閫?
        if (this.distanceToSqr(boss) > EMPLOYER_TELEPORT_DIST * EMPLOYER_TELEPORT_DIST) {
            teleportNearEntity(boss);
        }
        // 鏂╂潃杩涜涓?
        if (finisherActive) {
            tickFinisher();
            return;
        }
        LivingEntity target = this.getTarget();
        // 闆囦富琚敾鍑?鈫?浼樺厛绱㈡晫鏀诲嚮鑰?
        LivingEntity attacker = boss.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && attacker != this
                && boss.distanceToSqr(attacker) <= 24.0 * 24.0) {
            if (target != attacker) {
                this.setTarget(attacker);
                target = attacker;
            }
        }
        // 闆囦富姝ｅ湪鏀诲嚮鐨勭洰鏍?鈫?鍗忓姪闆嗙伀锛?
        // 淇濇姢闆囦富锛堝綋鍓嶇洰鏍囨槸鏀诲嚮闆囦富鑰咃級涓?Boss 鎴橈紙鍙楅檺 Boss 璧扮牬淇濇姢閾撅級浠嶄紭鍏堬紱
        // 绌洪棽鎴栧彧鏄嚜宸辨壘鐨勬潅楸肩洰鏍囨椂锛屽垏鎹㈠埌闆囦富姝ｅ湪鏀诲嚮鐨勭洰鏍?
        LivingEntity assist = validAssistTarget();
        if (assist != null && assist != target) {
            boolean defending = target != null && target.isAlive() && target == boss.getLastHurtByMob()
                    && boss.distanceToSqr(target) <= 24.0 * 24.0;
            boolean bossFight = target != null && target.isAlive()
                    && com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isDamageLimitedBoss(target);
            if (target == null || !target.isAlive() || (!defending && !bossFight)) {
                this.setTarget(assist);
                target = assist;
            }
        }
        // 璇″巹宸硶淇濇姢閾撅紙鐜鏈?Goety 鏃讹級锛氱洰鏍囨槸鍙楅檺 Boss 涓旇榛戞洔鐭虫煴淇濇姢 鈫?鍏堟墦閭暀寰掞紝鍏舵榛戞洔鐭虫煴锛屾渶鍚?Boss
        if (target != null && target.isAlive()
                && com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isDamageLimitedBoss(target)) {
            LivingEntity chain = pickGoetyChainTarget(boss);
            if (chain != null && chain != target) {
                engageChainTarget(chain); // 鏌卞お杩滐紙>12鏍硷級鐩存帴鐬Щ鍒版煴杈?
                target = chain;
            }
        }
        if (target != null && target.isAlive()) {
            tickHiredCombat(boss, target);
            return;
        }
        // 璇″巹宸硶锛氫富鍔ㄦ敾鍑婚粦鏇滅煶鏌?閭暀寰?鍙楅檺 Boss锛堢牬淇濇姢閾句紭鍏堬級
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isAvailable()) {
            LivingEntity goetyTarget = pickGoetyChainTarget(boss);
            if (goetyTarget != null) {
                engageChainTarget(goetyTarget);
                return;
            }
        }
        // 涓诲姩绱㈡晫闆囦富鍛ㄥ洿鐨勪骸鐏?鐏惧巹鏉戞皯
        Mob prey = nearestHostileNear(boss, 12.0);
        if (prey != null) {
            this.setTarget(prey);
            return;
        }
        // 姝屽敱瑙﹀彂锛氶泧涓讳綆琛€涓旀湁濞佽儊
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
        // 绌洪棽锛氫綆璇?+ 杩涢 + 浠ラ泧涓讳负涓績娓歌蛋
        tickAmbientVoice();
        tickEatIfIdle();
        if (this.distanceToSqr(boss) > 14.0 * 14.0) {
            this.getNavigation().moveTo(boss, 1.15);
        } else {
            tickWanderPath(); // 娓歌蛋閿氱偣 = 闆囦富锛堝崐寰?6 鏍硷級
        }
    }

    /**
     * 璇″巹宸硶锛堝彲閫夛級锛氫互闆囦富涓轰腑蹇冮€夌洰鏍団€斺€斾繚鎶ら摼 閭暀寰?> 榛戞洔鐭虫煴 > 鍙楅檺 Boss锛?
     * 闄勮繎瀛樺湪鍙楅檺 Boss(32鏍?鏃讹細鏌ュ叾榛戞洔鐭虫煴(64鏍煎唴瑙嗕负淇濇姢鏌憋紝鏌卞父琚彫鍦?2~24鏍煎
     * 涓斿彲鑳藉洜鍦哄湴涓嶈创韬?鈫掓煴鏃侀偑鏁欏緬浼樺厛锛涙棤 Boss 鏃朵篃涓诲姩鎵?40 鏍煎唴鐨勯偑鏁欏緬/榛戞洔鐭虫煴銆?
     * 鐜鏃?Goety 杩斿洖 null銆?
     */
    @Nullable
    private LivingEntity pickGoetyChainTarget(LivingEntity boss) {
        if (!com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isAvailable()) return null;
        LivingEntity limited = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestLimitedBoss(boss, 32.0);
        if (limited != null) {
            LivingEntity pillar = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestPillar(limited, 64.0);
            if (pillar != null) {
                LivingEntity cult = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.cultistNearPillar(pillar);
                if (cult != null) return cult;                       // 鈶?閭暀寰掞紙淇濇姢鏌辫€咃級
                return pillar;                                       // 鈶?榛戞洔鐭虫煴锛堢牬淇濇姢锛?
            }
            return limited;                                          // 鈶?鐩爣锛堟棤鏌变繚鎶ゅ垯鐩存帴鎵擄級
        }
        LivingEntity cult = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestCultist(boss, 40.0);
        if (cult != null) return cult;
        return com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.nearestPillar(boss, 40.0);
    }

    /** 鍒囧埌淇濇姢閾剧洰鏍囷細鐩爣涓洪粦鏇滅煶鏌变笖绂诲緱杩滐紙>12鏍硷級鏃剁洿鎺ョ灛绉诲埌鏌辫竟锛岄伩鍏嶈蛋涓嶈繃鍘?缁曡矾 */
    private void engageChainTarget(LivingEntity chain) {
        if (chain == null) return;
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isPillar(chain)
                && this.distanceToSqr(chain) > 12.0 * 12.0) {
            blinkBeside(chain);
        }
        this.setTarget(chain);
        this.getNavigation().stop();
    }

    /** 鐬Щ鍒扮洰鏍囪韩鏃侊紙鐮磋繙澶勯粦鏇滅煶鏌辩敤锛?*/
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

    /** 闆囦剑鎴樻枟锛氫袱鍒€锛?0%/180%脳20 蹇呬腑锛夆啋 鎷夎繙锛涘彈鍑绘牸鎸?鈫?鍙嶅嚮杩炴柀锛涚洰鏍?<10 琛€ 鈫?鐬Щ韬悗鏂╂潃 */
    private void tickHiredCombat(ServerPlayer boss, LivingEntity target) {
        // 鏍兼尅绐楀彛鍒版湡
        if (blockWindowUntil > 0 && this.tickCount > blockWindowUntil) {
            blockWindowUntil = -1;
        }
        // 鍙楀嚮鏍兼尅鎴愬姛 鈫?杩炴柀鍙嶅嚮
        if (state == S_COUNTER) {
            tickHiredCounter();
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 鏂╂潃锛氱洰鏍囩敓鍛?< 10
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
                        // 鐮嶄竴鍒€锛?0% 脳 20锛屽繀涓級
                        hiredHit(target, 0.8f);
                        hiredComboPhase = 1;
                        hiredComboTimer = 10; // 0.5s 鍚庣浜屽垁
                    }
                } else {
                    this.getNavigation().moveTo(target, chaseSpeedWithBlock(1.4));
                }
            }
            case 1 -> {
                // 绛?0.5s 鍚庣珫鍔堬紙180% 脳 20锛屽繀涓級
                if (--hiredComboTimer <= 0) {
                    hiredHit(target, 1.8f);
                    hiredComboPhase = 2;
                    hiredComboTimer = 24; // 鐮嶅畬鎷夎繙
                }
            }
            default -> {
                // 鎷夎繙
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

    /** 闆囦剑蹇呬腑涓€鍑伙紙鏃犺璺濈/闈㈠悜锛岀洿鎺ョ粨绠楋級 */
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
     * 澧ㄩ粯鐨勪激瀹崇粺涓€鍏ュ彛锛?
     * 鍑嬬伒鍑虹敓鏃犳晫 鈫?鏃犺锛?
     * 璇″巹宸硶鍙楅檺 Boss锛堜簹娉鸡/浣垮緬锛夊浜庨粦鏇滅煶鏌变繚鎶わ紙obsidianInvul>0锛屽叏绋嬪厤浼わ級鏃?鈫?
     * 澧ㄩ粯鎵撲笉鍑轰激瀹筹紝蹇呴』鍏堢牬鏌憋紙绱㈡晫閾惧凡鍒囧埌鏌?閭暀寰掞級锛屾澶勫彧鍋氭牸鎸″弽棣堬紱
     * 鏌辩牬鍚?鈫?瀵瑰叾闄愪激锛堝惎绀哄綍 apollyon_hurt_limit=20 绛変簨浠剁骇 clamp锛夌敤澶氭鎷嗗垎绐佺牬銆?
     */
    private void applyHurt(LivingEntity target, float dmg) {
        witherInvulnBypass(target);
        target.invulnerableTime = 0;
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isPillar(target)) {
            // 榛戞洔鐭虫煴锛氬ⅷ榛樼洿鎺ュ娈电┛閫忓嚮纰庯紙缁曟姢鐢?empowered 鍏嶄激锛屾煴閫氬父 50 琛€ 鈫?涓€涓ゅ垁纰庯級
            pierceDamageDirect(target, dmg);
            return;
        }
        if (com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.isDamageLimitedBoss(target)) {
            // 鏌变繚鎶や互 Boss 瀹炰綋鐨?obsidianInvul 璁℃椂涓哄噯锛堝瓨娲婚粦鏇滅煶鏌辨瘡 tick 缃?10锛夛紝
            // 姣旀寜璺濈鎵炬煴鏇存帴杩戠湡瀹炲厤浼ゅ垽瀹氾紙鏌辩灛绉昏创韬悗蹇呯劧 >0锛?
            int shield = com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.readObsidianInvul(target);
            if (shield > 0) {
                if (this.level() instanceof ServerLevel sl) {
                    sl.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.HOSTILE, 0.8F, 1.2F);
                    sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2,
                            target.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
                }
                return; // 鏌辨湭鐮达細鏈浼ゅ琚尅
            }
            pierceDamageDirect(target, dmg); // 鏌卞凡鐮达細澶氭鎷嗗垎绐佺牬闄愪激
        } else {
            target.hurt(this.damageSources().mobAttack(this), dmg);
        }
    }

    /**
     * 绐佺牬"姣忔浼ゅ涓婇檺"锛堝惎绀哄綍 apollyon_hurt_limit=20锛涗富 Goety apostleDamageCap=20
     * 鍥?genericKill 甯?bypasses_invulnerability 澶╃劧缁曡繃锛夛細
     * 涓嶈蛋鐩存敼琛€锛堜細搴熸帀鍙楀嚮浜嬩欢/闃舵閫昏緫锛夛紝鑰屾槸鎶婁激瀹虫媶鎴?鈮?9 鐨勫娈佃繛缁?hurt鈥斺€?
     * 姣忔閮藉湪涓婇檺涔嬩笅銆佽蛋瀹屾暣浼ゅ绠＄嚎锛堜簨浠?Boss 闃舵鐓у父瑙﹀彂锛夛紝绱鎬诲拰绐佺牬鍗曟涓婇檺銆?
     * 姣忔鍓嶉『甯︽竻闆讹細浣垮緬鍙楀嚮鏃犳晫甯?moddedInvul锛堝叾浠栧甫鐩存帴瀹炰綋鐨勬敾鍑讳細鐣欎笅 15tick 鎸′激锛?
     * 涓庡惎绀哄綍涓嬬晫 Apollyon 鐨勫彈鍑诲喎鍗达紙姣忔 actuallyHurt 缃?30銆佹湡闂?hurt() 琚洿鎺ュ彇娑堬級銆?
     * 鐩爣涓轰娇寰掓椂鎸?apostleDamageCompensation 鏀惧ぇ閫佸嚭閲忥紝鎶垫秷涓嬬晫鍑忎激(50%)涓?
     * 闄勮繎鐜╁鏃堕潪鐜╁浼ゅ鍑忓崐鈥斺€旇惤琛€浠嶆槸鍚嶄箟浼ゅ銆?
     */

    private void pierceDamageDirect(LivingEntity target, float dmg) {
        if (target.level().isClientSide || target.isRemoved()) return;
        // 鍏ㄩ绌块€忥細hurt 浜嬩欢 + 宸鐩磋ˉ锛堟€婚噺绮剧‘鍏ㄩ銆佷笉鍙楀厤鐤獥/鍗曟涓婇檺褰卞搷锛?
        com.mofengbaizhi.tinkersnewlife.util.GoetyBridge.pierceFullDamage(target, dmg);
    }

    /** 鍙楀嚮鏍兼尅鎴愬姛鍚庣殑鍙嶅嚮锛堥泧浣ｇ増锛?0/80/100% 脳 20锛屽繀涓級 */
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

    /** 鏂╂潃锛氱灛绉诲埌鏁屼汉韬悗锛岀孩鑹茬矑瀛愯繛鏂╂敹灏?*/
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
            // 绾㈣壊绮掑瓙锛堟柀鏉€鐗规晥锛?
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

    /** 鏃犺鍑嬬伒鍑虹敓鏃犳晫锛堟竻闆跺叾鏃犳晫璁℃椂瀛楁锛屽弽灏勫厹搴曪級 */
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
            // 鎵句笉鍒板瓧娈靛垯蹇界暐锛堥€€鍖栵細鏅€氭敾鍑讳粛鍙懡涓潪鏃犳晫绐楀彛锛?
        }
        // 鍑虹敓鏃犳晫鏈熼棿涓€骞惰В闄ゅ疄浣撴棤鏁屾爣璁帮紙鑻ュ叾鍩轰簬 isInvulnerable 瀹炵幇锛?
        try {
            w.setInvulnerable(false);
        } catch (Throwable ignored) {
        }
    }

    /** 浼犻€佽嚦鐩爣闄勮繎鐨勮惤鐐癸紙瀵绘壘鍙珯绔嬩綅缃紝甯﹀洖鍝嶇矑瀛愶級 */
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

    /** 闆囦剑鍒版湡锛氬洖鏉ユ壘闆囦富骞惰В闄ら泧浣ｏ紙鑷劧鍒锋柊鐗堢粰缁泧瀹介檺锛?*/
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
    //  鍙楀嚮 / 鏍兼尅 / 绉掓潃
    // ============================================================

    private LivingEntity lastBlockedBy;

    private LivingEntity getLastBlockedBy() {
        if (lastBlockedBy == null || !lastBlockedBy.isAlive() || lastBlockedBy.isRemoved()) {
            return null;
        }
        return lastBlockedBy;
    }

    /** 璇″巹璇呭拻鏁堟灉锛堟寜娉ㄥ唽琛?id 鎯版€ф煡鎵撅紝鏈璇″巹杩斿洖 null锛?*/
    private static net.minecraft.world.effect.MobEffect GOETY_CURSED_CACHE = null;
    private static boolean goetyCursedResolved = false;

    private static net.minecraft.world.effect.MobEffect goetyCursedEffect() {
        if (!goetyCursedResolved) {
            goetyCursedResolved = true;
            try {
                GOETY_CURSED_CACHE = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS
                        .getValue(new net.minecraft.resources.ResourceLocation("goety", "cursed"));
            } catch (Throwable ignored) {
            }
        }
        return GOETY_CURSED_CACHE;
    }

    /** 鏄惁澶勪簬鏍兼尅绐楀彛 */
    public boolean isBlockingStance() {
        return blockWindowUntil > 0 && this.tickCount <= blockWindowUntil && state != S_FLEE;
    }

    /**
     * 涓嬬晫浜氭尝浼?姝讳骸绠洦"鏍兼尅锛?
     * 褰撳墠鐩爣锛堟垨 32 鏍煎唴浣庨鎵弿鍒扮殑鍙楅檺 Boss锛夋澶勪簬鍚ず褰?Apollyon 鐨勭闆ㄦ柦鏀?
     * 锛坕sShooting锛岀害 100 tick銆佹瘡 tick 涓€绠級鏃讹紝鎸佺画鍒锋柊鏍兼尅绐楀彛鈥斺€?
     * 鏁存绠洦鏈熼棿涓剧浘鍏嶇柅锛氱鐭?hurt 琚牸鎸?鈫?鍏跺悗缁?5% 鏈€澶х敓鍛界殑铏氱┖鎵ｈ涔熶笉浼氳Е鍙戙€?
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
            blockWindowUntil = this.tickCount + BLOCK_WINDOW;
        }
    }

    /**
     * 鏍兼尅鏈熼棿杩?铏氱┖/鐪熶激"涔熶竴骞舵牸鎸★細涓嬬晫浜氭尝浼︾鐭㈠懡涓細棰濆
     * heal(-5%鏈€澶х敓鍛?锛堟棤瑙嗘姢鐢?鏃犳晫甯?鏍兼尅鐨勭洿鎺ユ墸琛€锛夆€斺€斾妇鐩炬椂鍏嶇柅銆?
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
        // 姝屽敱琚敾鍑绘墦鏂?
        stopSinging();
        // 鏍兼尅绐楀彛鍐咃細鍏嶇柅浼ゅ銆傝繎韬紙鈮?鏍硷級鏍兼尅鎴愬姛 鈫?杩炴柀鍙嶅嚮骞舵敹璧锋牸鎸★紱
        // 杩滅▼/鎶曞皠鐗╂牸鎸?鈫?鍒锋柊鏍兼尅绐楀彛锛堟寔缁皠鍑荤殑绠洦淇濇寔涓剧浘锛夛紝鍑忛€熸帹杩涗笉鎵撴柇杩藉嚮
        if (isBlockingStance()) {
            // 鏀诲嚮鑰呭垽瀹氾細鐩存帴杩戞垬瀹炰綋 / 鎶曞皠鐗╃殑鍙戝皠鑰?/ 鍏滃簳鍙栦激瀹虫簮瀹炰綋
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
                    // 杩戣韩鏍兼尅鎴愬姛锛氳繛鏂╁弽鍑?
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
                    // 杩滅▼鏀诲嚮琚牸鎸★細淇濇寔涓剧浘锛堝埛鏂扮獥鍙ｏ級+ 缁х画杩藉嚮锛堜互杈冩參閫熷害鎺ㄨ繘锛夛紝閬垮厤鍘熷湴鎰ｄ綇
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
                // 鏈煡鏉ユ簮锛堢幆澧冧激瀹崇瓑锛夛細浠嶈涓烘牸鎸★紝鍒锋柊绐楀彛
                blockWindowUntil = this.tickCount + BLOCK_WINDOW;
            }
            return false;
        }
        // 姝ｅ父鍙楀嚮锛氳褰曚粐鎭?+ 寮€鍚?1s 鏍兼尅绐楀彛
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

    /** 鏄惁琚?绉掓潃"锛堟渶鍚庝竴鍑讳激瀹?鈮?鏈€澶х敓鍛斤級 */
    private float lastDamageTaken = 0;

    public void recordDamageTaken(float amount) {
        this.lastDamageTaken = amount;
    }

    public boolean isOneShotKill() {
        return this.lastDamageTaken >= this.getMaxHealth();
    }

    // ============================================================
    //  姝讳骸 / 鎺夎惤 / 璇煶
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
        return null; // 浣庤鐢?tickAmbientVoice 鎺у埗
    }

    /** 鍙楀嚮璇煶锛氶伩鍏嶄笌鍏朵粬璇煶閲嶅彔锛堣繛鎵撴椂鍙挱涓€娆★級 */
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

    /** 铚樿洓寮忕埇澧欙細姘村钩璐村鍗冲彲鏀€鐖紙鍘熺増铚樿洓鍚屾鍒ゅ畾锛?*/
    @Override
    public boolean onClimbable() {
        return this.horizontalCollision;
    }

    /** 璐村鏃跺噺灏戜笅婊戯紱鐩爣/闆囦富鍦ㄦ洿楂樺涓旂揣璐村 鈫?鍚戜笂鏀€鐖?*/
    private void tickWallClimb() {
        if (this.level().isClientSide || this.isInWater() || !this.horizontalCollision) return;
        // 璐村锛氶檺鍒朵笅婊戦€熷害
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
            // 澶撮《鏈夌┖闂存墠涓婄埇锛堥槻鎸よ繘鏂瑰潡锛?
            if (this.level().noCollision(this.getBoundingBox().move(0.0, 1.0, 0.0))) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.28, 0.0));
                this.fallDistance = 0;
            }
        }
    }

    /** 姘翠笅锛氭汉灏稿紡鎲嬫皵鍛煎惛锛堜笉浼氱獟鎭級+ 娓告吵锛堟湞鐩爣娓革紱鏃犵洰鏍囨椂娴悜姘撮潰锛?*/
    private void tickWaterSwim() {
        if (this.level().isClientSide) return;
        if (!this.isInWater()) {
            // 鍑烘按锛氬叧闂父娉冲Э鎬?
            if (this.isSwimming()) {
                this.setSwimming(false);
            }
            return;
        }
        // 娓告吵濮挎€侊紙鐜╁妯″瀷浼氭挱鏀炬父娉冲姩鐢伙細鍓嶄几鍒掓按+鍙岃吙鎵撴按锛夛紱璐村湴/鑳界珯鏃朵笉鎽嗗Э鍔?
        this.setSwimming(!this.onGround());
        // 鎲嬫皵锛氬懆鏈熸€цˉ婊＄┖姘旓紝姘镐笉绐掓伅
        if (this.tickCount % 20 == 0 && this.getAirSupply() < this.getMaxAirSupply()) {
            this.setAirSupply(this.getMaxAirSupply());
        }
        // 寰诞鍔涳細閬垮厤涓€鐩存矇搴?
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
                double sp = 0.5; // 娓告吵閫熷害锛堝潡/tick锛夛細蹇簬鐜╁锛?.15锛?
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
            // 鏃犵洰鏍囷細娴悜姘撮潰闄勮繎锛岄伩鍏嶅憜鍦ㄦ按搴?
            double surface = this.level().getSeaLevel();
            if (this.getY() < surface - 1.0 && this.getDeltaMovement().y < 0.1) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.04, 0.0));
            }
        }
    }
}
