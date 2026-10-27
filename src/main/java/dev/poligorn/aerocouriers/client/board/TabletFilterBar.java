package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.client.ClientBoard;
import dev.poligorn.aerocouriers.client.TabletButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Builds filter-row widgets for board / mine tabs. */
public final class TabletFilterBar {
    public static final int ROW_H = 18;
    public static final int GAP = 4;

    private TabletFilterBar() {
    }

    public record Built(List<AbstractWidget> widgets, int rows, int height) {
    }

    public static Built buildBoard(int sx, int y, int contentW, Runnable onChange) {
        BoardTabFilterState state = ClientBoard.boardFilters();
        List<AbstractWidget> widgets = new ArrayList<>();
        int x = sx + 38;
        int rowY = y;
        int row = 1;
        for (CargoSize size : CargoSize.values()) {
            int w = 22;
            if (x + w > sx + contentW) {
                rowY += ROW_H + GAP;
                x = sx;
                row++;
            }
            widgets.add(chip(x, rowY, w, Component.translatable("cargo.aerocouriers.size." + size.getSerializedName()),
                    state.sizes().contains(size), () -> {
                        state.toggleSize(size);
                        onChange.run();
                    },
                    Component.translatable("ui.aerocouriers.filters.size." + size.getSerializedName())));
            x += w + GAP;
        }
        x += 6;
        int rankW = 112;
        if (x + rankW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(chip(x, rowY, rankW,
                Component.translatable("ui.aerocouriers.filters.rank_available"),
                state.onlyAvailable(),
                () -> {
                    state.toggleOnlyAvailable();
                    onChange.run();
                },
                Component.translatable("ui.aerocouriers.filters.rank_available.tooltip")));
        x += rankW + GAP;
        int typeW = 98;
        if (x + typeW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(dropdown(x, rowY, typeW,
                Component.translatable("ui.aerocouriers.filters.type"),
                Component.translatable("ui.aerocouriers.filters.type." + state.contractKind().name().toLowerCase()),
                false, false,
                () -> {
                    state.cycleContractKind();
                    onChange.run();
                },
                () -> {},
                Component.translatable("ui.aerocouriers.filters.type.tooltip")));
        x += typeW + GAP;
        int sortW = 110;
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
        int x = sx + 44;
        int rowY = y;
        int row = 1;
        if (section == MineListSection.CURRENT) {
            x = placeChipRow(widgets, x, rowY, contentW, sx, row,
                    Component.translatable("ui.aerocouriers.filters.status.open"),
                    state.statusOpen(),
                    () -> {
                        state.toggleStatusOpen();
                        onChange.run();
                    },
                    "ui.aerocouriers.filters.status.open.tooltip");
            x = placeChipRow(widgets, x, rowY, contentW, sx, row,
                    Component.translatable("ui.aerocouriers.filters.status.in_transit"),
                    state.statusInTransit(),
                    () -> {
                        state.toggleStatusInTransit();
                        onChange.run();
                    },
                    "ui.aerocouriers.filters.status.in_transit.tooltip");
        } else {
            for (MineHistoryBucket bucket : MineHistoryBucket.values()) {
                String key = bucket.name().toLowerCase();
                int w = Math.max(52, 12 + key.length() * 5);
                if (x + w > sx + contentW) {
                    rowY += ROW_H + GAP;
                    x = sx;
                    row++;
                }
                widgets.add(chip(x, rowY, w,
                        Component.translatable("ui.aerocouriers.filters.status." + key),
                        bucket.enabled(state),
                        () -> {
                            state.toggleHistoryStatus(bucket);
                            onChange.run();
                        },
                        Component.translatable("ui.aerocouriers.filters.status." + key + ".tooltip")));
                x += w + GAP;
            }
        }
        x += 6;
        int courierW = 64;
        if (x + courierW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(chip(x, rowY, courierW,
                Component.translatable("ui.aerocouriers.filters.role.courier"),
                state.roleCourier(),
                () -> {
                    state.toggleRoleCourier();
                    onChange.run();
                },
                Component.translatable("ui.aerocouriers.filters.role.courier.tooltip")));
        x += courierW + GAP;
        int customerW = 72;
        if (x + customerW > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
            row++;
        }
        widgets.add(chip(x, rowY, customerW,
                Component.translatable("ui.aerocouriers.filters.role.customer"),
                state.roleCustomer(),
                () -> {
                    state.toggleRoleCustomer();
                    onChange.run();
                },
                Component.translatable("ui.aerocouriers.filters.role.customer.tooltip")));
        x += customerW + GAP;
        int sortW = 110;
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

    private static int placeChipRow(
            List<AbstractWidget> widgets,
            int x,
            int rowY,
            int contentW,
            int sx,
            int row,
            Component label,
            boolean selected,
            Runnable onPress,
            String hintKey) {
        int w = Math.max(48, 12 + label.getString().length() * 5);
        if (x + w > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
        }
        widgets.add(chip(x, rowY, w, label, selected, onPress, Component.translatable(hintKey)));
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
        if (x + 58 > sx + contentW) {
            rowY += ROW_H + GAP;
            x = sx;
        }
        widgets.add(TabletButton.create(x, rowY, 58, ROW_H,
                Component.translatable("ui.aerocouriers.filters.reset"), b -> {
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
        return dropdown(x, y, w,
                Component.translatable("ui.aerocouriers.filters.sort"),
                Component.translatable(key.translationKey()),
                true, desc, cycle, toggleDir,
                Component.translatable("ui.aerocouriers.filters.sort.tooltip"));
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
}
