package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 玉（Jade）联动：戴着<b>双向认知阻碍面具</b>的玩家，在玉的浮层里名字显示为 {@code ？？？}。
 *
 * <h2>注入点</h2>
 * Jade 的实体名由 {@code snownee.jade.addon.core.ObjectNameProvider#getEntityName(Entity)} 产出
 * （公共静态方法，返回名字 {@code Component}），在 {@code @At("RETURN")} 处改掉返回值即可 ✓ ——
 * 只影响"玉里那一行名字"，聊天栏 / Tab 列表 / 死亡信息等其它地方**一律不动** ✓
 * （所以不走"改 {@code getDisplayName}"那种大招 ✗）。
 *
 * <p>与 {@code XaeroRadarMixin} 同一套写法：Jade 的类名不混淆，用字符串 {@code targets} 指过去，
 * 本模组**不需要**把它的 jar 当运行时依赖（它本来就只是 {@code compileOnly}）✓；
 * 没装玉的环境（例如开发环境）目标类不会被加载 → 本 mixin 静默不生效 ✓，
 * 配置 {@code required=false} + {@code defaultInjectors: defaultRequire=0} 保证失败也不崩 ✓。
 */
@Mixin(targets = "snownee.jade.addon.core.ObjectNameProvider")
public abstract class JadeObjectNameMixin {

    /** 名字收尾处改值：面具佩戴者 → ？？？（其余原样放行 ✓） */
    @Inject(method = "getEntityName", at = @At("RETURN"), cancellable = true, remap = false)
    private static void tinkersnewlife$maskHiddenName(Entity entity, CallbackInfoReturnable<Component> cir) {
        if (entity instanceof Player player && CognitiveMaskItem.isWorn(player)) {
            cir.setReturnValue(Component.translatable("message.tinkersnewlife.cognitive_mask.hidden_name"));
        }
    }
}
