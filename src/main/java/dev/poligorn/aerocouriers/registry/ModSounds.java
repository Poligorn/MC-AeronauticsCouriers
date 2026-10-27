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

    public static final DeferredHolder<SoundEvent, SoundEvent> ORDER_ACCEPT =
            REGISTER.register("order_accept", () -> SoundEvent.createVariableRangeEvent(AeronauticsCouriers.id("order_accept")));

    public static final DeferredHolder<SoundEvent, SoundEvent> ORDER_DENY =
            REGISTER.register("order_deny", () -> SoundEvent.createVariableRangeEvent(AeronauticsCouriers.id("order_deny")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TABLET_OPEN =
            REGISTER.register("tablet_open", () -> SoundEvent.createVariableRangeEvent(AeronauticsCouriers.id("tablet_open")));

    public static final DeferredHolder<SoundEvent, SoundEvent> TABLET_CLOSE =
            REGISTER.register("tablet_close", () -> SoundEvent.createVariableRangeEvent(AeronauticsCouriers.id("tablet_close")));

    public static final DeferredHolder<SoundEvent, SoundEvent> WAYBILL_SIGN =
            REGISTER.register("waybill_sign", () -> SoundEvent.createVariableRangeEvent(AeronauticsCouriers.id("waybill_sign")));

    public static final DeferredHolder<SoundEvent, SoundEvent> CARGO_PACK =
            REGISTER.register("cargo_pack", () -> SoundEvent.createVariableRangeEvent(AeronauticsCouriers.id("cargo_pack")));

    private ModSounds() {
    }
}
