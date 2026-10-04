package com.mofengbaizhi.tinkersnewlife.content.handler;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>百宝书 ⇄ 「古旧书袋」桥</b>（§910s，用户方案的 A ✓）。
 *
 * <p>用户口径：「**a，但是不要给开启书袋的能力，吞进去就不能取出来了，
 * 并且保留之前写的吞噬启示之证等书会带有攻击属性的能力**」✓
 *
 * <h3>为什么这样做才"泛用"</h3>
 * 「古旧书袋」是 [神秘遗物扩展] {@code enigmaticaddons} 的物品 ✓，它的内容物存在
 * **玩家身上的 capability** 里（{@code AntiqueBagCapability.INVENTORY}
 * : {@code Capability<IAntiqueBagHandler>} ✓ 已反编译确认 ✓）。
 * 别的 mod 想知道"玩家身上有没有某本书"时，**查的就是这条通道** ✓ ——
 * 铁证：诡厄遗物里的 {@code EnigmaticAddonsBookBagHelper}
 * （{@code getAllItems(player)} / {@code hasItem(player, id)} / {@code collectItems(...)} ✓）。
 *
 * <p>⇒ 我们把吞掉的书**写进这条通道**，所有已经与书袋集成的 mod 就会自然看见它们 ✓
 * （不用再一家一家镜像效果 ✓）。
 *
 * <h3>用户明确要求的边界（都已遵守 ✓）</h3>
 * <ul>
 *   <li><b>不提供"打开书袋"的能力</b> ✓ —— 本类只做"写入" ✓，
 *       不注册任何按键、物品交互或界面（百宝书的右键界面仍只列已吞的书 ✓）；</li>
 *   <li><b>吞进去就取不出来</b> ✓ —— 本类**没有任何"取出/回吐"逻辑** ✓，
 *       百宝书自己的记录也依旧只增不减 ✓（那条 §910m 的"刷新"也只是刷新元数据 ✓）；</li>
 *   <li><b>保留攻击属性继承</b> ✓（§910j 的 {@code getAttributeModifiers}/{@code hurtEnemy} 原样不动 ✓）。</li>
 * </ul>
 *
 * <p>⚠ 如实说明一个我改不了的细节：书袋**自己的界面**由那个 mod 画 ✗ ——
 * 我们写进去的栈如果落在它显示的槽位上，玩家**打开自己的书袋**时是可能看见/拿出来的 ✗
 * （我们无法改它的 GUI ✓）。你若不希望这样，我可以改成方案 B（自建隐藏 capability ＋
 * 公开 helper，等别的 mod 来集成 ✓）—— 说一声即可 ✓。
 *
 * <p>⚠ 反射只用在**那个 mod 自己的类/方法**上（mod 类不被混淆 ✓ 名字稳定 ✓）；
 * 对 Forge/MC 的调用（{@code player.getCapability}）是**编译期直调** ✓ 不走反射 ✓
 * —— 否则发布包里 SRG 名一变就崩 ✗。
 */
public final class CompendiumAntiqueBagBridge {

    private static final Logger LOG = LoggerFactory.getLogger("TinkersNewlife/Compendium");

    /** 诊断只打一次 ✓ */
    private static boolean tnl$logged;

    private CompendiumAntiqueBagBridge() {}

    /**
     * 把吞噬掉的那本书**原样写进玩家身上的书袋**（写满或没书袋就静默跳过 ✓）。
     *
     * @param book 被吞噬的那本书（**调用方要在 shrink 之前传进来** ✓）
     */
    public static void deposit(ServerPlayer player, ItemStack book) {
        if (book.isEmpty()) return;
        if (!ModList.get().isLoaded("enigmaticaddons")) return;
        try {
            Class<?> capCls = Class.forName(
                    "auviotre.enigmatic.addon.contents.objects.bookbag.AntiqueBagCapability");
            Object raw = capCls.getField("INVENTORY").get(null);        // Capability<IAntiqueBagHandler>
            if (!(raw instanceof Capability<?> capability)) return;

            Object handler = player.getCapability((Capability) capability).orElse(null);
            if (handler == null) {
                if (!tnl$logged) {
                    tnl$logged = true;
                    LOG.info("[百宝书] §910s 玩家身上没有书袋 capability ⇒ 本次跳过（百宝书自己的记录照旧 ✓）");
                }
                return;
            }

            Object invRaw = handler.getClass().getMethod("getInventory").invoke(handler);
            if (!(invRaw instanceof IItemHandlerModifiable inv)) return;

            for (int i = 0; i < inv.getSlots(); i++) {
                if (inv.getStackInSlot(i).isEmpty()) {
                    inv.insertItem(i, book.copyWithCount(1), false);
                    inv.setStackInSlot(i, book.copyWithCount(1));     // 双保险（有的实现 insert 语义不同 ✓）
                    LOG.info("[百宝书] §910s 已把《{}》写入书袋槽 {}（只写不读 ✓ 百宝书不提供取出 ✓）",
                            book.getHoverName().getString(), i);
                    return;
                }
            }
            LOG.info("[百宝书] §910s 书袋已满 ⇒ 这本书只记在百宝书里 ✓");
        } catch (Throwable t) {
            LOG.warn("[百宝书] §910s 写书袋失败（跳过，百宝书自己的记录不受影响 ✓）：{}", t.toString());
        }
    }
}
