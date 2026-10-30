package dev.poligorn.aerocouriers.config;

import dev.poligorn.aerocouriers.contract.NoticeKind;
import java.util.Locale;

/** Client delivery channels of a notification; the Mail log is always written and is not a setting. */
public enum NoticeChannel {
    TOAST, SOUND, CHAT;

    /** Rows of the settings matrix: urgent events are one row whatever their category. */
    public enum Row {
        CRITICAL(true, true, true),
        ORDERS(true, true, false),
        MONEY(true, true, false),
        CARGO(true, true, false),
        TUTORIAL(true, false, false),
        BOARD(false, false, false),
        ADMIN(true, true, false);

        private final boolean toast;
        private final boolean sound;
        private final boolean chat;

        Row(boolean toast, boolean sound, boolean chat) {
            this.toast = toast;
            this.sound = sound;
            this.chat = chat;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public boolean defaultToast() {
            return toast;
        }

        public boolean defaultSound() {
            return sound;
        }

        public boolean defaultChat() {
            return chat;
        }

        public static Row of(NoticeKind kind) {
            if (kind.priority() == NoticeKind.Priority.URGENT) {
                return CRITICAL;
            }
            return switch (kind.category()) {
                case ORDERS -> ORDERS;
                case MONEY -> MONEY;
                case CARGO -> CARGO;
                case TUTORIAL -> TUTORIAL;
                case BOARD -> BOARD;
                case ADMIN -> ADMIN;
            };
        }
    }
}
