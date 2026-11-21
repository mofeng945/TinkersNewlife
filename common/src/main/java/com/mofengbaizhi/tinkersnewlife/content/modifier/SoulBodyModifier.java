package com.mofengbaizhi.tinkersnewlife.content.modifier;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.entity.SoulToolEcho;
import com.mofengbaizhi.tinkersnewlife.content.modifier.events.SoulBodyProjectileHandler;
import com.mofengbaizhi.tinkersnewlife.integration.IntegrationLoader;
import com.mofengbaizhi.tinkersnewlife.util.ToolHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.*;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid="tinkersnewlife")
public final class SoulBodyModifier extends Modifier {
    public static final ModifierId ID = new ModifierId(new ResourceLocation("tinkersnewlife","soul_body"));
    @Override protected void registerHooks(ModuleHookMap.Builder builder) {
        super.registerHooks(builder);
        builder.addHook(SoulBodyProjectileHandler.INSTANCE, ModifierHooks.PROJECTILE_LAUNCH, ModifierHooks.PROJECTILE_THROWN);
    }
    private static boolean melee(IToolStackView tool) {
        return tool != null && !tool.isBroken() && tool.hasTag(TinkerTags.Items.MELEE)
                && !tool.hasTag(TinkerTags.Items.RANGED) && !tool.hasTag(TinkerTags.Items.STAFFS)
                && !tool.hasTag(TinkerTags.Items.ARMOR) && !tool.hasTag(TinkerTags.Items.SHIELDS);
    }
    private static boolean eligible(Player player) {
        return IntegrationLoader.isIceAndFire() && player.isAlive() && !player.isSpectator() && !player.isUsingItem()
                && player.getAttackStrengthScale(0) >= 1;
    }
    // One swing can be reported by several interaction hooks; never create two copies in one tick.
    private static final java.util.Map<ServerPlayer, Long> LAST_LAUNCH = new java.util.WeakHashMap<>();
    private static boolean launch(ServerPlayer player) {
        long now=player.level().getGameTime();
        if (!eligible(player) || LAST_LAUNCH.getOrDefault(player, Long.MIN_VALUE)==now) return false;
        var stack=player.getMainHandItem(); var tool=ToolHelper.getToolStack(stack);
        if(!melee(tool))return false;
        int level=ToolHelper.getActiveModifierLevel(tool,ID); if(level<=0)return false;
        if (!SoulToolEcho.launch(player,stack,tool.getStats().get(ToolStats.ATTACK_DAMAGE),level)) return false;
        LAST_LAUNCH.put(player,now);
        return true;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void attack(AttackEntityEvent event) {
        // Vanilla/TConstruct will reset the meter for the actual hit; never weaken the triggering attack.
        if(event.getEntity() instanceof ServerPlayer player) launch(player);
    }
    @SubscribeEvent public static void miss(PlayerInteractEvent.LeftClickEmpty event) {
        var p=event.getEntity();if(!p.level().isClientSide || !IntegrationLoader.isIceAndFire())return; // The client may already have reset its swing meter.
        var tool=ToolHelper.getToolStack(p.getMainHandItem());
        if(melee(tool)&&ToolHelper.getActiveModifierLevel(tool,ID)>0) TinkersNewlife.CHANNEL.sendToServer(new Swing());
    }
    public static final class Swing {
        public Swing() {} public Swing(FriendlyByteBuf ignored) {} public void encode(FriendlyByteBuf ignored) {}
        public void handle(Supplier<NetworkEvent.Context> supplier) {
            var ctx=supplier.get();var player=ctx.getSender();
            ctx.enqueueWork(()->{if(player!=null && launch(player))player.resetAttackStrengthTicker();});
            ctx.setPacketHandled(true);
        }
    }
}
