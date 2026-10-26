package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> REGISTER =
            DeferredRegister.create(Registries.SOUND_EVENT, AeronauticsCouriers.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> NOTIFICATION =
            REGISTER.register("notification", () -> SoundEvent.createVariableRangeEvent(AeronauticsCouriers.id("notification")));

    private ModSounds() {
    }
}
