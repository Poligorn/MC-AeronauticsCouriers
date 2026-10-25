package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/** GDD §5.1. Only {@link #PLAYER} is created so far; village contracts arrive in iteration 5. */
public enum ContractType implements StringRepresentable {
    VILLAGE,
    PLAYER;

    public static final Codec<ContractType> CODEC = StringRepresentable.fromEnum(ContractType::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
