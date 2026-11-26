package com.mofengbaizhi.tinkersnewlife.integration;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 联动模块统一契约：<b>每个被联动模组一个实现类</b>，且该实现类所在的子包
 * （{@code integration/<modid>/}）是本模组内<b>唯一允许直接 import 该模组类型</b>的代码区。
 *
 * <h2>铁律（联动注册标准写法）</h2>
 * <ol>
 *   <li><b>存在性一律用 {@link IntegrationLoader#isLoaded(String)}</b>（内部 {@code ModList.get().isLoaded}）判定，
 *       禁止用 {@code Class.forName} 试探——后者既慢又可能把 {@code LinkageError} 吞成"未安装"。</li>
 *   <li><b>联动内容整组隔离</b>：流体 / 流动流体 / FluidType / 方块 / 桶 / 物品 / 渲染 / 事件 / 配方
 *       必须在同一个"该模组在场"分支里注册，绝不出现"流体注册了但桶没注册"的半残状态。</li>
 *   <li><b>公共代码只按注册名取用</b>：{@code ForgeRegistries.ITEMS.getValue(new ResourceLocation(MODID, name))}
 *       —— 不引用联动类的静态字段/常量，这样公共类加载时不会去解析联动类型。</li>
 *   <li>数据包侧同理：配方用 {@code forge:mod_loaded} 条件；标签 JSON 不支持条件（只能留警告）。</li>
 * </ol>
 *
 * <p>注意：实现类的 <b>方法体</b>可以自由引用联动类型（JVM 解析是惰性的），但<b>静态字段/父类/接口</b>
 * 不可以引用——否则实现类一被加载就会连带解析联动类型。因此实现类本身通常保持"零联动 import"，
 * 真正引用联动类型的代码放在同包的更深一层类里（例如 {@code GoetyIntegration} → {@code GoetyStaffItem}）。
 */
public interface Integration {

    /** 被联动的模组 id（必须与 {@code ModList} / mods.toml 中的 modId 完全一致） */
    String modId();

    /** 模组总线注册（DeferredRegister / 事件订阅 / 能力注册）。仅在该模组已加载时被调用。 */
    default void register(IEventBus bus) {
    }
}
