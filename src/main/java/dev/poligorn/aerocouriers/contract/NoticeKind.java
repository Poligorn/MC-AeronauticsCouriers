package dev.poligorn.aerocouriers.contract;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Category and priority of a server event. The server always logs; the client picks toast / chat / sound per
 * category from its own settings. {@link Priority#URGENT} uses the separate «critical» settings row.
 */
public record NoticeKind(Category category, Priority priority) {
    public static final NoticeKind ORDERS = new NoticeKind(Category.ORDERS, Priority.NORMAL);
    public static final NoticeKind URGENT = new NoticeKind(Category.ORDERS, Priority.URGENT);
    public static final NoticeKind MONEY = new NoticeKind(Category.MONEY, Priority.NORMAL);
    public static final NoticeKind CARGO = new NoticeKind(Category.CARGO, Priority.NORMAL);
    public static final NoticeKind TUTORIAL = new NoticeKind(Category.TUTORIAL, Priority.NORMAL);
    public static final NoticeKind ADMIN = new NoticeKind(Category.ADMIN, Priority.NORMAL);

    public enum Category {
        ORDERS, MONEY, CARGO, TUTORIAL, BOARD, ADMIN;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Category byOrdinal(int ordinal) {
            Category[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : ORDERS;
        }
    }

    public enum Priority {
        SILENT, NORMAL, URGENT;

        public static Priority byOrdinal(int ordinal) {
            Priority[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : NORMAL;
        }
    }

    public boolean urgent() {
        return priority == Priority.URGENT;
    }

    /** Kind for call sites that predate categories, derived from the message's translation key. */
    public static NoticeKind classify(Component message) {
        if (!(message.getContents() instanceof TranslatableContents translatable)) {
            return ORDERS;
        }
        String key = translatable.getKey();
        if (key.startsWith("admin.aerocouriers.")) {
            return ADMIN;
        }
        if (key.contains(".tutorial.")) {
            return TUTORIAL;
        }
        if (key.equals("ui.aerocouriers.inbox.cargo_return")) {
            return CARGO;
        }
        if (key.contains(".notify.failed.") || key.endsWith(".notify.pickup_blocked")
                || key.contains(".notify.deadline_") || key.endsWith(".notify.late")
                || key.endsWith(".notify.far_container")) {
            return URGENT;
        }
        if (key.equals("contract.aerocouriers.delivered") || key.equals("contract.aerocouriers.delivered_late")
                || key.endsWith(".notify.rank_up") || key.endsWith(".trainee.advance")
                || key.startsWith("license.aerocouriers.")) {
            return MONEY;
        }
        return ORDERS;
    }
}
