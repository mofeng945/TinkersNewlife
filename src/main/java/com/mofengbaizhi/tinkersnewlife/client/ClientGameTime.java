package com.mofengbaizhi.tinkersnewlife.client;

/**
 * <b>§829 客户端游戏时间</b>（tick）—— 只给"拟造物剩余寿命条"用 ✓。
 *
 * <p>⚠ 本类**只能被客户端代码碰** ✗：唯一入口是
 * {@code ConstructedBlueprintItem#currentGameTime()} 里那句
 * {@code DistExecutor.unsafeCallWhenOn(Dist.CLIENT, ...)} ✓
 * （专服上那个 lambda 永不执行 ⇒ 本类不会被加载 ⇒ 不会 NoClassDefFoundError ✓
 *   —— 本仓 §801/§813 都踩过"公共代码直接引用客户端类"的坑 ✗）。
 */
public final class ClientGameTime {

    private ClientGameTime() {
    }

    /** 当前维度游戏时间（tick ✓）；还没进世界 ⇒ 0 ✓（调用方按"拿不到"回退 ✓） */
    public static long now() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return (mc == null || mc.level == null) ? 0L : mc.level.getGameTime();
    }
}
