package dev.poligorn.aerocouriers.client;

import java.lang.reflect.Field;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Reflection bridge for {@link EditBox} fields needed by {@link PaperEditBox#renderWidget}. */
final class EditBoxAccess {
    private static final Field DISPLAY_POS;
    private static final Field HIGHLIGHT_POS;
    private static final Field HINT;
    private static final Field SUGGESTION;
    private static final Field FORMATTER;
    private static final Field FOCUSED_TIME;
    private static final Field TEXT_COLOR;
    private static final Field TEXT_COLOR_UNEDITABLE;
    private static final Field IS_EDITABLE;
    private static final Field MAX_LENGTH;

    static {
        try {
            Class<?> type = EditBox.class;
            DISPLAY_POS = declared(type, "displayPos");
            MAX_LENGTH = declared(type, "maxLength");
            HIGHLIGHT_POS = declared(type, "highlightPos");
            HINT = declared(type, "hint");
            SUGGESTION = declared(type, "suggestion");
            FORMATTER = declared(type, "formatter");
            FOCUSED_TIME = declared(type, "focusedTime");
            TEXT_COLOR = declared(type, "textColor");
            TEXT_COLOR_UNEDITABLE = declared(type, "textColorUneditable");
            IS_EDITABLE = declared(type, "isEditable");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private EditBoxAccess() {
    }

    private static Field declared(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    static int displayPos(EditBox box) {
        return getInt(DISPLAY_POS, box);
    }

    static int highlightPos(EditBox box) {
        return getInt(HIGHLIGHT_POS, box);
    }

    @Nullable
    static Component hint(EditBox box) {
        return get(HINT, box);
    }

    @Nullable
    static String suggestion(EditBox box) {
        return get(SUGGESTION, box);
    }

    static BiFunction<String, Integer, FormattedCharSequence> formatter(EditBox box) {
        return get(FORMATTER, box);
    }

    static long focusedTime(EditBox box) {
        return getLong(FOCUSED_TIME, box);
    }

    static int textColor(EditBox box) {
        return getInt(TEXT_COLOR, box);
    }

    static int textColorUneditable(EditBox box) {
        return getInt(TEXT_COLOR_UNEDITABLE, box);
    }

    static boolean isEditable(EditBox box) {
        return getBoolean(IS_EDITABLE, box);
    }

    static int maxLength(EditBox box) {
        return getInt(MAX_LENGTH, box);
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Field field, EditBox box) {
        try {
            return (T) field.get(box);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int getInt(Field field, EditBox box) {
        try {
            return field.getInt(box);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long getLong(Field field, EditBox box) {
        try {
            return field.getLong(box);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean getBoolean(Field field, EditBox box) {
        try {
            return field.getBoolean(box);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
