package dev.poligorn.aerocouriers.license;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Owner written onto a license the first time it is worn (GDD §3.2–3.3).
 *
 * @param number sequential license number issued by the server, 0 if unknown (e.g. given before numbering)
 */
public record LicenseOwner(UUID uuid, String name, int number) {
    public static final Codec<LicenseOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(LicenseOwner::uuid),
            Codec.STRING.fieldOf("name").forGetter(LicenseOwner::name),
            Codec.INT.optionalFieldOf("number", 0).forGetter(LicenseOwner::number)
    ).apply(instance, LicenseOwner::new));

    public static final StreamCodec<ByteBuf, LicenseOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LicenseOwner::uuid,
            ByteBufCodecs.STRING_UTF8, LicenseOwner::name,
            ByteBufCodecs.VAR_INT, LicenseOwner::number,
            LicenseOwner::new);

    public boolean isOwnedBy(UUID player) {
        return uuid.equals(player);
    }
}
