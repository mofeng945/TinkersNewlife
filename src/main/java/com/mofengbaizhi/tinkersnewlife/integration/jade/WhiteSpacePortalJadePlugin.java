package com.mofengbaizhi.tinkersnewlife.integration.jade;

import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * 玉（Jade）联动入口：让「伟大白色空间之门」在玉的显示界面里报出<b>对面落点</b>（§661）。
 *
 * <p>和 {@link CurseVaultJadePlugin} 一样，属于"被玉反向加载"的插件 ——
 * 玉加载完成时自己扫描 {@code @WailaPlugin} 注解并实例化本类，
 * <b>没装玉时本类永远不会被加载</b>（也就不会有 {@code NoClassDefFoundError}）✓，
 * 主类／{@code IntegrationLoader} 都不需要引用它 ✓。
 *
 * <p>只对 {@link WhiteSpacePortalBlock}（及其方块实体）生效，别的方块一律不动。
 */
@WailaPlugin
public class WhiteSpacePortalJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // 服务端侧：把目的地（维度 + xyz）写进玉的数据包
        registration.registerBlockDataProvider(
                WhiteSpacePortalJadeProvider.INSTANCE, WhiteSpacePortalBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // 客户端侧：只有这扇门挂提示
        registration.registerBlockComponent(
                WhiteSpacePortalJadeProvider.INSTANCE, WhiteSpacePortalBlock.class);
    }
}
