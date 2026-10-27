package dev.poligorn.aerocouriers.sound;

import dev.poligorn.aerocouriers.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** World-position feedback for nearby players (waybill seal, cargo pack). */
public final class CouriersSounds {
    private CouriersSounds() {
    }

    public static void playWaybillSealed(ServerLevel level, BlockPos pos) {
        playAt(level, pos, ModSounds.WAYBILL_SIGN.get());
        playAt(level, pos, ModSounds.CARGO_PACK.get());
    }

    private static void playAt(ServerLevel level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
