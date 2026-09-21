package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;

/**
 * §506 **死亡 / 换维度重生时，把我们的"按玩家记"的数据搬到新玩家身上**。
 *
 * <p><b>用户报的问题</b>：「玩家死亡后善恶值会重置」。
 * <b>根因（已确证）</b>：重生时 MC **新建一个 {@code ServerPlayer}** 再 {@code Player#restoreFrom(旧玩家)}
 * —— 那个方法只搬**原版字段**（背包、经验、效果、食物、分数…）⇒
 * **`persistentData`（Forge 给实体的那个通用 CompoundTag）不在搬运名单里** ✗。
 * 我们的数据全都存在那儿（{@code tn_conscience} 善恶值、{@code tn_momo_favor} 墨默好感、
 * 各种事迹/猎杀计数、{@code tn_unspeakable_next} 之类冷却）⇒ 死一次**全部清零** ✗（用户只发现了善恶值 ✓）。
 *
 * <p><b>做法</b>：监听 {@link PlayerEvent.Clone}，把**所有以 {@code tn_} 开头的键**从旧玩家复制到新玩家 ✓
 * —— 用**前缀**而不是列举键名 ⇒ 以后新加的数据**自动覆盖** ✓ 不会再漏 ✗。
 * （死亡与换维度都会走 Clone ✓ 两种情况本来就都该保留 ✓；若某个键将来真的需要"死亡重置"，
 * 在下面排除列表里点名即可 ✓。）
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerDataPersistHandler {

    private PlayerDataPersistHandler() {}

    /** 我们所有"按玩家记"的数据都放在 `Entity#getPersistentData()` 里，键统一以 `tn_` 开头 ✓ */
    private static final String PREFIX = "tn_";

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag from = event.getOriginal().getPersistentData();
        if (from.isEmpty()) return;
        CompoundTag to = event.getEntity().getPersistentData();
        // 复制一份键集合再遍历 ✓（别在遍历 NBT 的同时动它）
        for (String key : new ArrayList<>(from.getAllKeys())) {
            if (key.startsWith(PREFIX)) {
                to.put(key, from.get(key).copy());
            }
        }
    }
}
