package dev.poligorn.aerocouriers.contract;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.Tags;

/**
 * Cargo weight (GDD §8.2). Only the GDD defaults for now; the {@code item_weights.json} datapack overrides
 * arrive together with the cargo tables.
 */
public final class Weights {
    public static final double BLOCK = 1.0;
    public static final double INGOT_OR_GEM = 0.11;
    public static final double TOOL_OR_ARMOR = 0.5;
    public static final double FLUID_BUCKET = 1.0;
    public static final double ITEM = 0.05;

    private Weights() {
    }

    public static double perItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            var override = WorldCargoOverrides.get(server)
                    .weightOverride(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (override.isPresent()) {
                return override.getAsDouble();
            }
        }
        double datapack = ItemWeights.datapackPerItem(stack);
        if (!Double.isNaN(datapack)) {
            return datapack;
        }
        if (stack.getItem() instanceof BucketItem bucket && bucket.content != Fluids.EMPTY) {
            return FLUID_BUCKET;
        }
        if (stack.getItem() instanceof BlockItem) {
            return BLOCK;
        }
        if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.GEMS)) {
            return INGOT_OR_GEM;
        }
        if (stack.isDamageableItem()) {
            return TOOL_OR_ARMOR;
        }
        return ITEM;
    }

    public static double of(List<ItemStack> manifest) {
        double total = 0;
        for (ItemStack stack : manifest) {
            total += perItem(stack) * stack.getCount();
        }
        return total;
    }

    /** Shulker boxes, bundles and other item containers are forbidden in cargo: weight could be hidden inside. */
    public static boolean isForbidden(ItemStack stack) {
        return !stack.canFitInsideContainerItems()
                || stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.BUNDLE_CONTENTS);
    }
}
