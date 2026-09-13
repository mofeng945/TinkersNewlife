package com.mofengbaizhi.tinkersnewlife.integration.jade;

import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.CurseVaultBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * 玉（Jade）联动入口：让放下的「呪蔵」在玉的显示界面里报出**主人是谁**（外加存量与咒力残秽）。
 *
 * <h2>为什么这个类不需要 {@code IntegrationLoader} 调用</h2>
 * 玉在加载完成时自己扫描注解（{@code ModFileScanData} 里找 {@code @WailaPlugin}）并实例化本类，
 * 属于"被玉反向加载"的插件，主类/联动总入口都不需要引用它。
 * 因此：<b>没装玉时本类永远不会被加载</b>，也就不会出现 {@code NoClassDefFoundError}；
 * 装玉时玉会自动发现它，无需任何注册代码。
 *
 * <p>只对 {@link CurseVaultBlock}（及其方块实体）生效，别的方块一律不动。
 */
@WailaPlugin
public class CurseVaultJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        // 服务端侧：把主人/存量写进玉的数据包（world data 客户端拿不到）
        registration.registerBlockDataProvider(CurseVaultJadeProvider.INSTANCE, CurseVaultBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // 客户端侧：只有呪蔵这一种方块挂提示
        registration.registerBlockComponent(CurseVaultJadeProvider.INSTANCE, CurseVaultBlock.class);
    }
}
