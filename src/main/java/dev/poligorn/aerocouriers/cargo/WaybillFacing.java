package dev.poligorn.aerocouriers.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Picks the world face that shows the paper waybill, once, when the order is published.
 * The block {@code facing} is this face rotated 90° counter-clockwise: models put the door on
 * {@code north} and the waybill on {@code east}, and blockstate {@code y} follows {@code facing}.
 */
public final class WaybillFacing {
    /** Horizontal distance below which position is ignored and the player's look is used. */
    public static final double LOOK_RANGE = 1.5;
    /** |dx| − |dz| below this (blocks) counts as a diagonal; look decides the axis. */
    public static final double DIAGONAL_EPSILON = 0.25;
    /** Beyond this horizontal distance (or another dimension / no position) keep the legacy east face. */
    public static final double MAX_RANGE = 64.0;
    /** Legacy spawn: {@code facing=north} puts the waybill on the east side. */
    public static final Direction DEFAULT_FACE = Direction.EAST;

    private WaybillFacing() {
    }

    /** Block {@code facing} that places the waybill on {@code waybillFace}. */
    public static Direction toContainerFacing(Direction waybillFace) {
        Direction face = horizontal(waybillFace);
        return face.getCounterClockWise();
    }

    /** World face the paper sits on for a given container {@code facing}. */
    public static Direction fromContainerFacing(Direction containerFacing) {
        return horizontal(containerFacing).getClockWise();
    }

    /**
     * Side of the cargo structure that should show the waybill, relative to {@code anchor}
     * (controller / bottom block). Height is ignored. Missing player, another dimension, or
     * {@link #MAX_RANGE} → {@link #DEFAULT_FACE}.
     */
    public static Direction choose(@Nullable ServerPlayer player, ResourceKey<Level> cargoDimension,
                                   BlockPos anchor, CargoSize size) {
        Vec3 center = CargoStructure.horizontalCenter(anchor, size);
        if (player == null) {
            return choose(null, null, null, cargoDimension, center);
        }
        return choose(player.level().dimension(), player.position(), player.getDirection(), cargoDimension, center);
    }

    public static Direction choose(@Nullable ResourceKey<Level> playerDimension, @Nullable Vec3 playerPos,
                                   @Nullable Direction look, ResourceKey<Level> cargoDimension, Vec3 containerCenter) {
        if (playerDimension == null || playerPos == null || !playerDimension.equals(cargoDimension)) {
            return DEFAULT_FACE;
        }
        double dx = playerPos.x - containerCenter.x;
        double dz = playerPos.z - containerCenter.z;
        double horizontal = Math.hypot(dx, dz);
        if (horizontal > MAX_RANGE) {
            return DEFAULT_FACE;
        }
        Direction lookFace = horizontalOrNull(look);
        if (horizontal < LOOK_RANGE) {
            return lookFace != null ? lookFace : DEFAULT_FACE;
        }
        if (Math.abs(Math.abs(dx) - Math.abs(dz)) < DIAGONAL_EPSILON) {
            return lookFace != null ? lookFace : nearestAxis(dx, dz);
        }
        return nearestAxis(dx, dz);
    }

    private static Direction nearestAxis(double dx, double dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    static Direction horizontal(Direction direction) {
        return direction.getAxis().isHorizontal() ? direction : DEFAULT_FACE;
    }

    private static @Nullable Direction horizontalOrNull(@Nullable Direction direction) {
        if (direction == null || !direction.getAxis().isHorizontal()) {
            return null;
        }
        return direction;
    }
}
