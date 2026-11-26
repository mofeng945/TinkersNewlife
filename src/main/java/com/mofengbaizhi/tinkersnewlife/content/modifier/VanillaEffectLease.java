package com.mofengbaizhi.tinkersnewlife.content.modifier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Reference-counted sources sharing one vanilla effect; removes/restores only the instance this helper supplied. */
final class VanillaEffectLease {
    private static final String ROOT="tinkersnewlife:vanilla_effect_leases";
    private static final String PREVIOUS="previous", APPLIED="applied";
    private VanillaEffectLease() {}

    static void maintain(LivingEntity entity,MobEffect effect,String source,boolean wanted,int amplifier) {
        CompoundTag root=entity.getPersistentData().getCompound(ROOT);
        String id=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(effect).toString().replace(':','_');
        CompoundTag state=root.getCompound(id);
        CompoundTag leases=state.getCompound("leases");
        var current=entity.getEffect(effect);
        boolean ours=state.contains(APPLIED)&&owned(current,state.getInt(APPLIED));
        if(wanted)leases.putInt(source,Math.max(0,amplifier));else leases.remove(source);
        if(leases.isEmpty()){
            if(ours){entity.removeEffect(effect);if(state.contains(PREVIOUS))entity.addEffect(MobEffectInstance.load(state.getCompound(PREVIOUS)));}
            root.remove(id);save(entity,root);return;
        }
        int desired=0;for(String key:leases.getAllKeys())desired=Math.max(desired,leases.getInt(key));
        if(current!=null&&current.getAmplifier()>desired&&!ours){state.put("leases",leases);root.put(id,state);save(entity,root);return;}
        if(ours&&current.getAmplifier()==desired&&current.getDuration()>20){state.put("leases",leases);root.put(id,state);save(entity,root);return;}
        if(!ours&&current!=null&&current.getAmplifier()>=desired){state.put("leases",leases);root.put(id,state);save(entity,root);return;}
        if(!ours&&current!=null&&!state.contains(PREVIOUS))state.put(PREVIOUS,current.save(new CompoundTag()));
        MobEffectInstance hidden=current==null?null:new MobEffectInstance(current);
        entity.addEffect(new MobEffectInstance(effect,40,desired,true,false,true,hidden,java.util.Optional.empty()));
        state.put("leases",leases);state.putInt(APPLIED,desired);root.put(id,state);save(entity,root);
    }
    private static boolean owned(MobEffectInstance effect,int amplifier){
        return effect!=null&&effect.getAmplifier()==amplifier&&effect.getDuration()<=40&&effect.isAmbient()&&effect.isVisible();
    }
    private static void save(LivingEntity entity,CompoundTag root){
        if(root.isEmpty())entity.getPersistentData().remove(ROOT);else entity.getPersistentData().put(ROOT,root);
    }
}
