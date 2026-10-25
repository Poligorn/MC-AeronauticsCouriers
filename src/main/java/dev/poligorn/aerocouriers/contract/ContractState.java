package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * GDD §5.2:
 * <pre>
 * OPEN → ACCEPTED → IN_TRANSIT → DELIVERED
 * OPEN → EXPIRED
 * ACCEPTED | IN_TRANSIT → FAILED
 * </pre>
 */
public enum ContractState implements StringRepresentable {
    OPEN,
    ACCEPTED,
    IN_TRANSIT,
    DELIVERED,
    EXPIRED,
    FAILED;

    public static final Codec<ContractState> CODEC = StringRepresentable.fromEnum(ContractState::values);
    public static final StreamCodec<ByteBuf, ContractState> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(name -> valueOf(name.toUpperCase(Locale.ROOT)), ContractState::getSerializedName);

    public boolean canTransitionTo(ContractState next) {
        return switch (this) {
            case OPEN -> next == ACCEPTED || next == EXPIRED;
            case ACCEPTED -> next == IN_TRANSIT || next == FAILED;
            case IN_TRANSIT -> next == DELIVERED || next == FAILED;
            case DELIVERED, EXPIRED, FAILED -> false;
        };
    }

    /** A courier is attached and the deadline is running. */
    public boolean isActive() {
        return this == ACCEPTED || this == IN_TRANSIT;
    }

    public boolean isFinal() {
        return this == DELIVERED || this == EXPIRED || this == FAILED;
    }

    public Component displayName() {
        return Component.translatable("contract.aerocouriers.state." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
