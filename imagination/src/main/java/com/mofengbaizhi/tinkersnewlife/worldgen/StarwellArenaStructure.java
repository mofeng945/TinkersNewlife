package com.mofengbaizhi.tinkersnewlife.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.HashMap;
import java.util.Optional;

public final class StarwellArenaStructure extends Structure {
    public static final Codec<StarwellArenaStructure> CODEC=simpleCodec(StarwellArenaStructure::new);
    public StarwellArenaStructure(StructureSettings settings){super(settings);}
    @Override public StructureType<?> type(){return SleepwakeChurchRegistry.ARENA.get();}
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx){
        var server=ServerLifecycleHooks.getCurrentServer(); var end=server==null?null:server.getLevel(Level.END);
        if(end==null || end.getChunkSource().getGenerator()!=ctx.chunkGenerator())return Optional.empty();
        int cx=ctx.chunkPos().getMiddleBlockX(), cz=ctx.chunkPos().getMiddleBlockZ();
        if((long)cx*cx+(long)cz*cz<1200L*1200L)return Optional.empty();
        var cache=new HashMap<Long,Integer>();
        var site=StarwellSite.choose((u,v)->cache.computeIfAbsent(((long)u<<32)^(v&0xffffffffL), key ->
            ctx.chunkGenerator().getFirstFreeHeight(cx+u,cz+v,Heightmap.Types.WORLD_SURFACE_WG,ctx.heightAccessor(),ctx.randomState())),ctx.heightAccessor().getMinBuildHeight());
        if(site==null || site.floor()+65>ctx.heightAccessor().getMaxBuildHeight())return Optional.empty();
        var rotation=Rotation.values()[site.turns()];
        var center=new BlockPos(cx,site.floor(),cz);
        return Optional.of(new GenerationStub(center,b->b.addPiece(new StarwellArenaPiece(ctx.structureTemplateManager(),center.offset(-88,0,-88),rotation))));
    }
}
