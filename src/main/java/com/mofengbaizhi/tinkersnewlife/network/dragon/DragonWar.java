package com.mofengbaizhi.tinkersnewlife.network.dragon;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.DragonStaffHandler;
import com.mofengbaizhi.tinkersnewlife.content.handler.DragonWarOrders;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

public final class DragonWar {
    private DragonWar() {}
    private static final int HALF_WIDTH = 32;
    private static final long SESSION_TICKS = 6000;
    private static final UUID NONE = new UUID(0, 0);
    private static final WeakHashMap<ServerPlayer, Session> SESSIONS = new WeakHashMap<>();
    private record Session(UUID token, ItemStack staff, Level level, BlockPos center, UUID deployment, long expires) {}

    public record Begin(UUID rosterToken, UUID deployment) {
        public Begin(FriendlyByteBuf buf) { this(buf.readUUID(), buf.readUUID()); }
        public void encode(FriendlyByteBuf buf) { buf.writeUUID(rosterToken); buf.writeUUID(deployment); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            var context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player == null || !DragonCards.valid(player, rosterToken) || player.isPassenger()) return;
                ListTag roster = DragonStaffHandler.warRoster(player);
                if (roster == null) return;
                var session = new Session(UUID.randomUUID(), player.getMainHandItem(), player.level(), player.blockPosition(),
                        slot(roster, deployment, 1) ? deployment : NONE, player.level().getGameTime() + SESSION_TICKS);
                SESSIONS.put(player, session);
                send(player, new View(session.token, session.center, session.deployment, allies(player, session, roster), true));
            });
            context.setPacketHandled(true);
        }
    }

    public record Command(UUID token, int action, UUID dragon, BlockPos position, int targetEntity) {
        public Command(FriendlyByteBuf buf) { this(buf.readUUID(), buf.readVarInt(), buf.readUUID(), buf.readBlockPos(), buf.readVarInt()); }
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token); buf.writeVarInt(action); buf.writeUUID(dragon); buf.writeBlockPos(position); buf.writeVarInt(targetEntity);
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            var context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null) execute(player, this);
            });
            context.setPacketHandled(true);
        }
    }

    private static void execute(ServerPlayer player, Command cmd) {
        var session = SESSIONS.get(player);
        if (session == null || !session.token.equals(cmd.token) || !player.isAlive() || player.isSpectator()
                || player.isPassenger() || player.level() != session.level || player.getMainHandItem() != session.staff
                || player.level().getGameTime() > session.expires || !inside(session, player.blockPosition())) return;
        if (cmd.action < 0 || cmd.action > 5) return;
        ListTag roster = DragonStaffHandler.warRoster(player);
        if (roster == null) return;
        var level = player.serverLevel();
        if (cmd.action == 0) {
            if (!session.deployment.equals(cmd.dragon) || !slot(roster, cmd.dragon, 1)
                    || !inside(session, cmd.position) || !level.isLoaded(cmd.position)) return;
            DragonStaffHandler.releaseSelected(player, cmd.dragon, cmd.position);
            var latest = DragonStaffHandler.warRoster(player);
            if (latest != null) send(player, new View(session.token, session.center, session.deployment, allies(player, session, latest), false));
            return;
        }
        Entity entity = level.getEntity(cmd.dragon);
        if (!slot(roster, cmd.dragon, 0) || !(entity instanceof TamableAnimal dragon) || !dragon.isAlive()
                || !dragon.isOwnedBy(player) || !inside(session, dragon.blockPosition())) return;
        LivingEntity target = null;
        if (cmd.action == 2 || cmd.action == 3 && cmd.targetEntity >= 0) {
            if (!(level.getEntity(cmd.targetEntity) instanceof LivingEntity living) || living == dragon || living == player
                    || !living.isAlive() || living.isSpectator() || dragon.isAlliedTo(living) || living.isAlliedTo(dragon)
                    || !inside(session, living.blockPosition()) || (living instanceof net.minecraft.world.entity.OwnableEntity own
                    && player.getUUID().equals(own.getOwnerUUID()))) return;
            target = living;
        }
        BlockPos aim = target != null ? target.blockPosition() : cmd.position;
        if (cmd.action <= 3 && (!inside(session, aim) || !level.isLoaded(aim))) return;
        DragonWarOrders.apply(player, dragon, cmd.action, aim, target);
    }

    private static boolean inside(Session session, BlockPos pos) {
        return Math.abs((long)pos.getX() - session.center.getX()) <= HALF_WIDTH
                && Math.abs((long)pos.getZ() - session.center.getZ()) <= HALF_WIDTH
                && pos.getY() >= session.level.getMinBuildHeight() && pos.getY() < session.level.getMaxBuildHeight();
    }
    private static boolean slot(ListTag roster, UUID id, int state) {
        for (int i = 0; i < roster.size(); i++) {
            var entry = roster.getCompound(i);
            if (entry.getInt("state") == state && id.toString().equals(entry.getString("uuid"))) return true;
        }
        return false;
    }
    private static Set<UUID> allies(ServerPlayer player, Session session, ListTag roster) {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < roster.size() && ids.size() < 128; i++) {
            var entry = roster.getCompound(i);
            if (entry.getInt("state") != 0) continue;
            try {
                UUID id = UUID.fromString(entry.getString("uuid"));
                if (player.serverLevel().getEntity(id) instanceof TamableAnimal dragon && dragon.isAlive()
                        && dragon.isOwnedBy(player) && inside(session, dragon.blockPosition())) ids.add(id);
            } catch (IllegalArgumentException ignored) { }
        }
        return ids;
    }
    private static void send(ServerPlayer player, View view) {
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), view);
    }
    public record View(UUID token, BlockPos center, UUID deployment, Set<UUID> allies, boolean open) {
        public View(FriendlyByteBuf buf) { this(buf.readUUID(), buf.readBlockPos(), buf.readUUID(),
                Set.copyOf(buf.readList(FriendlyByteBuf::readUUID)), buf.readBoolean()); }
        public void encode(FriendlyByteBuf buf) {
            buf.writeUUID(token); buf.writeBlockPos(center); buf.writeUUID(deployment);
            buf.writeCollection(allies, FriendlyByteBuf::writeUUID); buf.writeBoolean(open);
        }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            var context = ctx.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.show(this)));
            context.setPacketHandled(true);
        }
    }
    private static final class Client {
        static void show(View view) {
            if (view.open && net.minecraft.client.Minecraft.getInstance().screen instanceof
                    com.mofengbaizhi.tinkersnewlife.client.screen.DragonCardsScreen parent) {
                com.mofengbaizhi.tinkersnewlife.client.screen.DragonWarScreen.open(parent, view.token, view.center, view.deployment, view.allies);
            } else if (!view.open) {
                com.mofengbaizhi.tinkersnewlife.client.screen.DragonWarScreen.updateAllies(view.token, view.allies);
            }
        }
    }
}
