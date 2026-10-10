package com.mofengbaizhi.tinkersnewlife.mixin;

import com.mofengbaizhi.tinkersnewlife.config.ModConfig;
import com.mofengbaizhi.tinkersnewlife.content.item.CognitiveMaskItem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Xaero's 小地图雷达：把戴着<b>认知阻碍面具</b>的玩家从雷达实体列表里剔掉。
 *
 * <h2>为什么走这里，而不是给玩家挂"隐身标记"</h2>
 * 需求是"<b>人看得见</b>，但小地图不显示、无名字、索敌不到"。MC 里"雷达不显示/无名字/锁不到"
 * 和"看不见人"共用 {@code Entity#isInvisible()} 这一个开关 —— 挂上它之后：
 * <ul>
 *   <li>本模组在渲染层能把身体画回来（原版路径 ✓），**但接管玩家渲染的模组（YSM / 是，史蒂夫模型）
 *       自己读那个标记、模型照样被藏掉** ✗ —— 用户实测正是如此（"不渲染 ysm 时没隐身，但 ysm 还是把模型隐藏了"）；
 *   <li>YSM 的类是**混淆名**（{@code com/elfmcys/yesstevemodel/o0000OOo0000oo000OOOo0o0}），
 *       往它身上注入又脆又脏 ✗。</li>
 * </ul>
 * 所以改成<b>不碰玩家身上任何状态</b>，只在雷达这一层定点过滤 ✓：人（原版/YSM/任何渲染模组）照常可见 ✓，
 * 名牌由 {@code CognitiveMaskClientHandler} 挡、索敌由 {@code CognitiveMaskHandler} 挡 ✓。
 *
 * <h2>注入点与为什么选它</h2>
 * {@code RadarStateUpdater#update} 用 {@code Iterable#iterator()} 遍历"要画进雷达的实体"，
 * 我们 redirect 这一次迭代、返回一个跳过面具佩戴者的迭代器 ✓。这样：
 * <ul>
 *   <li><b>绕开它自己的开关</b>：原本的跳过条件是 {@code if (hide_invisible_entities && isInvisibleTo(...))}
 *       —— 玩家身上没有隐身标记时那条根本不成立 ✗，而"隐藏隐身实体"又是客户端设置、改它等于动用户配置 ✗；
 *   <li><b>不依赖 Xaero's 的 API</b>：它没有可编程接口，但它<b>类名不混淆</b>（{@code xaero.hud...}），
 *       用字符串 {@code targets} 指过去即可，本模组**不需要**把它的 jar 当编译依赖 ✓；
 *   <li>只过滤"是玩家且戴面具"的条目，别的集合迭代（例如内部 Map 的 entrySet）不受影响 ✓；
 *   <li>配置 {@code cognitive_mask.hide_from_radar=false} 时直接返回原迭代器（放弃雷达隐藏 ✓）。</li>
 * </ul>
 *
 * <p>⚠ 没装 Xaero's 的环境（例如开发环境）里，这个目标类根本不会被加载 → 本 mixin 静默不生效 ✓；
 * 本配置 {@code required=false}、{@code defaultRequire=0}，即使目标缺失/版本改名也只是日志提示，不会崩 ✓。
 * 代价：Xaero's 若在将来改掉 {@code update}/{@code iterator} 结构，这条会静默失效（雷达重新显示你）✗。
 */
@Mixin(targets = "xaero.hud.minimap.radar.state.RadarStateUpdater")
public abstract class XaeroRadarMixin {

    /** 官方名（Xaero's 自身类名不混淆，用字符串 target + remap=false 直连） */
    @Redirect(method = "update",
            at = @At(value = "INVOKE", target = "Ljava/lang/Iterable;iterator()Ljava/util/Iterator;"),
            remap = false)
    private Iterator<?> tinkersnewlife$skipMaskedPlayers(Iterable<?> source) {
        Iterator<?> original = source.iterator();
        if (!ModConfig.COGNITIVE_MASK_HIDE_FROM_RADAR.get()) {
            return original;
        }
        return new MaskedSkippingIterator(original);
    }

    /** 跳过"戴着认知阻碍面具的玩家"的惰性迭代器（其余条目原样透传 ✓） */
    private static final class MaskedSkippingIterator implements Iterator<Object> {

        private final Iterator<?> source;
        private Object pending;
        private boolean hasPending;

        private MaskedSkippingIterator(Iterator<?> source) {
            this.source = source;
        }

        @Override
        public boolean hasNext() {
            advance();
            return hasPending;
        }

        @Override
        public Object next() {
            advance();
            if (!hasPending) throw new NoSuchElementException();
            Object value = pending;
            pending = null;
            hasPending = false;
            return value;
        }

        private void advance() {
            while (!hasPending && source.hasNext()) {
                Object candidate = source.next();
                if (candidate instanceof Player player && CognitiveMaskItem.isWorn(player)) {
                    continue;   // 面具佩戴者：不上雷达 ✓
                }
                pending = candidate;
                hasPending = true;
            }
        }
    }
}
