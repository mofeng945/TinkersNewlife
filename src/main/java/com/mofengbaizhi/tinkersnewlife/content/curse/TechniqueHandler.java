package com.mofengbaizhi.tinkersnewlife.content.curse;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.YuchuziTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHandler;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.BaseTechnique;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.Modifiers;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 术式注册表（服务端）
 * <p>
 * 管理所有已实现术式：术式修饰符 → 术式实例（后续新术式继承 {@link BaseTechnique} 后在此登记）。
 * <p>
 * 多术式支持：每个玩家记住当前选中的术式（{@link #SELECTED}），切换按键按核心修饰符顺序循环，
 * 释放按键只释放当前选中的术式；当前术式随 {@code PacketSyncCurse} 同步到客户端 HUD 显示。
 * 未选中或选中的术式已不在核心上时，自动回退到第一个术式。
 */
@Mod.EventBusSubscriber(modid = TinkersNewlife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TechniqueHandler {

    private static final Map<ModifierId, BaseTechnique> TECHNIQUES = new ConcurrentHashMap<>();
    /** 每个玩家当前选中的术式 id（按核心修饰符列表顺序循环） */
    private static final Map<UUID, ModifierId> SELECTED = new ConcurrentHashMap<>();

    private TechniqueHandler() {}

    /** 注册术式：修饰符 ID → 术式实例（在 TinkersNewlife 初始化时调用） */
    public static void register(BaseTechnique technique) {
        TECHNIQUES.put(technique.getModifierId(), technique);
    }

    /** 全部已注册术式修饰符 id（供剥离/槽位配方等遍历；新增术式自动包含） */
    public static java.util.Set<ModifierId> getAllTechniqueIds() {
        return TECHNIQUES.keySet();
    }

    /** 该修饰符是否为本模组已注册的术式 */
    public static boolean isTechnique(ModifierId id) {
        return TECHNIQUES.containsKey(id);
    }

    /** 按键按下：熔断检查（反转术式豁免）→ 封印检查 → 技巧禁用检查 → 当前选中的术式 → 按下行为（即时释放 / 开始蓄力） */
    public static void onKeyPress(ServerPlayer player) {
        // ⭐ 新阴流技巧：弥虚葛笼/简易领域激活期间禁用术式
        if (com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler.blocksTechnique(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.skill.no_technique"), true);
            return;
        }
        // 先取选中术式：熔断期间「反转术式」等豁免术式仍可使用（isBurnoutExempt）
        BaseTechnique technique = findSelected(player);
        if (technique != null && !technique.isBurnoutExempt() && CursePowerHelper.isBurnout(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.burnout.active",
                    CursePowerHelper.getBurnoutRemainingSeconds(player)), true);
            return;
        }
        if (CursePowerHelper.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.sealed.active",
                    CursePowerHelper.getSealedRemainingSeconds(player)), true);
            return;
        }
        if (technique != null) {
            technique.onKeyPress(player);
        }
    }

    /** 按键松开：当前选中的术式 → 松开行为（蓄力术式发射） */
    public static void onKeyRelease(ServerPlayer player) {
        BaseTechnique technique = findSelected(player);
        if (technique != null) {
            technique.onKeyRelease(player);
        }
    }

    /** 术式反转按键按下（F）：封印检查 → 当前选中的术式 → 反转行为（如无下限·苍 → 赫） */
    public static void onReverseKeyPress(ServerPlayer player) {
        if (com.mofengbaizhi.tinkersnewlife.content.curse.skill.SkillHandler.blocksTechnique(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.skill.no_technique"), true);
            return;
        }
        if (CursePowerHelper.isSealed(player)) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.sealed.active",
                    CursePowerHelper.getSealedRemainingSeconds(player)), true);
            return;
        }
        BaseTechnique technique = findSelected(player);
        if (technique != null) {
            technique.onReverseKeyPress(player);
        }
    }

    /** 术式反转按键松开（F） */
    public static void onReverseKeyRelease(ServerPlayer player) {
        BaseTechnique technique = findSelected(player);
        if (technique != null) {
            technique.onReverseKeyRelease(player);
        }
    }

    /**
     * 切换按键：把当前选中的术式循环到有效术式列表的下一个（真赝相爱领域内=本存档已解锁术式，
     * 平时=核心上的术式；列表末尾回到第一个）。切换后提示并立即同步 HUD。
     */
    public static void onSwitch(ServerPlayer player) {
        List<ModifierId> techniques = effectiveTechniques(player);
        if (techniques == null) {
            // ⭐ 未佩戴咒力核心时按键静默（不再弹提示）
            return;
        }
        if (techniques.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_trait"), true);
            return;
        }
        UUID uuid = player.getUUID();
        ModifierId current = SELECTED.get(uuid);
        int index = current != null ? techniques.indexOf(current) : -1;
        ModifierId next = techniques.get((index + 1) % techniques.size());
        // 切换术式时取消草木操术的顺转蓄力（防止切回后残留旧蓄力）
        com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cancelCharge(player);
        SELECTED.put(uuid, next);
        player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.switched",
                getDisplayName(next)), true);
        CursePowerHandler.syncToClient(player);
    }

    /** 静默获取当前应选术式 id（无有效列表返回 null）；未选中或选中失效时自动补选第一个 */
    @Nullable
    public static ModifierId getSelectedTechniqueId(ServerPlayer player) {
        List<ModifierId> techniques = effectiveTechniques(player);
        if (techniques == null || techniques.isEmpty()) return null;
        UUID uuid = player.getUUID();
        ModifierId selected = SELECTED.get(uuid);
        if (selected == null || !techniques.contains(selected)) {
            selected = techniques.get(0);
            SELECTED.put(uuid, selected);
        }
        return selected;
    }

    /**
     * 有效术式列表：真赝相爱领域开启期间 = 本存档已帕秋莉解锁的全部术式；
     * 平时 = 佩戴核心上的术式（无核心返回 null）。
     */
    @Nullable
    private static List<ModifierId> effectiveTechniques(ServerPlayer player) {
        if (BORROW_ORIGINAL.containsKey(player.getUUID())) {
            return unlockedTechniques(player);
        }
        return getTechniquesOnCore(player);
    }

    // ==================== 真赝相爱：借术式模式 ====================

    /** 借术式模式：玩家 → 进入领域前的原选中术式（存此键 = 处于借术式模式） */
    private static final Map<UUID, ModifierId> BORROW_ORIGINAL = new ConcurrentHashMap<>();

    /** 该玩家当前是否处于"真赝相爱借术式"模式 */
    public static boolean isBorrowing(ServerPlayer player) {
        return BORROW_ORIGINAL.containsKey(player.getUUID());
    }

    /** 进入真赝领域：记录原选中并进入借术式模式（保持当前术式不变） */
    public static void enableBorrow(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (BORROW_ORIGINAL.containsKey(uuid)) return;
        ModifierId original = SELECTED.get(uuid);
        BORROW_ORIGINAL.put(uuid, original);
        // 若从未选中，补选当前有效列表（解锁集）第一个
        List<ModifierId> list = unlockedTechniques(player);
        if (SELECTED.get(uuid) == null && !list.isEmpty()) {
            SELECTED.put(uuid, list.get(0));
        }
    }

    /** 离开真赝领域（关闭/死亡/登出）：恢复原选中术式并退出借术式模式；
     *  同时关闭借用（核心上没有对应术式）期间开启的持续性术式状态 */
    public static void disableBorrow(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ModifierId original = BORROW_ORIGINAL.remove(uuid);
        if (original == null) {
            SELECTED.remove(uuid);
        } else {
            SELECTED.put(uuid, original);
        }
        // ⭐ 借用状态结束：借来的持续术式效果一并关闭（自己核心自有的不受影响）
        closeBorrowedSustained(player);
        CursePowerHandler.syncToClient(player);
    }

    /**
     * ⭐ <b>真赝相爱领域结束后的统一收尾</b>（修复"借来的无下限在领域结束后仍然开启"）：
     * <ol>
     *   <li><b>自动切换到身上咒力核心佩戴的第一个术式</b>（没有核心/核心上没有术式 → 清空选中）；</li>
     *   <li><b>停止身上所有持续性/开关型术式</b>（不再区分"是否装在核心上"——领域里借来的状态一律收掉）。</li>
     * </ol>
     * 与 {@link #disableBorrow} 的区别：那个只关"核心上没有的"术式，而且依赖进入领域前记录的原选中；
     * 本方法是不依赖任何历史状态的兜底收尾，因此对"切换顺序异常 / 领域内死亡 / 核心被换掉"等情况同样有效。
     */
    public static void resetAfterDomain(ServerPlayer player) {
        if (player == null) return;
        // 1) 选中：核心上的第一个术式
        List<ModifierId> onCore = getTechniquesOnCore(player);
        if (onCore != null && !onCore.isEmpty()) {
            SELECTED.put(player.getUUID(), onCore.get(0));
        } else {
            SELECTED.remove(player.getUUID());
        }
        // 2) 停掉所有持续性术式
        stopAllSustained(player);
        BORROW_ORIGINAL.remove(player.getUUID());
        CursePowerHandler.syncToClient(player);
        if (player.isAlive()) {
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.domain.cleanup_after_borrow"), true);
        }
    }

    /**
     * 停止该玩家身上<b>所有</b>持续性 / 开关型 / 蓄力型术式状态（逐一调用各术式自己的收尾方法，
     * 全部幂等；与 {@link #closeBorrowedSustained} 不同，这里<b>不看术式是否在核心上</b>）。
     */
    public static void stopAllSustained(ServerPlayer player) {
        if (player == null) return;
        try {
            // 无下限·无限（开关型）
            if (com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.isActive(player)) {
                com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.deactivate(player);
            }
            // 反重力机构·压力场
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.AntiGravityTechnique.cleanup(player);
            // 构筑术式：无限弹药 / 拟造中
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.cleanup(player);
            // 咒言 / 咒灵
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpeechTechnique.cleanup(player);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique.cleanup(player);
            // 灶·开
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique.cleanup(player);
            // 雷电操术：幻兽琥珀解放 + 收尾
            if (com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.isReleased(player)) {
                com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.deactivate(player);
            }
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.cleanup(player);
            // 草木操术：蓄力 + 收尾
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cancelCharge(player);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cleanup(player);
            // 投影术式：自身眩晕状态
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.ProjectionTechnique.endStun(player);
            // 傀儡操术：视角转移
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique.cleanup(player);
            // 十划咒法：标记
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenDivideTechnique.cleanup(player);
            // 无下限·苍/赫 与 宇宙子：蓄力
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangCangTechnique.cancelCharge(player);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.YuchuziTechnique.cancelCharge(player);
            // 黑鸟操术：操控结束、视角回归
            if (com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique.findActiveBird(player) != null) {
                com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique.sealRecall(player);
            }
            // 无为转变·转变外放开关（变形本体不强制解除：那涉及属性/生命还原，交给它自己的流程）
            if (com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.isReversalActive(player)) {
                com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.setReversal(player, false);
            }
            TinkersNewlife.LOGGER.debug("[术式] 已停止 {} 的所有持续性术式状态",
                    player.getName().getString());
        } catch (Throwable t) {
            TinkersNewlife.LOGGER.warn("[术式] 收尾持续性术式时出现异常: {}", t.toString());
        }
    }

    // ============================================================
    //  ⭐ 咒力核心被摘下 / 被换掉 → 立刻收掉持续性术式
    // ============================================================

    /** 上一次检查到的「佩戴核心指纹」（物品 + 术式列表），用于检测摘下/换核心 */
    private static final Map<UUID, String> CORE_FINGERPRINT = new ConcurrentHashMap<>();

    /**
     * 每 10 tick 检查一次佩戴的咒力核心：
     * <ul>
     *   <li><b>核心被摘下</b> → 立刻停止身上所有持续性/开关型术式（无限屏障、压力场、无限弹药、
     *       黑鸟操控、拟造物、变形本体……都算「持续性状态」），并清空当前选中；</li>
     *   <li><b>核心被换成另一个</b> → 只停「新核心上没有的」持续状态，选中回退到新核心的第一个术式。</li>
     * </ul>
     * 用轮询而不是监听某个槽位：摘下核心的路径太多（背包移动 / 饰品栏 / 死亡掉落 / 被清除等），
     * 轮询才能全覆盖。领域不必管：{@code DomainRegistry} 每 tick 用 {@code isValid} 校验核心，会自己关。
     */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 10 != 0) return;
        String now = coreFingerprint(player);
        String prev = CORE_FINGERPRINT.put(player.getUUID(), now);
        if (prev == null || prev.equals(now)) return;
        if (now.isEmpty()) {
            // 核心被摘下：所有持续性术式一并解除（变形本体也算；「被他人转变」不由自己的核心负责）
            if (com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.isTransformed(player)
                    && !com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.isForcedTransform(player)) {
                com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.endTransformPublic(player);
            }
            stopAllSustained(player);
            SELECTED.remove(player.getUUID());
            BORROW_ORIGINAL.remove(player.getUUID());
            CursePowerHandler.syncToClient(player);
            player.displayClientMessage(Component.translatable(
                    "message.tinkersnewlife.technique.core_removed"), true);
            TinkersNewlife.LOGGER.info("[术式] {} 摘下咒力核心：已解除全部持续性术式",
                    player.getName().getString());
        } else {
            // 换了核心：停掉新核心上没有的持续状态，选中回退到新核心的第一个术式
            closeBorrowedSustained(player);
            List<ModifierId> onCore = getTechniquesOnCore(player);
            ModifierId sel = SELECTED.get(player.getUUID());
            if (onCore != null && !onCore.isEmpty() && (sel == null || !onCore.contains(sel))) {
                SELECTED.put(player.getUUID(), onCore.get(0));
            }
            CursePowerHandler.syncToClient(player);
        }
    }

    /** 佩戴核心的指纹：物品注册名 + 其上的术式列表；没有核心返回空串 */
    private static String coreFingerprint(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(
                String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(core.getItem())));
        List<ModifierId> list = getTechniquesOnCore(player);
        if (list != null) {
            for (ModifierId id : list) sb.append('|').append(id);
        }
        return sb.toString();
    }
    /** 玩家核心上是否装有该术式 modifier */
    private static boolean hasOnCore(ServerPlayer player, ModifierId id) {
        List<ModifierId> onCore = getTechniquesOnCore(player);
        return onCore != null && onCore.contains(id);
    }

    /**
     * 关闭"借来的"（核心上没有对应术式）持续/开关型术式状态：
     * 真赝领域借术式模式下玩家可以开启这些状态，领域关闭后它们本应随之结束，
     * 否则会残留（如无限屏障常开、压力场常开、无限弹药模式常开、黑鸟操控不回、雷电解放常驻等）。
     */
    private static void closeBorrowedSustained(ServerPlayer player) {
        // 无下限·无限（开关型）
        if (!hasOnCore(player, Modifiers.WULIANG_WUXIAN.getId())
                && com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.isActive(player)) {
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.WuliangWuxianTechnique.deactivate(player);
        }
        // 反重力机构·压力场（开关型）
        if (!hasOnCore(player, Modifiers.ANTI_GRAVITY.getId())
                && com.mofengbaizhi.tinkersnewlife.content.curse.technique.AntiGravityTechnique.isFieldActive(player)) {
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.AntiGravityTechnique.cleanup(player);
        }
        // 构筑术式：无限弹药模式 / 拟造中
        if (!hasOnCore(player, Modifiers.CONSTRUCT.getId())
                && (com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.isAmmoModeOn(player)
                || com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.isForging(player))) {
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.cleanup(player);
        }
        // 黑鸟操术：操控中的黑鸟结束、视角回归
        if (!hasOnCore(player, Modifiers.BLACK_BIRD.getId())
                && com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique.findActiveBird(player) != null) {
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.BlackBirdTechnique.sealRecall(player);
        }
        // 雷电操术·幻兽琥珀解放（开关型）
        if (!hasOnCore(player, Modifiers.LIGHTNING_MANIPULATION.getId())
                && com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.isReleased(player)) {
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.deactivate(player);
        }
        // 咒力外放·冰沙冲击激光（进行中）
        if (!hasOnCore(player, Modifiers.CURSED_ENERGY_RELEASE.getId())
                && com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedEnergyReleaseTechnique.isBlasting(player)) {
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedEnergyReleaseTechnique.cancelBlast(player);
        }
        // 无为转变·转变外放开关（借来可开外放，领域结束应关闭）
        if (!hasOnCore(player, Modifiers.WU_WEI.getId())
                && com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.isReversalActive(player)) {
            com.mofengbaizhi.tinkersnewlife.content.curse.WuWeiHandler.setReversal(player, false);
        }
    }

    /**
     * 该玩家本存档内"帕秋莉已解锁"的术式列表：
     * 以 techniques/&lt;path&gt; advancement 是否完成为准（佩戴装有该术式核心即自动解锁）。
     */
    public static List<ModifierId> unlockedTechniques(ServerPlayer player) {
        List<ModifierId> out = new ArrayList<>();
        net.minecraft.server.ServerAdvancementManager manager =
                player.server.getAdvancements();
        net.minecraft.server.PlayerAdvancements pa = player.getAdvancements();
        for (ModifierId id : getAllTechniqueIds()) {
            net.minecraft.resources.ResourceLocation adv = new net.minecraft.resources.ResourceLocation(
                    TinkersNewlife.MOD_ID, "techniques/" + id.getPath());
            net.minecraft.advancements.Advancement holder = manager.getAdvancement(adv);
            if (holder != null && pa.getOrStartProgress(holder).isDone()) {
                out.add(id);
            }
        }
        return out;
    }

    /** 取佩戴核心上已注册的术式 id 列表（按修饰符列表顺序）；无核心返回 null */
    @Nullable
    private static List<ModifierId> getTechniquesOnCore(ServerPlayer player) {
        ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
        if (core.isEmpty()) return null;
        ToolStack tool = ToolHelper.getToolStack(core);
        if (tool == null) return null;
        List<ModifierId> list = new ArrayList<>();
        for (ModifierEntry entry : tool.getModifierList()) {
            if (TECHNIQUES.containsKey(entry.getId())) {
                list.add(entry.getId());
            }
        }
        return list;
    }

    /** 当前选中的术式实例；无核心/无术式时提示并返回 null */
    private static BaseTechnique findSelected(ServerPlayer player) {
        ModifierId selected = getSelectedTechniqueId(player);
        if (selected == null) {
            ItemStack core = CursePowerHelper.findEquippedCurseCore(player);
            if (core.isEmpty()) {
                // ⭐ 未佩戴咒力核心时按键静默（不再弹提示）
            } else {
                player.displayClientMessage(Component.translatable("message.tinkersnewlife.technique.no_trait"), true);
            }
            return null;
        }
        return TECHNIQUES.get(selected);
    }

    /** 术式显示名（modifier.<命名空间>.<路径> 本地化键） */
    private static Component getDisplayName(ModifierId id) {
        return Component.translatable(slimeknights.tconstruct.library.utils.Util.makeTranslationKey("modifier", id));
    }

    /** 玩家登出：取消进行中的蓄力并清空选中记录 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            YuchuziTechnique.cancelCharge(sp);
            WuliangCangTechnique.cancelCharge(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedEnergyReleaseTechnique.cancelBlast(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpeechTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.AntiGravityTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenDivideTechnique.cleanup(sp);
            BORROW_ORIGINAL.remove(sp.getUUID());
            SELECTED.remove(sp.getUUID());
        }
    }

    /** 玩家死亡：取消进行中的蓄力，正在操控的傀儡立即消散、视角回归 */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            YuchuziTechnique.cancelCharge(sp);
            WuliangCangTechnique.cancelCharge(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PuppetTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.PlantManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.FlameManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpiritTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.LightningManipulationTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedEnergyReleaseTechnique.cancelBlast(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.CursedSpeechTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.AntiGravityTechnique.cleanup(sp);
            com.mofengbaizhi.tinkersnewlife.content.curse.technique.TenDivideTechnique.cleanup(sp);
        }
    }

    /**
     * 玩家"永久进度"持久数据键：死亡重生/跨维度克隆时 Forge 不会自动携带 persistentData，
     * 必须手动拷给新实体，否则复活后调伏的式神、学会的咒言等全部回退默认。
     */
    private static final java.util.Set<String> PROGRESS_KEYS = java.util.Set.of(
            "tinkersnewlife.tamed_shikigami",   // 十影术式：已调伏式神位掩码
            "tnl_cursed_learned",               // 咒言术：已学词条
            "tnl_cursed_chant",                 // 咒言术：当前六段组合
            "tinkersnewlife.wuwei_records",     // 无为转变：击杀记录形态列表
            "tinkersnewlife.wuwei_selected");   // 无为转变：选中形态

    /** 死亡重生（Clone）：把"永久进度"键从旧实体拷给新实体（幂等，传送克隆也安全） */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        net.minecraft.nbt.CompoundTag src = event.getOriginal().getPersistentData();
        net.minecraft.nbt.CompoundTag dst = newPlayer.getPersistentData();
        boolean copied = false;
        for (String key : PROGRESS_KEYS) {
            if (src.contains(key)) {
                dst.put(key, src.get(key).copy());
                copied = true;
            }
        }
        if (copied) {
            TinkersNewlife.LOGGER.info("[术式] 玩家 {} 重生，已保留式神调伏/咒言/记录进度",
                    newPlayer.getName().getString());
        }
    }
}
