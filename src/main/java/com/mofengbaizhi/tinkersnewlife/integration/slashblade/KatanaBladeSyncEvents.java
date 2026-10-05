package com.mofengbaizhi.tinkersnewlife.integration.slashblade;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.network.slashblade.PacketSlashBladeStateSync;
import mods.flammpfeil.slashblade.event.BladeMotionEvent;
import mods.flammpfeil.slashblade.event.handler.InputCommandEvent;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerFlyableFallEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.PacketDistributor;
import slimeknights.tconstruct.library.tools.item.IModifiable;

/**
 * <b>§1022 照抄 TiCEX 的 {@code TicEXSBEvent}</b> ✓（<a href="https://github.com/mofumofumoffy/ticex">ticex</a> `1.20.1` ✓）
 * —— <b>把刀状态在关键时机主动推给客户端</b> ✓。
 *
 * <p>TiCEX 原文的时机：{@code BladeMotionEvent}（刀动作 ✓）、{@code LivingFallEvent}／
 * {@code PlayerFlyableFallEvent}（落地 ✓）、{@code LivingDeathEvent}／{@code LivingExperienceDropEvent}
 * （击杀与经验 ✓）、{@code LivingHurtEvent}（受伤 ✓）、以及 {@code InputCommandEvent}（输入指令 ✓，
 * 靠 {@link IInputCommandEvent} 那个接口 mixin 取玩家 ✓）✓ —— 本类逐条照搬 ✓。
 *
 * <p>为什么重要：连段/蓄力/技能的表现都在客户端读刀状态 ✓；光靠原版槽位同步不够及时 ✓
 * （我们前几轮"连段不推进 / 技能表现对不上"很可能与此有关 ✗）。
 *
 * <p>⚠ 本类引用了拔刀剑的类 ✓ ⇒ **只在拔刀剑在场时才会被加载** ✓
 * （由 {@link SlashBladeIntegration#register} 调用 ✓，那条路径本身就带"模组在场"判定 ✓）。
 */
public final class KatanaBladeSyncEvents {

    private KatanaBladeSyncEvents() {
    }

    /** 注册全部同步监听器（由 {@link SlashBladeIntegration#register} 调用 ✓）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, KatanaBladeSyncEvents::onBladeMotion);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, KatanaBladeSyncEvents::onLivingFall);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, KatanaBladeSyncEvents::onPlayerFlyableFall);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, KatanaBladeSyncEvents::onLivingDeath);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, KatanaBladeSyncEvents::onLivingExperienceDrop);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, KatanaBladeSyncEvents::onLivingHurt);
        // 输入指令（对应 TiCEX 的动态注册 ✓；我们直接按类型注册 ✓，因为整条路径已经保证拔刀剑在场 ✓）
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, InputCommandEvent.class, event -> {
            if (event instanceof IInputCommandEvent inputCommandEvent) {
                syncState(inputCommandEvent.tnl$getEntity());
            }
        });
    }

    public static void onBladeMotion(BladeMotionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncState(player);
        }
    }

    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncState(player);
        }
    }

    public static void onPlayerFlyableFall(PlayerFlyableFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncState(player);
        }
    }

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            syncState(player);
        }
    }

    public static void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getAttackingPlayer() instanceof ServerPlayer player) {
            syncState(player);
        }
    }

    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            syncState(player);
        }
    }

    /**
     * 把玩家主手那把刀的刀状态：① 写回物品 NBT ✓ ② 单独发一个包给该玩家自己 ✓（照 TiCEX ✓）。
     *
     * @param player 目标玩家 ✓
     */
    public static void syncState(ServerPlayer player) {
        ItemStack mainHandStack = player.getMainHandItem();
        if (mainHandStack.getItem() instanceof IModifiable) {
            KatanaDebug.log("syncState 触发（我方匠魂物品）✓ 状态存在=" + mainHandStack.getCapability(ItemSlashBlade.BLADESTATE).isPresent());
            mainHandStack.getCapability(ItemSlashBlade.BLADESTATE).ifPresent(state -> {
                CompoundTag nbt = state.serializeNBT();
                mainHandStack.getOrCreateTag().put("bladeState", nbt.copy());
                TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSlashBladeStateSync(nbt));
            });
        }
    }
}
