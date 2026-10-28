package dev.poligorn.aerocouriers.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A point with a zone radius (GDD §5.1), used for both pickup and delivery. */
public record Destination(ResourceKey<Level> dimension, BlockPos pos, int radius) {
    public static final Codec<Destination> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Destination::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Destination::pos),
            Codec.INT.fieldOf("radius").forGetter(Destination::radius)
    ).apply(instance, Destination::new));

    public boolean contains(ResourceKey<Level> otherDimension, Vec3 point) {
        return dimension.equals(otherDimension) && center().distanceToSqr(point) <= (double) radius * radius;
    }

    public boolean within(ResourceKey<Level> otherDimension, Vec3 point, double distance) {
        return dimension.equals(otherDimension) && center().distanceToSqr(point) <= distance * distance;
    }

    /** Straight-line distance in blocks (GDD §4.1 {@code D}). */
    public double distanceTo(Destination other) {
        return Math.sqrt(pos.distSqr(other.pos));
    }

    public Vec3 center() {
        return Vec3.atCenterOf(pos);
    }
}
