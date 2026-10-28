package dev.poligorn.aerocouriers.license;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Rank shown on the license (GDD §4.2). {@link #ADMIN} is granted by an operator and never recomputed from XP.
 * The enum order is persisted only by name, never by ordinal.
 */
public enum LicenseRank implements StringRepresentable {
    RANK1(1, "cadet"),
    RANK2(2, "second"),
    RANK3(3, "first"),
    RANK4(4, "captain"),
    RANK5(5, null),
    ADMIN(0, "admin");

    public static final Codec<LicenseRank> CODEC = StringRepresentable.fromEnum(LicenseRank::values);
    public static final StreamCodec<ByteBuf, LicenseRank> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(LicenseRank::bySerializedName, LicenseRank::getSerializedName);

    private final int level;
    private final String legacyKubeJsSuffix;

    LicenseRank(int level, String legacyKubeJsSuffix) {
        this.level = level;
        this.legacyKubeJsSuffix = legacyKubeJsSuffix;
    }

    /** 1..5 for regular ranks, 0 for {@link #ADMIN}. */
    public int level() {
        return level;
    }

    public boolean isAdmin() {
        return this == ADMIN;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("rank.aerocouriers." + getSerializedName());
    }

    public static LicenseRank byLevel(int level) {
        return switch (level) {
            case 1 -> RANK1;
            case 2 -> RANK2;
            case 3 -> RANK3;
            case 4 -> RANK4;
            case 5 -> RANK5;
            default -> throw new IllegalArgumentException("No regular rank with level " + level);
        };
    }

    public static LicenseRank bySerializedName(String name) {
        for (LicenseRank rank : values()) {
            if (rank.getSerializedName().equals(name)) {
                return rank;
            }
        }
        return RANK1;
    }

    /** Maps {@code kubejs:pilot_license_<suffix>} from the MC-Ikaria pack to the new rank. */
    public static Optional<LicenseRank> fromLegacySuffix(String suffix) {
        for (LicenseRank rank : values()) {
            if (suffix.equals(rank.legacyKubeJsSuffix)) {
                return Optional.of(rank);
            }
        }
        return Optional.empty();
    }
}
