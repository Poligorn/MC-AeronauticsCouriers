package dev.poligorn.aerocouriers.contract;

import dev.poligorn.aerocouriers.cargo.CargoSize;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Validates player cargo against the largest single courier container (XL). */
public final class ManifestLimits {
    private ManifestLimits() {
    }

    public static int maxStacks() {
        return CouriersServerConfig.containerSlots(CargoSize.XL);
    }

    public static int maxWeight() {
        return CouriersServerConfig.containerMaxWeight(CargoSize.XL);
    }

    public static int stackCount(List<ItemStack> stacks) {
        int n = 0;
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public static Optional<Component> validateMessage(List<ItemStack> stacks) {
        int count = stackCount(stacks);
        if (count == 0) {
            return Optional.empty();
        }
        int maxStacks = maxStacks();
        if (count > maxStacks) {
            return Optional.of(Component.translatable("contract.aerocouriers.error.cargo_too_many_stacks", count, maxStacks));
        }
        double weight = Weights.of(stacks);
        if (CargoSize.smallestFor(count, weight).isEmpty()) {
            return Optional.of(Component.translatable("contract.aerocouriers.error.cargo_too_big", count,
                    String.format(Locale.ROOT, "%.1f", weight)));
        }
        return Optional.empty();
    }

    public static Optional<ContractManager.Outcome> rejectContract(List<ItemStack> stacks) {
        return validateMessage(stacks).map(msg -> new ContractManager.Outcome(false, msg, Optional.empty()));
    }
}
