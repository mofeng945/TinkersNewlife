package com.mofengbaizhi.tinkersnewlife.content.item;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.curio.IndustrialPioneerHandler;
import com.mofengbaizhi.tinkersnewlife.content.rate.ContainerRateManager;
import com.mofengbaizhi.tinkersnewlife.network.rate.PacketOpenPioneerRates;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <b>工业开拓之证</b>（§738）—— 占用 {@code charm}（护符）槽的饰品 ✓
 *
 * <h2>用法（用户口径 ✓）</h2>
 * <ol>
 *   <li><b>蹲下右键</b>（{@code isSecondaryUseActive()} ✓）⇒ 把<b>当前维度永久绑定</b>到这个饰品上 ✓；</li>
 *   <li>绑定后，它会按<b>该维度</b>的物品 / 流体 / 能量产率给佩戴者加属性 ✓
 *       —— 规则与实现在 {@code content/curio/IndustrialPioneerHandler} ✓。</li>
 * </ol>
 *
 * <h2>为什么"永久"就真的不给改 ✗</h2>
 * 用户说的就是<b>永久绑定</b> ✓ ⇒ 一旦绑到某个维度，再蹲下右键<b>不会</b>改绑 ✓
 * （绑同一个维度只说一句"已绑定"✓；绑另一个维度会拒绝并告诉你现在绑的是哪 ✓）。
 * <p>⚠ 想让玩家改绑的话改一行就行 ✓ —— 但那是**口径变更**，先问再做 ✓（§728 的教训 ✓）。
 */
public class IndustrialPioneerCertificateItem extends Item implements ICurioItem {

    /** 绑定的维度存在这个 NBT 键里（维度 id 字符串 ✓） */
    public static final String BOUND_DIMENSION = "tinkersnewlife:pioneer_dimension";

    public IndustrialPioneerCertificateItem() {
        super(new Properties().stacksTo(1));
    }

    // ============================================================
    //  ICurioItem：只进 charm 槽 ✓（用户口径 ✓）
    // ============================================================

    @Override
    public boolean canEquip(SlotContext context, ItemStack stack) {
        return "charm".equals(context.identifier());
    }

    /**
     * <b>右键不会自动戴上</b> ✓（用户口径：「像七咒之戒那样」✓）
     * <p>原因有两层：
     * <ol>
     *   <li>本饰品的右键是<b>绑定维度</b>用的 ✓ ⇒ 不能让 Curios 顺手把它塞进饰品槽 ✗
     *       （否则"想绑定"变成"戴上了"✓）；</li>
     *   <li>与 {@code CurseCoreItem} / {@code SilentGloveItem} / {@code FlyingSwordItem}
     *       同款口径 ✓ —— 那几件也都是 {@code canEquipFromUse = false} ✓（要戴就手动放进槽位 ✓）。</li>
     * </ol>
     */
    @Override
    public boolean canEquipFromUse(SlotContext context, ItemStack stack) {
        return false;
    }

    // ============================================================
    //  蹲下右键：绑定维度 ✓
    // ============================================================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 不潜行 ⇒ §742：**查看绑定维度的物品产率**（服务端打包 ⇒ 客户端开屏 ✓）
        if (!player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                ResourceKey<Level> bound = boundDimension(stack);
                if (bound == null) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.rates.nobind")
                                    .withStyle(ChatFormatting.GRAY), false);
                } else {
                    sendRateReport(serverPlayer, bound);
                }
            }
            return InteractionResultHolder.success(stack);
        }

        ResourceKey<Level> here = level.dimension();
        ResourceKey<Level> bound = boundDimension(stack);

        if (bound != null) {
            // 已经绑过 ⇒ 永久绑定口径：不改绑 ✓
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                        bound.equals(here)
                                ? Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.already",
                                dimensionName(bound)).withStyle(ChatFormatting.GRAY)
                                : Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.refuse",
                                dimensionName(bound)).withStyle(ChatFormatting.RED),
                        false);
            }
            return InteractionResultHolder.success(stack);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            stack.getOrCreateTag().putString(BOUND_DIMENSION, here.location().toString());
            serverPlayer.displayClientMessage(
                    Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.bound",
                            dimensionName(here)).withStyle(ChatFormatting.AQUA),
                    false);
            TinkersNewlife.LOGGER.info("[工业开拓之证] 玩家 {} 永久绑定维度 {} ✓",
                    serverPlayer.getName().getString(), here.location());
        }
        return InteractionResultHolder.success(stack);
    }

    // ============================================================
    //  §742：查看"绑定维度"的物品产率（服务端打包 ⇒ 客户端开屏 ✓）
    // ============================================================

    /**
     * 把这个维度<b>全部物品</b>的净产率（个/时 ✓）打包发给玩家 ✓。
     *
     * <ul>
     *   <li>读的是<b>绑定维度</b>的 {@code SavedData} ✓ ⇒ 玩家人在别的维度也能看 ✓；</li>
     *   <li>按产率<b>从高到低</b>排 ✓；只送前 {@link PacketOpenPioneerRates#MAX_ROWS} 行 ✓
     *       （截断了会在界面页脚写明"共 X 种、只显示前 Y" ✓ 不悄悄丢 ✗）；</li>
     *   <li>⚠ 只统计**物品** ✓（用户口径"查看这个维度内所有物品的产率"✓
     *       —— 流体/能量同一套接口也能做 ✓ 但这次没做 ✗ 需要就说 ✓）。</li>
     * </ul>
     */
    public static void sendRateReport(ServerPlayer player, ResourceKey<Level> bound) {
        ServerLevel level = player.server.getLevel(bound);
        if (level == null) {
            player.displayClientMessage(
                    Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.rates.nodim")
                            .withStyle(ChatFormatting.RED), false);
            return;
        }
        Map<Item, Double> all = ContainerRateManager.netPerHourAll(level, IndustrialPioneerHandler.WINDOW);
        List<Map.Entry<Item, Double>> sorted = new ArrayList<>(all.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int total = sorted.size();
        int shown = Math.min(total, PacketOpenPioneerRates.MAX_ROWS);
        List<PacketOpenPioneerRates.Row> rows = new ArrayList<>(shown);
        for (int i = 0; i < shown; i++) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(sorted.get(i).getKey());
            if (id == null) continue;
            rows.add(new PacketOpenPioneerRates.Row(id.toString(), sorted.get(i).getValue()));
        }
        String dimensionKey = "dimension." + bound.location().getNamespace() + "." + bound.location().getPath();
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketOpenPioneerRates(dimensionKey, total, rows));
    }

    // ============================================================
    //  提示文本
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        ResourceKey<Level> bound = boundDimension(stack);
        tooltip.add((bound == null
                ? Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.unbound")
                : Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.bound.line",
                dimensionName(bound))).withStyle(bound == null ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.rates.hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.tinkersnewlife.industrial_pioneer_certificate.effect")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ============================================================
    //  工具：读绑定 / 维度显示名
    // ============================================================

    /** 读这个饰品绑定的维度（没绑或记录坏了 ⇒ null ✓） */
    @Nullable
    public static ResourceKey<Level> boundDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(BOUND_DIMENSION)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(BOUND_DIMENSION));
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    /** 维度的可读名（{@code dimension.<ns>.<path>} 语言键 ✓ 没有就用 id ✓） */
    public static Component dimensionName(ResourceKey<Level> dimension) {
        ResourceLocation id = dimension.location();
        return Component.translatable("dimension." + id.getNamespace() + "." + id.getPath());
    }
}
