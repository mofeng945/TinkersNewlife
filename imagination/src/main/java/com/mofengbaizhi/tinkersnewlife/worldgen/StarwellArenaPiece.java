package com.mofengbaizhi.tinkersnewlife.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

public final class StarwellArenaPiece extends TemplateStructurePiece {
    private static final ResourceLocation ID=new ResourceLocation("tinkersnewlife","starwell_arena");
    private static final BlockPos PIVOT=new BlockPos(88,0,88);
    private static StructurePlaceSettings settings(Rotation rot){return new StructurePlaceSettings().setRotationPivot(PIVOT).setRotation(rot).setIgnoreEntities(true).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);}
    StarwellArenaPiece(StructureTemplateManager manager,BlockPos origin,Rotation rotation){
        super(SleepwakeChurchRegistry.ARENA_PIECE.get(),0,manager,ID,ID.toString(),settings(rotation),origin);
    }
    StarwellArenaPiece(StructurePieceSerializationContext ctx,CompoundTag tag){super(SleepwakeChurchRegistry.ARENA_PIECE.get(),tag,ctx.structureTemplateManager(),id->settings(Rotation.valueOf(tag.getString("StarwellRotation"))));}
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext ctx,CompoundTag tag){super.addAdditionalSaveData(ctx,tag);tag.putString("StarwellRotation",placeSettings.getRotation().name());}
    @Override protected void handleDataMarker(String name,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox box){}
    @Override public void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,RandomSource random,BoundingBox box,ChunkPos chunk,BlockPos pivot){
        int cx=templatePosition.getX()+88,cz=templatePosition.getZ()+88,base=templatePosition.getY();
        int inverse=(4-placeSettings.getRotation().ordinal())%4; var pos=new BlockPos.MutableBlockPos();
        for(int x=box.minX();x<=box.maxX();x++)for(int z=box.minZ();z<=box.maxZ();z++){
            int dx=x-cx,dz=z-cz,u=StarwellSite.x(dx,dz,inverse),v=StarwellSite.z(dx,dz,inverse);
            boolean approach=Math.abs(u)<=5&&v>=62&&v<=87;
            if(!approach && dx*dx+dz*dz>63*63)continue; // Never fill beneath the rear star points.
            int ground=level.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            int grade=base;
            if(approach&&v>=80){int outer=generator.getFirstFreeHeight(cx+StarwellSite.x(u,88,(4-inverse)%4),cz+StarwellSite.z(u,88,(4-inverse)%4),Heightmap.Types.WORLD_SURFACE_WG,level,level.getLevel().getChunkSource().randomState());grade=base+1+(int)Math.round((outer-base-1)*(v-79)/9.0);}
            for(int y=Math.max(ground,grade-14);y<grade;y++)level.setBlock(pos.set(x,y,z),Blocks.END_STONE.defaultBlockState(),2);
            // The exported template omits air. Excavate the occupied island footprint before placing it,
            // so the crypt, incomplete beacon base and stairs remain accessible even on uneven terrain.
            for(int y=grade;y<ground;y++)level.setBlock(pos.set(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        super.postProcess(level,structures,generator,random,box,chunk,pivot);
    }
}
