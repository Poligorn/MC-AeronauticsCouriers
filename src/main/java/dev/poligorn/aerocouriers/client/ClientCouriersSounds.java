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

    /** Order-notification bell; also gated by {@link CouriersClientConfig#notificationSoundsEnabled()}. */
    public static void playNotification() {
        if (!CouriersClientConfig.notificationSoundsEnabled()) {
            return;
        }
        playUi(ModSounds.NOTIFICATION.get());
    }

    public static void playUi(SoundEvent sound) {
        if (!CouriersClientConfig.couriersSoundsEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        float volume = CouriersClientConfig.couriersSoundsVolume();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, 1.0F));
    }
}
