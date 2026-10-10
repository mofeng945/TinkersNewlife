package com.mofengbaizhi.tinkersnewlife.content.goety;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.item.ModularStaffItem;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;

/**
 * 模块化魔杖 · 模式（铁魔法 ⇄ 巫法）。
 *
 * <p>模式标记存魔杖 ModDataNBT（与亲和同机制）：{@link #MODE_IRON} / {@link #MODE_GOETY}。
 *
 * <p>§1078 <b>聚晶只存法杖本体槽</b> ✓ —— 巫法模式下本模组就是诡厄的"真法杖"
 * （{@code GoetyStaffItem implements IWand} ✓，{@code initCapabilities} 里挂诡厄原生
 * {@code SoulUsingItemHandler} ✓），聚晶由<b>诡厄本体</b>的聚晶轮盘（法杖本体槽 ⇄ 聚晶包对调）
 * 与聚晶包管理 ✓。本模组<b>不再</b>自研聚晶包/玩家持久数据/镜像/界面/网络包 ✗。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ModularStaffGoety {

    public static final int MODE_IRON = 0;
    public static final int MODE_GOETY = 1;

    private static final ResourceLocation KEY_MODE = key("staff_mode");

    private ModularStaffGoety() {}

    private static ResourceLocation key(String path) {
        return new ResourceLocation(TinkersNewlife.MOD_ID, path);
    }

    private static ToolStack tool(ItemStack stack) {
        return ToolHelper.getToolStack(stack);
    }

    // ================= 魔杖身份 / 模式 =================

    public static int getMode(ItemStack stack) {
        ToolStack t = tool(stack);
        return t == null ? MODE_IRON : t.getPersistentData().getInt(KEY_MODE);
    }

    private static void setMode(ItemStack stack, int mode) {
        ToolStack t = tool(stack);
        if (t == null) return;
        t.getPersistentData().putInt(KEY_MODE, mode);
        t.updateStack(stack);
    }

    @Nullable
    private static ItemStack heldStaff(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ModularStaffItem) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof ModularStaffItem) return off;
        return null;
    }

    /** 诡厄巫法是否加载（存在性判定统一走 ModList；真法杖类的加载由 IntegrationLoader 分支兜住） */
    public static boolean isGoetyLoaded() {
        return IntegrationLoader.isGoety();
    }

    // ================= 动作 =================

    public static void toggleMode(ServerPlayer player) {
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        int next = getMode(staff) == MODE_IRON ? MODE_GOETY : MODE_IRON;
        if (next == MODE_GOETY && !isGoetyLoaded()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.staff.goety_missing"), true);
            return;
        }
        setMode(staff, next);
        player.displayClientMessage(Component.translatable(next == MODE_GOETY
                ? "message.tinkersnewlife.staff.mode_goety"
                : "message.tinkersnewlife.staff.mode_iron"), true);
        // ⚠ 这里不再动聚晶：法杖本体槽是唯一权威（§1078），聚晶由诡厄轮盘/聚晶包管理。
    }

    public static boolean isStaffGoetyMode(Player player) {
        ItemStack staff = heldStaff(player);
        return staff != null && getMode(staff) == MODE_GOETY;
    }

    // ================= 事件 =================

    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;
        // Spell 属性增益：手持真法杖(巫法模式) → 按法杖强度刷新；否则清残留（值未变时内部跳过）
        refreshSpellAttrsTick(sp);
    }

    /** 手持真法杖（巫法模式）→ 按法杖强度给持有者上诡厄 Spell 属性；否则清空残留 */
    private static void refreshSpellAttrsTick(ServerPlayer sp) {
        if (!isGoetyLoaded()) return;
        ItemStack staff = heldStaff(sp);
        if (staff != null && !staff.isEmpty() && IntegrationLoader.isGoetyStaffItem(staff)) {
            IntegrationLoader.refreshSpellAttrs(sp, staff);
        } else {
            IntegrationLoader.clearSpellAttrs(sp);
        }
    }

    /** 死亡重生：瞬态 Spell 属性随旧实体销毁，清缓存让新实体按需重挂 */
    @SubscribeEvent
    public static void onPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (isGoetyLoaded()) {
                IntegrationLoader.clearSpellAttrs(sp);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack staff = heldStaff(player);
        if (staff == null) return;
        if (player.isShiftKeyDown()) {
            event.setCanceled(true);
            toggleMode(player);
            return;
        }
        // 巫法模式：真法杖形态（GoetyStaffItem implements IWand）→ 不取消，交给原版 use() →
        // GoetyStaffItem.use → 诡厄原生施法（长吟唱蓄力/冷却/灵魂全走原生管线，双端一致）；
        // 聚晶固定读法杖本体槽（SoulUsingItemHandler），本类不再接管施法。
    }
}
