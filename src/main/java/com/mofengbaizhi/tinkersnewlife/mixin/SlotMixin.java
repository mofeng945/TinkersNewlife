package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.technique.ConstructTechnique;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 构筑术式·拟造物「禁止手动放入容器」拦截。
 *
 * <p>为什么需要它：拟造物是临时投影，一旦被送进工作方块就能参与熔炼/合成等配方，
 * 把"临时物"洗成真材料（流体产物没有 NBT，事后追不回来）。掉在地上的拟造物已经在
 * {@code EntityJoinLevelEvent} 里直接消散、容器里的也会被全局扫描清掉，
 * 但"玩家手动把拟造物拖进箱子/熔炉/机器"这条路只靠扫描是"塞进去 1 秒后才消失"，
 * 观感与安全性都差 —— 干脆在槽位层面直接拒绝，物品老老实实留在手里/背包里。
 *
 * <p>判定：{@link ConstructTechnique#denyContainerSlot}（只拒绝"非玩家背包"的槽位）。
 *
 * <p>两个注入器与 {@code EntityRenderDispatcherMixin} 同一套路：MCP 名（靠手写 refmap 解析成 SRG）
 * ＋ SRG 名直连兜底。{@code injectors.defaultRequire=0} 意味着注入失败是<b>静默</b>的，
 * 所以命中时打一条一次性日志，方便确认 Hook 真的在跑。
 */
@Mixin(Slot.class)
public abstract class SlotMixin {

    /** 诊断用：拦截生效只打一条日志 */
    private static boolean tinkersnewlife$logged = false;

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void tinkersnewlife$denyConstructed(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        tinkersnewlife$checkDeny(stack, cir);
    }

    /** 兜底注入：直连 SRG 方法名，绕开 refmap */
    @Inject(method = "m_5857_", at = @At("HEAD"), cancellable = true, remap = false)
    private void tinkersnewlife$denyConstructedSrg(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        tinkersnewlife$checkDeny(stack, cir);
    }

    private void tinkersnewlife$checkDeny(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        try {
            Slot self = (Slot) (Object) this;
            Container container = self.container;
            if (ConstructTechnique.denyContainerSlot(container, stack)) {
                if (!tinkersnewlife$logged) {
                    tinkersnewlife$logged = true;
                    TinkersNewlife.LOGGER.info("[构筑] Slot 拦截 Hook 已生效：拟造物无法放入非玩家背包的槽位");
                }
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // 任何异常都退回原逻辑，绝不影响正常槽位行为
        }
    }
}
