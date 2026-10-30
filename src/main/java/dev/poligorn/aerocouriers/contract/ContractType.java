package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * GDD §5.1. {@link #VILLAGE} is an NPC station order (iteration 5). {@link #TUTORIAL} is the onboarding order: issued by
 * the courier service, visible to one player only, no reward, collateral or deadline, cannot fail.
 */
public enum ContractType implements StringRepresentable {
    VILLAGE,
    PLAYER,
    TUTORIAL;

    public boolean isNpc() {
        return this == VILLAGE;
    }

    public boolean isTutorial() {
        return this == TUTORIAL;
    }

    public static final Codec<ContractType> CODEC = StringRepresentable.fromEnum(ContractType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
