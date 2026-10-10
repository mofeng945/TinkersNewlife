package com.mofengbaizhi.tinkersnewlife.integration.jade;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlock;
import com.mofengbaizhi.tinkersnewlife.content.block.WhiteSpacePortalBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 「伟大白色空间之门」的玉（Jade）显示：<b>指到门就报出对面落点</b>（§661 用户口径
 * 「当鼠标指针指向传送门时，要让门能够显示对面落点」）。
 *
 * <p>照仓库里 {@link CurseVaultJadeProvider} 同一套写法：服务端在
 * {@link #appendServerData} 里把目的地（维度 id ＋ xyz）填进玉的数据包，客户端在
 * {@link #appendTooltip} 里只读这份数据。
 *
 * <p>⚠ 目的地在{@code WhiteSpacePortalBlockEntity} 里，虽然它已经靠 {@code getUpdatePacket()}
 * 同步到客户端了，但这里**仍然走"服务端填包"**：一是与仓库既有做法一致，
 * 二是玉的服务端数据是**跟着玩家那一刻的真实世界状态**取的，
 * 不会出现"客户端方块实体还没收到更新 ⇒ 提示是空的"这种时序问题 ✓。
 */
public final class WhiteSpacePortalJadeProvider
        implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {

    public static final WhiteSpacePortalJadeProvider INSTANCE = new WhiteSpacePortalJadeProvider();

    /** 玉的配置项／唯一 id（也是 config 里那条开关的名字） */
    private static final ResourceLocation UID =
            new ResourceLocation(TinkersNewlife.MOD_ID, "white_space_portal");

    /** 数据包字段（加模组前缀防撞名） */
    private static final String KEY_DIM = "tnl_ws_dim";
    private static final String KEY_X = "tnl_ws_x";
    private static final String KEY_Y = "tnl_ws_y";
    private static final String KEY_Z = "tnl_ws_z";

    private WhiteSpacePortalJadeProvider() {
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    // ============================================================
    //  服务端：填数据
    // ============================================================

    @Override
    public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) return;
        if (!(level.getBlockEntity(accessor.getPosition()) instanceof WhiteSpacePortalBlockEntity portal)) return;
        ResourceKey<Level> dimension = portal.getDestinationDimension();
        if (dimension == null) return;   // 没目的地的门（旧存档／异常）什么都不报
        BlockPos dest = portal.getDestinationPos();
        tag.putString(KEY_DIM, dimension.location().toString());
        tag.putInt(KEY_X, dest.getX());
        tag.putInt(KEY_Y, dest.getY());
        tag.putInt(KEY_Z, dest.getZ());
    }

    // ============================================================
    //  客户端：出提示
    // ============================================================

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(KEY_DIM)) return;

        ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_DIM));
        Component name = id == null
                ? Component.literal("?")
                : Component.translatableWithFallback(
                        "dimension." + id.getNamespace() + "." + id.getPath(), id.toString());

        tooltip.add(Component.translatable("jade.tinkersnewlife.white_space_portal.dest", name)
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("jade.tinkersnewlife.white_space_portal.coords",
                        tag.getInt(KEY_X), tag.getInt(KEY_Y), tag.getInt(KEY_Z))
                .withStyle(ChatFormatting.GRAY));
    }
}
