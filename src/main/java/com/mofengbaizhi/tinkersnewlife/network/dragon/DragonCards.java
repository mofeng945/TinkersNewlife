package com.mofengbaizhi.tinkersnewlife.network.dragon;

import com.mofengbaizhi.tinkersnewlife.TinkersNewlife;
import com.mofengbaizhi.tinkersnewlife.content.handler.DragonStaffHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Supplier;

/** The client selects a UUID, never supplies a dragon, its stats, or a release position. */
public final class DragonCards {
    private DragonCards() {}
    private record Session(UUID token, ItemStack staff, long expires, ResourceLocation dimension) {}
    private static final Map<ServerPlayer,Session> OPEN = new WeakHashMap<>();
    private static final int LIMIT = 128;

    public static void open(ServerPlayer player, ListTag slots) { open(player, slots, false); }

    public static void open(ServerPlayer player, ListTag slots, boolean refresh) {
        List<CompoundTag> cards = new ArrayList<>();
        for (int i=0; i<slots.size() && cards.size()<LIMIT; i++) {
            CompoundTag entry = slots.getCompound(i);
            UUID id;
            try { id = UUID.fromString(entry.getString("uuid")); } catch (IllegalArgumentException ignored) { continue; }
            CompoundTag data = entry.getCompound("data");
            LivingEntity live = null;
            if (entry.getInt("state") == 0) {
                for (var level : player.server.getAllLevels()) {
                    Entity found = level.getEntity(id);
                    if (found instanceof LivingEntity dragon && dragon.isAlive()) { live=dragon; break; }
                }
            }
            if (live != null) { data = new CompoundTag(); live.saveWithoutId(data); }
            CompoundTag card = new CompoundTag();
            card.putString("uuid",id.toString()); card.putString("name",live != null ? live.getName().getString() : entry.getString("name"));
            card.putString("type",entry.getString("type")); card.putInt("state",entry.getInt("state")); card.putBoolean("live",live!=null);
            card.putBoolean("male",data.getBoolean("Gender"));
            int days=Math.max(0,data.getInt("AgeTicks"))/24000;
            card.putInt("days",days); card.putInt("stage",Math.min(5,days/25+1));
            card.putDouble("hp",live!=null ? live.getHealth() : data.getFloat("Health"));
            for (var stat : List.of(Attributes.MAX_HEALTH,Attributes.ARMOR,Attributes.ARMOR_TOUGHNESS,Attributes.ATTACK_DAMAGE)) {
                String key = stat==Attributes.MAX_HEALTH?"maxHp":stat==Attributes.ARMOR?"armor":stat==Attributes.ARMOR_TOUGHNESS?"toughness":"attack";
                var inst=live==null?null:live.getAttribute(stat);
                card.putDouble(key,inst!=null?inst.getValue():attribute(data,stat));
            }
            CompoundTag model=new CompoundTag(); model.putString("id",entry.getString("type"));
            for(String key:List.of("AgeTicks","Gender","Variant","CustomPose")) if(data.contains(key)) model.put(key,data.get(key).copy());
            ListTag armor=new ListTag();
            for(Tag t:data.getList("Items",Tag.TAG_COMPOUND)) { var it=(CompoundTag)t; int slot=it.getByte("Slot")&255;
                if(slot<=4){CompoundTag gear=new CompoundTag();gear.putByte("Slot",(byte)slot);gear.putString("id",it.getString("id"));gear.putByte("Count",(byte)1);armor.add(gear);}}
            model.put("Items",armor);card.put("data",model);cards.add(card);
        }
        UUID token=UUID.randomUUID();OPEN.put(player,new Session(token,player.getMainHandItem(),player.level().getGameTime()+2400,player.level().dimension().location()));
        TinkersNewlife.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new PitView(new Open(cards,token), DragonStaffHandler.canStartPit(player), DragonStaffHandler.pitRemaining(player), refresh));
    }
    public static double attribute(CompoundTag data,Attribute stat) {
        String id=Objects.requireNonNull(ForgeRegistries.ATTRIBUTES.getKey(stat)).toString();
        for(Tag t:data.getList("Attributes",Tag.TAG_COMPOUND)) { var tag=(CompoundTag)t;
            if (id.equals(tag.getString("Name"))) { AttributeInstance temp=new AttributeInstance(stat,a->{});temp.load(tag);return temp.getValue(); }}
        return stat.getDefaultValue();
    }
    public record Open(List<CompoundTag> cards, UUID token) {
        public Open(FriendlyByteBuf buf) { this(buf.readList(b->Objects.requireNonNull(b.readNbt())),buf.readUUID()); }
        public void encode(FriendlyByteBuf buf){buf.writeCollection(cards,FriendlyByteBuf::writeNbt);buf.writeUUID(token);}
        public void handle(Supplier<NetworkEvent.Context> ctx){ new PitView(this, false, 0, false).handle(ctx); }
    }
    public record PitView(Open roster, boolean pitAvailable, int remainingTicks, boolean refresh) {
        public PitView(FriendlyByteBuf buf) { this(new Open(buf), buf.readBoolean(), buf.readVarInt(), buf.readBoolean()); }
        public void encode(FriendlyByteBuf buf) { roster.encode(buf); buf.writeBoolean(pitAvailable); buf.writeVarInt(remainingTicks); buf.writeBoolean(refresh); }
        public void handle(Supplier<NetworkEvent.Context> ctx){var context=ctx.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Client.open(this)));context.setPacketHandled(true);}
    }
    private static final class Client {
        static void open(PitView packet){
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (packet.refresh && !(mc.screen instanceof com.mofengbaizhi.tinkersnewlife.client.screen.DragonCardsScreen)) return;
            mc.setScreen(new com.mofengbaizhi.tinkersnewlife.client.screen.DragonCardsScreen(packet.roster.cards,packet.roster.token,packet.pitAvailable,packet.remainingTicks));
        }
    }
    private static boolean valid(ServerPlayer player, UUID token) {
        var session = OPEN.get(player);
        return session != null && session.token.equals(token) && player.isAlive() && !player.isSpectator()
                && player.getMainHandItem() == session.staff && player.level().getGameTime() <= session.expires
                && player.level().dimension().location().equals(session.dimension);
    }

    public record StartPit(UUID token) {
        public StartPit(FriendlyByteBuf buf) { this(buf.readUUID()); }
        public void encode(FriendlyByteBuf buf) { buf.writeUUID(token); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            var context = ctx.get();
            context.enqueueWork(() -> {
                var player = context.getSender();
                if (player != null && valid(player, token)) DragonStaffHandler.startPit(player);
            });
            context.setPacketHandled(true);
        }
    }

    public record Release(UUID token,UUID dragon) {
        public Release(FriendlyByteBuf buf){this(buf.readUUID(),buf.readUUID());}
        public void encode(FriendlyByteBuf buf){buf.writeUUID(token);buf.writeUUID(dragon);}
        public void handle(Supplier<NetworkEvent.Context> ctx){var context=ctx.get();context.enqueueWork(()->{
            var player=context.getSender();if(player==null)return;var session=OPEN.get(player);
            if(session==null || !session.token.equals(token))return;OPEN.remove(player);
            if(!player.isAlive()||player.isSpectator()||player.getMainHandItem()!=session.staff||player.level().getGameTime()>session.expires||!player.level().dimension().location().equals(session.dimension))return;
            DragonStaffHandler.releaseSelected(player,dragon);
        });context.setPacketHandled(true);}
    }
}
