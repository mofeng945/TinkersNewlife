package com.mofengbaizhi.tinkersnewlife.integration.jade;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curse.CursePowerHelper;
import com.mofengbaizhi.tinkersnewlife.content.curse.CurseVaultData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.UsernameCache;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 「呪蔵」的玉（Jade）显示：**主人是谁** + 存量 + 咒力残秽。
 *
 * <h2>主人名字怎么来的（照抄诡厄巫法「灵魂方舟」的做法）</h2>
 * 方舟的 {@code compat/jade/BlockOwnerProvider} 只做两件事：服务端把
 * {@code CommonProxy.getLastKnownUsername(ownerUUID)} 写进数据包，客户端读出来显示。
 * 那个方法在 Forge 上就是 {@link UsernameCache#getLastKnownUsername(UUID)}
 * —— Forge 自带的 {@code usernamecache.json}（玩家每次登录都会写进去），
 * **不联网、不卡服务端线程、离线模式也能查**，所以完全不用我们自己在存档里另存一份名字。
 * 查不到（从没在这个整合包登录过）时退回 UUID 前 8 位，至少能区分是谁。
 *
 * <h2>为什么走"服务端填包、客户端读"</h2>
 * 咒力存量在 {@link CurseVaultData}（每维度一份 world data）里，**客户端拿不到**，
 * 所以按玉的标准做法：{@link #appendServerData} 服务端填 {@link CompoundTag}，
 * {@link #appendTooltip} 客户端只读这份数据包。
 */
public final class CurseVaultJadeProvider implements IServerDataProvider<BlockAccessor>, IBlockComponentProvider {

    public static final CurseVaultJadeProvider INSTANCE = new CurseVaultJadeProvider();

    /** 玉的配置项/唯一 id（也是 config 里那条开关的名字） */
    private static final ResourceLocation UID = new ResourceLocation(TinkersNewlife.MOD_ID, "curse_vault");

    /** 数据包字段（加模组前缀防撞名） */
    private static final String KEY_OWNER = "tnl_vault_owner";
    private static final String KEY_OWNER_ID = "tnl_vault_owner_id";
    private static final String KEY_POWER = "tnl_vault_power";
    private static final String KEY_MB = "tnl_vault_mb";

    private CurseVaultJadeProvider() {
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
        CurseVaultData data = CurseVaultData.getOrNull(level);
        if (data == null) return;
        BlockPos pos = accessor.getPosition();
        CurseVaultData.Entry entry = data.get(pos);
        if (entry == null) return;   // 没登记 → 不是呪蔵（或数据没同步），什么都不报

        tag.putUUID(KEY_OWNER_ID, entry.owner);
        String name = ownerName(entry.owner);
        if (name != null) tag.putString(KEY_OWNER, name);
        tag.putDouble(KEY_POWER, entry.power);
        tag.putInt(KEY_MB, (int) Math.floor(entry.power / CurseVaultData.POWER_PER_MB));
    }

    /** UUID → 名字：Forge 的登录名缓存（== 玉的 {@code CommonProxy.getLastKnownUsername}）→ UUID 前 8 位 */
    @Nullable
    private static String ownerName(UUID owner) {
        String cached = UsernameCache.getLastKnownUsername(owner);
        if (cached != null && !cached.isEmpty()) return cached;
        String raw = owner.toString();
        return raw.length() >= 8 ? raw.substring(0, 8) : raw;
    }

    // ============================================================
    //  客户端：出提示
    // ============================================================

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag tag = accessor.getServerData();
        if (!tag.contains(KEY_OWNER)) return;   // 服务端没给数据（未连线）→ 不显示

        String name = tag.getString(KEY_OWNER);
        boolean self = tag.hasUUID(KEY_OWNER_ID)
                && tag.getUUID(KEY_OWNER_ID).equals(accessor.getPlayer().getUUID());
        tooltip.add(Component.translatable(
                        self ? "jade.tinkersnewlife.curse_vault.owner_self" : "jade.tinkersnewlife.curse_vault.owner", name)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("jade.tinkersnewlife.curse_vault.power",
                        CursePowerHelper.formatAmount(tag.getDouble(KEY_POWER)),
                        CursePowerHelper.formatAmount(CurseVaultData.CAPACITY))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("jade.tinkersnewlife.curse_vault.fluid",
                        tag.getInt(KEY_MB), CurseVaultData.CAPACITY_MB)
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
