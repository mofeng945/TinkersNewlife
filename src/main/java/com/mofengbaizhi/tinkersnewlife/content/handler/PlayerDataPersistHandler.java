package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Set;

/**
 * §506／§693 **死亡重生时，把我们的"按玩家记"的数据搬到新玩家身上**。
 *
 * <p><b>§506 用户报的问题</b>：「玩家死亡后善恶值会重置」。
 * <b>根因（已确证）</b>：重生时 MC **新建一个 {@code ServerPlayer}** 再 {@code Player#restoreFrom(旧玩家)}
 * —— 那个方法只搬**原版字段**（背包、经验、效果、食物、分数…）⇒
 * **`persistentData`（Forge 给实体的那个通用 CompoundTag）不在搬运名单里** ✗。
 * 于是我们的数据（{@code tn_conscience} 善恶值、{@code tn_momo_favor} 墨默好感、各种计数/冷却）
 * 死一次**全部清零** ✗。
 *
 * <p><b>做法</b>：监听 {@link PlayerEvent.Clone}，把**属于我们的键**从旧玩家复制到新玩家 ✓
 * —— 用**前缀**而不是列举键名 ⇒ 以后新加的数据**自动覆盖** ✓ 不会再漏 ✗。
 *
 * <p>⚠ <b>唯一的例外</b>（§720）：<b>咒力核心池</b> {@code tinkersnewlife.curse_power}
 * 是「封咒瓶 → 呪蔵 → 核心池」三层里的**垫底临时存储**，**死亡必须清零** ✓
 * ⇒ 在 {@link #RESET_ON_DEATH} 里点名排除 ✓（封咒瓶/呪蔵里的那份分别写在物品 NBT 与 SavedData 里，
 * 不经过本类 ⇒ 照旧保留 ✓）。**别把这条"例外"当成漏网之鱼再补回前缀搬运** ✗。
 *
 * <h2>⚠ §693 用户报的问题：「无下限挡不住伤害了」</h2>
 * <b>根因（已确证）</b>：这里原来<b>只认 {@code tn_} 一个前缀</b> ✗，
 * 可后来新增的数据改用了别的前缀（`tnl_`／`tnl.`／`tinkersnewlife.`／`tinkersnewlife:`／`tinkersnewlife_`）✗
 * ⇒ 那些键**死一次就丢** ✗。
 * 最典型的就是无下限·无限的开关 —— {@link com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique#KEY_ACTIVE}
 * ＝ {@code tinkersnewlife.wuxian_active} ✗ —— 丢了以后 {@code isActive()} 恒为 false
 * ⇒ **{@code LivingHurtEvent} 里那段格挡直接不生效** ✗（玩家看到的就是"无下限挡不住伤害了" ✓）。
 * ⚠ 反过来说：那**不是**格挡逻辑本身坏了 ✗ —— 存档里那位玩家的 {@code wuxian_active} 键**整个不见了** ✓，
 * 而仍然存在的 `tinkersnewlife.wuwei_records`／`cursed_spirits`／`visited_dimensions` 都是
 * **功能跑起来时会自动重写**的键 ✓ ⇒ 正好印证"只有重生没被搬走的那些丢了" ✓。
 *
 * <p>⇒ **修法**：把前缀扩成下面这张**前缀表** ✓；
 * 以后再加新前缀，**在这里补一行**即可 ✓（别退回"只认某一个前缀" ✗）。
 *
 * <h2>⚠ 关于「换维度也会走 Clone」这句旧说明（§693 更正 ✗）</h2>
 * 换维度**不会**新建玩家对象（`ServerPlayer#teleportTo`／`changeDimension` 都是同一个实例 ✓）
 * ⇒ **不会**触发 {@code PlayerEvent.Clone} ✓。
 * 真正会走 Clone 的是**死亡重生**（{@code PlayerList#respawn → restoreFrom} ✓）。
 * （本次 §678~§691 的跨维度传送相关数据从来没经过这里 ✓。）
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerDataPersistHandler {

    private PlayerDataPersistHandler() {}

    /**
     * 我们所有"按玩家记"的数据都放在 `Entity#getPersistentData()` 里，键用以下几族前缀 ✓
     * （§693 按全仓检索补齐：`tn_` 28 处、`tnl_`＋`tnl.` 35 处、`tinkersnewlife.` 97 处、
     * `tinkersnewlife:` 若干、`tinkersnewlife_` 若干 ✓）。
     */
    private static final String[] PREFIXES = {
            "tn_",              // 最早统一使用的前缀（善恶值、事迹、墨默好感…）
            "tnl_",             // 后续新增：tnl_*（咒言、处刑、法杖挂件…）
            "tnl.",             // 术式/法杖参数：tnl.staff.goety.*
            "tinkersnewlife.",  // 咒力/术式/绑定等：tinkersnewlife.wuxian_active、curse_power…
            "tinkersnewlife:",  // 维度与名单类：tinkersnewlife:visited_dimensions…
            "tinkersnewlife_",  // 渲染/挂件类：tinkersnewlife_heart…
    };

    /**
     * ⭐ <b>死亡重生时不搬运（＝死亡即清零）的键</b> —— 目前只有**咒力核心池**一条 ✓。
     *
     * <p><b>为什么核心池要例外</b>（§720 用户报的问题）：咒力的存储分三层（见
     * {@link CursePowerHelper#addCurse} 的注释与物品文案 ✓）——
     * <ol>
     *   <li><b>封咒瓶</b>：咒力写在**物品 NBT**（{@code CurseBottleHelper.KEY_POWER}）里，
     *       文案明写「咒力优先存入瓶中，<b>死亡不丢</b>」✓ ⇒ 与本类无关，天然保留 ✓；</li>
     *   <li><b>呪蔵</b>：咒力写在 {@code CurseVaultData}（SavedData，按玩家 UUID 绑定）里 ✓
     *       ⇒ 同样不经过这里，天然保留 ✓；</li>
     *   <li><b>咒力核心池</b>：{@code setCurse/addCurse} 的**垫底临时存储**（{@code tinkersnewlife.curse_power}）——
     *       前两层满了才落到这里 ⇒ **死了就该清零** ✓。</li>
     * </ol>
     * 而本类原先按**前缀**无差别搬运（`tinkersnewlife.` 前缀覆盖了核心池 ✗）⇒ 核心池**永远不清零** ✗，
     * 导致"没戴封咒瓶、也没有呪蔵的玩家死一次，咒力还在" ✗（用户实测报告 ✓）。
     *
     * <p>⚠ 只对 {@code isWasDeath()} 的 Clone 生效 ✓：换维度/其它原因触发的 Clone 不该吞掉核心池 ✓。
     */
    private static final Set<String> RESET_ON_DEATH = Set.of(
            CursePowerHelper.KEY_CURSE          // "tinkersnewlife.curse_power"
    );

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData();
        if (from.isEmpty()) return;
        CompoundTag to = event.getEntity().getPersistentData();
        boolean wasDeath = event.isWasDeath();
        // 复制一份键集合再遍历 ✓（别在遍历 NBT 的同时动它）
        for (String key : new ArrayList<>(from.getAllKeys())) {
            if (!isOurs(key)) continue;
            // ⭐ 咒力核心池：死亡即清零（封咒瓶/呪蔵里的那份不在 persistentData 里，不受影响 ✓）
            if (wasDeath && RESET_ON_DEATH.contains(key)) continue;
            to.put(key, from.get(key).copy());
        }
    }

    /** 这个键是不是我们模组的玩家数据（§693：前缀表，别退回单前缀 ✗） */
    private static boolean isOurs(String key) {
        for (String prefix : PREFIXES) {
            if (key.startsWith(prefix)) return true;
        }
        return false;
    }
}
