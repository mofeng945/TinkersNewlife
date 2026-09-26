package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
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

    /** 某个键将来若真的需要"死亡重置"，在这里点名排除 ✓（目前为空 ✓） */
    private static final Set<String> EXCLUDED = Set.of();

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData();
        if (from.isEmpty()) return;
        CompoundTag to = event.getEntity().getPersistentData();
        // 复制一份键集合再遍历 ✓（别在遍历 NBT 的同时动它）
        for (String key : new ArrayList<>(from.getAllKeys())) {
            if (EXCLUDED.contains(key)) continue;
            if (!isOurs(key)) continue;
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
