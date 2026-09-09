package com.mofengbaizhi.tinkersnewlife.content.modifier.events;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.util.SoulEnergyBridge;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 灵魂获取增幅处理器：服务端每 tick 记录玩家灵魂能量，检测"本 tick 新增的灵魂"（Goety 攻击/击杀等自增），
 * 按玩家装备（主/副手 + 盔甲槽）上的相关修饰符补发增幅。为避免多个独立探测相互叠加，
 * 「噬魂」与「人屠」共用本处理器<b>同一个基线探测器</b>，本 tick 只探测一次、同一次结算：
 * <ul>
 *   <li>人屠（{@code butcher}，佩戴）：灵魂获取效率 ×2 → 补发 {@code 增量 × 1}（即再补一份）</li>
 *   <li>噬魂（{@code soul_eater}）：每级 +25% 灵魂获取 → 补发 {@code 增量 × 0.25 × 等级}，一级后额外 +1</li>
 * </ul>
 * 通过把「上次观察基线」更新到补发后的值，避免把本处理器补发的部分再次计入增量（不递归放大）。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SoulEaterHandler {

    private static final ModifierId SOUL_EATER = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID + ":soul_eater"));
    private static final ModifierId BUTCHER = new ModifierId(
            new ResourceLocation(TinkersNewlife.MOD_ID + ":butcher"));

    /** 上次观察到的灵魂能量基线（含本处理器补发部分） */
    private static final Map<UUID, Integer> LAST_SOULS = new HashMap<>();

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        int cur = SoulEnergyBridge.getSouls(sp);
        Integer prev = LAST_SOULS.put(sp.getUUID(), cur);
        if (prev == null) return;                       // 首次基线
        int delta = cur - prev;
        if (delta <= 0) return;                         // 仅有增量（获得灵魂）才增幅

        // 人屠：灵魂获取 ×2（把本 tick 自然增量再补一份）
        if (modifierLevelOnEquipment(sp, BUTCHER) > 0) {
            SoulEnergyBridge.addSouls(sp, delta);
        }

        // 噬魂：每级 +25%，一级后额外 +1（基于自然增量，而非×2后的值）
        int lv = modifierLevelOnEquipment(sp, SOUL_EATER);
        if (lv > 0) {
            int bonus = (int) Math.ceil(delta * 0.25 * lv) + 1;
            SoulEnergyBridge.addSouls(sp, bonus);
        }

        // 把基线更新到补发后的值，避免本次补发被下次当作增量
        LAST_SOULS.put(sp.getUUID(), SoulEnergyBridge.getSouls(sp));
    }

    /** 玩家装备（主/副手 + 盔甲槽）上指定修饰符的总等级（人屠/噬魂共用） */
    private static int modifierLevelOnEquipment(ServerPlayer sp, ModifierId id) {
        int total = 0;
        for (ItemStack stack : candidateStacks(sp)) {
            ToolStack tool = ToolHelper.getToolStack(stack);
            if (tool != null) {
                total += tool.getModifierLevel(id);
            }
        }
        return total;
    }

    private static java.util.List<ItemStack> candidateStacks(ServerPlayer sp) {
        java.util.List<ItemStack> list = new java.util.ArrayList<>(6);
        list.add(sp.getMainHandItem());
        list.add(sp.getOffhandItem());
        sp.getArmorSlots().forEach(list::add);
        return list;
    }
}
