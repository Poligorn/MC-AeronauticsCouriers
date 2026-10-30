package dev.poligorn.aerocouriers.client;

import dev.poligorn.aerocouriers.config.CouriersClientConfig;
import dev.poligorn.aerocouriers.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Client UI feedback (tablet, accept/deny); respects {@link CouriersClientConfig} sound toggles. */
public final class ClientCouriersSounds {
    private ClientCouriersSounds() {
    }

    public static void playOrderAccept() {
        playUi(ModSounds.ORDER_ACCEPT.get());
    }

    public static void playOrderDeny() {
        playUi(ModSounds.ORDER_DENY.get());
    }

    public static void playTabletOpen() {
        playUi(ModSounds.TABLET_OPEN.get());
    }

    public static void playTabletClose() {
        playUi(ModSounds.TABLET_CLOSE.get());
    }

    public static void playWaybillStamp() {
        playUi(ModSounds.WAYBILL_SIGN.get());
    }

    /** Order-notification bell; also gated by {@link CouriersClientConfig#notificationSoundsEnabled()}. */
    public static void playNotification() {
        if (!CouriersClientConfig.notificationSoundsEnabled()) {
            return;
        }
        playUi(ModSounds.NOTIFICATION.get());
    }

    /** Urgent alert: the same bell, lower and doubled, so it is told apart without a new sound asset. */
    public static void playUrgent() {
        if (!CouriersClientConfig.notificationSoundsEnabled()) {
            return;
        }
        playUi(ModSounds.NOTIFICATION.get(), 0.7F);
        playUi(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 0.6F);
    }

    public static void playUi(SoundEvent sound) {
        playUi(sound, 1.0F);
    }

    public static void playUi(SoundEvent sound, float pitch) {
        if (!CouriersClientConfig.couriersSoundsEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        float volume = CouriersClientConfig.couriersSoundsVolume();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
