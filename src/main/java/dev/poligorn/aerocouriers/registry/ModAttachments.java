package dev.poligorn.aerocouriers.registry;

import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.license.CourierProfile;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AeronauticsCouriers.MOD_ID);

    public static final Supplier<AttachmentType<CourierProfile>> COURIER_PROFILE = REGISTER.register(
            "courier_profile",
            () -> AttachmentType.builder(() -> CourierProfile.EMPTY)
                    .serialize(CourierProfile.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
