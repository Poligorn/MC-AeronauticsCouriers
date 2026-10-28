package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.TabletButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Builds filter-row widgets for board / mine tabs. */
public final class TabletFilterBar {
    public static final int ROW_H = 18;
    public static final int GAP = 3;
    private static final int CHIP_PAD = 8;
    private static final int DROPDOWN_PAD = 10;

    private TabletFilterBar() {
    }

    public record Built(List<AbstractWidget> widgets, int rows, int height) {
    }

    public static Built buildAdmin(int sx, int y, int contentW, Runnable onChange) {
        AdminOrdersFilterState state = ClientBoard.adminOrderFilters();
        List<AbstractWidget> widgets = new ArrayList<>();
        int x = sx;
        int rowY = y;
        int row = 1;
        Font font = font();
        String statusGlyph = TabletFilterGlyphs.status(font);
        x = placeStatusChip(widgets, x, rowY, contentW, sx, row, statusGlyph, "open", state.statusOpen(), () -> {
            state.toggleStatusOpen();
            onChange.run();
        });
        x = placeStatusChip(widgets, x, rowY, contentW, sx, row, statusGlyph, "in_transit", state.statusInTransit(), () -> {
            state.toggleStatusInTransit();
            onChange.run();
        });
        x += 4;
        String sizeGlyph = TabletFilterGlyphs.size(font);
        for (CargoSize size : CargoSize.values()) {
            Component label = TabletFilterGlyphs.label(sizeGlyph,
                    Component.translatable("cargo.aerocouriers.size." + size.getSerializedName()));
            int w = chipWidth(label);
            if (x + w > sx + contentW) {
                rowY += ROW_H + GAP;
                x = sx;
                row++;
            }
            Component sizeValue = Component.translatable("cargo.aerocouriers.size." + size.getSerializedName());
            Component hint = toggleHint("ui.aerocouriers.filters.size", sizeValue, state.sizes().contains(size));
            widgets.add(chip(x, rowY, w, label, state.sizes().contains(size), () -> {
                state.toggleSize(size);
                onChange.run();
            }, hint));
            x += w + GAP;
        }
        x += 4;
        Component typeValue =
                Component.translatable("ui.aerocouriers.filters.admin.type." + state.contractKind().name().toLowerCase());
        Component typePrefix = Component.literal(TabletFilterGlyphs.typeForAdmin(font, state.contractKind()));
        int typeW = dropdownWidth(typePrefix, typeValue, false);
        if (x + typeW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(dropdown(x, rowY, typeW, typePrefix, typeValue, false, false, () -> {
            state.cycleContractKind();
            onChange.run();
        }, () -> {}, TabletFilterGlyphs.tooltip("ui.aerocouriers.filters.type", typeValue)));
        x += typeW + GAP;
        int sortW = sortDropdownWidth(state.sortKey(), state.sortDescending());
        if (x + sortW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(sortDropdown(x, rowY, sortW, state.sortKey(), state.sortDescending(), () -> {
            state.cycleSortKey();
            onChange.run();
        }, () -> {
            state.toggleSortDirection();
            onChange.run();
        }));
        x += sortW + GAP;
        if (!state.isDefault()) {
            Component reset = Component.translatable("ui.aerocouriers.filters.reset");
            int resetW = chipWidth(reset);
            if (x + resetW > sx + contentW) {
                rowY += ROW_H + GAP;
                x = sx;
            }
            widgets.add(TabletButton.create(x, rowY, resetW, ROW_H, reset, b -> {
                state.reset();
                onChange.run();
            }).hint(Component.translatable("ui.aerocouriers.filters.reset.tooltip")));
        }
        int height = row * ROW_H + (row > 1 ? GAP : 0);
        return new Built(widgets, row, height);
    }

    public static Built buildBoard(int sx, int y, int contentW, Runnable onChange) {
        BoardTabFilterState state = ClientBoard.boardFilters();
        List<AbstractWidget> widgets = new ArrayList<>();
        int x = sx;
        int rowY = y;
        int row = 1;
        Font font = font();
        String sizeGlyph = TabletFilterGlyphs.size(font);
        for (CargoSize size : CargoSize.values()) {
            Component label = TabletFilterGlyphs.label(sizeGlyph,
                    Component.translatable("cargo.aerocouriers.size." + size.getSerializedName()));
            int w = chipWidth(label);
            if (x + w > sx + contentW) {
                rowY += ROW_H + GAP;
                x = sx;
                row++;
            }
            Component sizeValue = Component.translatable("cargo.aerocouriers.size." + size.getSerializedName());
            Component hint = toggleHint("ui.aerocouriers.filters.size", sizeValue, state.sizes().contains(size));
            widgets.add(chip(x, rowY, w, label, state.sizes().contains(size), () -> {
                state.toggleSize(size);
                onChange.run();
            }, hint));
            x += w + GAP;
        }
        x += 4;
        String rankGlyph = TabletFilterGlyphs.rank(font);
        Component rankValue = state.onlyAvailable()
                ? Component.translatable("ui.aerocouriers.filters.rank_available.short_on")
                : Component.translatable("ui.aerocouriers.filters.rank_available.short_off");
        Component rankLabel = TabletFilterGlyphs.label(rankGlyph, rankValue);
        int rankW = chipWidth(rankLabel);
        if (x + rankW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        Component rankHintValue = state.onlyAvailable()
                ? Component.translatable("ui.aerocouriers.filters.rank_available")
                : Component.translatable("ui.aerocouriers.filters.rank_available.short_off");
        widgets.add(chip(x, rowY, rankW, rankLabel, state.onlyAvailable(), () -> {
            state.toggleOnlyAvailable();
            onChange.run();
        }, TabletFilterGlyphs.tooltip("ui.aerocouriers.filters.rank_available", rankHintValue)));
        x += rankW + GAP;
        Component typeValue =
                Component.translatable("ui.aerocouriers.filters.type." + state.contractKind().name().toLowerCase());
        Component typePrefix = Component.literal(TabletFilterGlyphs.typeForBoard(font, state.contractKind()));
        int typeW = dropdownWidth(typePrefix, typeValue, false);
        if (x + typeW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(dropdown(x, rowY, typeW, typePrefix, typeValue, false, false, () -> {
            state.cycleContractKind();
            onChange.run();
        }, () -> {}, TabletFilterGlyphs.tooltip("ui.aerocouriers.filters.type", typeValue)));
        x += typeW + GAP;
        int sortW = sortDropdownWidth(state.sortKey(), state.sortDescending());
        if (x + sortW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(sortDropdown(x, rowY, sortW, state.sortKey(), state.sortDescending(), () -> {
            state.cycleSortKey();
            onChange.run();
        }, () -> {
            state.toggleSortDirection();
            onChange.run();
        }));
        x += sortW + GAP;
        addReset(widgets, sx, contentW, rowY, x, false, MineListSection.CURRENT, onChange);
        int height = row * ROW_H + (row > 1 ? GAP : 0);
        return new Built(widgets, row, height);
    }

    public static Built buildMine(int sx, int y, int contentW, MineListSection section, Runnable onChange) {
        MineTabFilterState state = ClientBoard.mineFilters(section);
        List<AbstractWidget> widgets = new ArrayList<>();
        int x = sx;
        int rowY = y;
        int row = 1;
        Font font = font();
        String statusGlyph = TabletFilterGlyphs.status(font);
        if (section == MineListSection.CURRENT) {
            x = placeStatusChip(widgets, x, rowY, contentW, sx, row, statusGlyph, "open", state.statusOpen(), () -> {
                state.toggleStatusOpen();
                onChange.run();
            });
            x = placeStatusChip(widgets, x, rowY, contentW, sx, row, statusGlyph, "in_transit", state.statusInTransit(),
                    () -> {
                        state.toggleStatusInTransit();
                        onChange.run();
                    });
        } else {
            for (MineHistoryBucket bucket : MineHistoryBucket.values()) {
                String key = bucket.name().toLowerCase();
                Component shortLabel = Component.translatable("ui.aerocouriers.filters.status." + key + ".short");
                Component label = TabletFilterGlyphs.label(statusGlyph, shortLabel);
                int w = chipWidth(label);
                if (x + w > sx + contentW) {
                    rowY += ROW_H + GAP;
                    x = sx;
                    row++;
                }
                Component fullStatus = Component.translatable("ui.aerocouriers.filters.status." + key);
                Component hint = toggleHint("ui.aerocouriers.filters.status", fullStatus, bucket.enabled(state));
                widgets.add(chip(x, rowY, w, label, bucket.enabled(state), () -> {
                    state.toggleHistoryStatus(bucket);
                    onChange.run();
                }, hint));
                x += w + GAP;
            }
        }
        x += 4;
        String roleGlyph = TabletFilterGlyphs.role(font);
        Component courierShort = Component.translatable("ui.aerocouriers.filters.role.courier.short");
        Component courierLabel = TabletFilterGlyphs.label(roleGlyph, courierShort);
        int courierW = chipWidth(courierLabel);
        if (x + courierW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        Component courierFull = Component.translatable("ui.aerocouriers.filters.role.courier");
        widgets.add(chip(x, rowY, courierW, courierLabel, state.roleCourier(), () -> {
            state.toggleRoleCourier();
            onChange.run();
        }, toggleHint("ui.aerocouriers.filters.role.courier", courierFull, state.roleCourier())));
        x += courierW + GAP;
        Component customerShort = Component.translatable("ui.aerocouriers.filters.role.customer.short");
        Component customerLabel = TabletFilterGlyphs.label(roleGlyph, customerShort);
        int customerW = chipWidth(customerLabel);
        if (x + customerW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        Component customerFull = Component.translatable("ui.aerocouriers.filters.role.customer");
        widgets.add(chip(x, rowY, customerW, customerLabel, state.roleCustomer(), () -> {
            state.toggleRoleCustomer();
            onChange.run();
        }, toggleHint("ui.aerocouriers.filters.role.customer", customerFull, state.roleCustomer())));
        x += customerW + GAP;
        int sortW = sortDropdownWidth(state.sortKey(), state.sortDescending());
        if (x + sortW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(sortDropdown(x, rowY, sortW, state.sortKey(), state.sortDescending(), () -> {
            state.cycleSortKey(section);
            onChange.run();
        }, () -> {
            state.toggleSortDirection();
            onChange.run();
        }));
        x += sortW + GAP;
        addReset(widgets, sx, contentW, rowY, x, true, section, onChange);
        int height = row * ROW_H + (row > 1 ? GAP : 0);
        return new Built(widgets, row, height);
    }

    private static int placeStatusChip(
            List<AbstractWidget> widgets,
            int x,
            int rowY,
            int contentW,
            int sx,
            int row,
            String statusGlyph,
            String statusKey,
            boolean selected,
            Runnable onPress) {
        Component shortLabel = Component.translatable("ui.aerocouriers.filters.status." + statusKey + ".short");
        Component label = TabletFilterGlyphs.label(statusGlyph, shortLabel);
        int w = chipWidth(label);
        if (x + w > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
        }
        Component fullStatus = Component.translatable("ui.aerocouriers.filters.status." + statusKey);
        widgets.add(chip(x, rowY, w, label, selected, onPress,
                toggleHint("ui.aerocouriers.filters.status", fullStatus, selected)));
        return x + w + GAP;
    }

    private static void addReset(
            List<AbstractWidget> widgets,
            int sx,
            int contentW,
            int rowY,
            int x,
            boolean mine,
            MineListSection section,
            Runnable onChange) {
        boolean show = mine
                ? !ClientBoard.mineFilters(section).isDefault(section)
                : !ClientBoard.boardFilters().isDefault();
        if (!show) {
            return;
        }
        Component reset = Component.translatable("ui.aerocouriers.filters.reset");
        int resetW = chipWidth(reset);
        if (x + resetW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
        }
        widgets.add(TabletButton.create(x, rowY, resetW, ROW_H, reset, b -> {
            if (mine) {
                ClientBoard.mineFilters(section).reset(section);
            } else {
                ClientBoard.boardFilters().reset();
            }
            onChange.run();
        }).hint(Component.translatable("ui.aerocouriers.filters.reset.tooltip")));
    }

    private static TabletFilterChip chip(
            int x, int y, int w, Component label, boolean selected, Runnable onPress, Component hint) {
        return new TabletFilterChip(x, y, w, ROW_H, label, onPress).selected(selected).hint(hint);
    }

    private static TabletFilterDropdown sortDropdown(
            int x, int y, int w, BoardSortKey key, boolean desc, Runnable cycle, Runnable toggleDir) {
        Font font = font();
        Component value = Component.translatable(key.translationKey());
        Component prefix = Component.literal(TabletFilterGlyphs.sort(font));
        return dropdown(x, y, w, prefix, value, true, desc, cycle, toggleDir,
                TabletFilterGlyphs.tooltip("ui.aerocouriers.filters.sort", value));
    }

    private static TabletFilterDropdown dropdown(
            int x,
            int y,
            int w,
            Component prefix,
            Component value,
            boolean sortStyle,
            boolean sortDescending,
            Runnable cycle,
            Runnable toggleDir,
            Component hint) {
        return new TabletFilterDropdown(x, y, w, ROW_H, prefix, value)
                .sortStyle(sortDescending)
                .onCycle(cycle)
                .onToggleDirection(toggleDir)
                .hint(hint);
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    private static int chipWidth(Component label) {
        return Math.max(16, CHIP_PAD + font().width(label));
    }

    private static int dropdownWidth(Component prefix, Component value, boolean sortStyle) {
        Font f = font();
        String valueRaw = value.getString() + (sortStyle ? " ▼" : " ▾");
        return DROPDOWN_PAD + f.width(prefix.getString() + " ") + f.width(valueRaw)
                + (sortStyle ? TabletFilterDropdown.ARROW_W : 0);
    }

    private static int sortDropdownWidth(BoardSortKey key, boolean desc) {
        return dropdownWidth(Component.literal(TabletFilterGlyphs.sort(font())),
                Component.translatable(key.translationKey()), true);
    }

    private static Component toggleHint(String filterNameKey, Component value, boolean enabled) {
        Component state = Component.translatable(enabled
                ? "ui.aerocouriers.filters.toggle.on"
                : "ui.aerocouriers.filters.toggle.off");
        return TabletFilterGlyphs.tooltip(filterNameKey, Component.empty().append(value).append(" (").append(state).append(")"));
    }
}
