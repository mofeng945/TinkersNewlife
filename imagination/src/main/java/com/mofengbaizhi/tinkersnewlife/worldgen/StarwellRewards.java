package com.mofengbaizhi.tinkersnewlife.worldgen;

import com.mofengbaizhi.tinkersnewlife.content.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.RandomMaterial;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import java.util.*;
import java.util.function.Supplier;

public final class StarwellRewards {
    private StarwellRewards() {}
    private static final String MARK="tnl_starwell_reward";
    private record Drop(int weight,int max,Supplier<Item> item){}
    private static final List<Drop> POOL=List.of(
        new Drop(3,1,()->Items.NETHERITE_INGOT),new Drop(12,3,()->Items.DIAMOND),new Drop(16,4,()->Items.EMERALD),
        new Drop(6,2,ModItems.GHELOTH_ORE),new Drop(3,1,ModItems.YELLOW_KING_REMNANT),new Drop(3,1,ModItems.NICHOLAS_BLESSING),new Drop(3,1,ModItems.NYARLATHOTEP_DESIRE),
        new Drop(22,6,()->Items.STRING),new Drop(24,5,()->Items.IRON_INGOT),new Drop(12,3,()->ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct","cobalt_ingot"))),
        new Drop(8,3,()->Items.DRAGON_BREATH),new Drop(2,1,()->Items.DRAGON_HEAD),new Drop(8,2,()->Items.SHULKER_SHELL),new Drop(18,5,()->Items.POPPED_CHORUS_FRUIT));
    private static final int NORMAL=POOL.stream().mapToInt(Drop::weight).sum(), SWORD=4;
    private static void populate(ChestBlockEntity chest,RandomSource rng){
        boolean swordAllowed=true;int rolls=5+rng.nextInt(11);var slots=new ArrayList<Integer>();for(int i=0;i<27;i++)slots.add(i);
        for(int i=0;i<rolls;i++){
            int ticket=rng.nextInt(NORMAL+(swordAllowed?SWORD:0));ItemStack stack=ItemStack.EMPTY;
            if(ticket>=NORMAL){
                var tool=ToolBuildHandler.buildToolRandomMaterials(ModItems.FLYING_SWORD.get(),RandomMaterial.random().minTier(2).build(),rng);
                if(!tool.getMaterials().isEmpty() && tool.getMaterials().getList().stream().allMatch(v->v.get().getTier()>=2)){stack=tool.createStack();swordAllowed=false;}
                else ticket=rng.nextInt(NORMAL);
            }
            if(stack.isEmpty())for(var drop:POOL){ticket-=drop.weight();if(ticket<0){stack=new ItemStack(drop.item().get(),1+rng.nextInt(drop.max()));break;}}
            chest.setItem(slots.remove(rng.nextInt(slots.size())).intValue(),stack);
        }
        chest.setChanged();
    }
    public static void afterExplosion(ServerLevel level,BlockPos death){
        BlockPos floor=StarwellSummoning.floorAt(level,death);if(floor==null)return;
        BlockPos beacon=floor.below(12);if(!level.getBlockState(beacon).is(Blocks.BEACON))return;
        var free=new ArrayList<BlockPos>();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x!=0||z!=0){
            BlockPos pos=beacon.offset(x,0,z);
            if(level.getBlockEntity(pos) instanceof ChestBlockEntity old && old.getPersistentData().getBoolean(MARK)){
                old.clearContent();level.destroyBlock(pos,false);
            }
            if(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir())free.add(pos);
        }
        int count=Math.min(free.size(),3+level.random.nextInt(3));
        for(int i=0;i<count;i++){
            BlockPos pos=free.remove(level.random.nextInt(free.size()));
            if(level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState())&&level.getBlockEntity(pos) instanceof ChestBlockEntity chest){
                chest.getPersistentData().putBoolean(MARK,true);populate(chest,level.random);
            }
        }
    }
}
