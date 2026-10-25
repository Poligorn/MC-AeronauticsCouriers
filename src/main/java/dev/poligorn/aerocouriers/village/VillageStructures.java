package dev.poligorn.aerocouriers.village;

import com.mojang.datafixers.util.Pair;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * Adds the post station to the village house pools (GDD §6.0). Only appends an element to pools already loaded
 * from datapacks, so vanilla and other mods' pool JSON stay untouched. Affects villages generated from now on.
 *
 * <p>Village house pools in 1.21.1 weigh 68–87 in total; with {@link #WEIGHT} a house slot becomes a station
 * with ≈5% chance, so a typical village gets zero to two stations.
 */
public final class VillageStructures {
    public static final ResourceLocation STRUCTURE = AeronauticsCouriers.id("village/post_station");
    private static final int WEIGHT = 4;
    private static final List<String> VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");

    private VillageStructures() {
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!CouriersServerConfig.SPEC.isLoaded() || !CouriersServerConfig.STATIONS_IN_VILLAGES.get()) {
            return;
        }
        Registry<StructureTemplatePool> pools = event.getServer().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        StructurePoolElement element = StructurePoolElement.single(STRUCTURE.toString())
                .apply(StructureTemplatePool.Projection.RIGID);
        int added = 0;
        for (String village : VILLAGES) {
            ResourceLocation id = ResourceLocation.withDefaultNamespace("village/" + village + "/houses");
            StructureTemplatePool pool = pools.get(id);
            if (pool == null) {
                AeronauticsCouriers.LOGGER.warn("[station] Village pool {} not found", id);
                continue;
            }
            try {
                if (append(pool, element)) {
                    added++;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                AeronauticsCouriers.LOGGER.error("[station] Could not add the post station to {}", id, e);
            }
        }
        AeronauticsCouriers.LOGGER.info("[station] Post station added to {} village house pools (weight {})", added, WEIGHT);
    }

    @SuppressWarnings("unchecked")
    private static boolean append(StructureTemplatePool pool, StructurePoolElement element) throws ReflectiveOperationException {
        Field templatesField = StructureTemplatePool.class.getDeclaredField("templates");
        Field rawField = StructureTemplatePool.class.getDeclaredField("rawTemplates");
        templatesField.setAccessible(true);
        rawField.setAccessible(true);

        ObjectArrayList<StructurePoolElement> templates = (ObjectArrayList<StructurePoolElement>) templatesField.get(pool);
        if (templates.stream().anyMatch(e -> e.toString().equals(element.toString()))) {
            return false;
        }
        for (int i = 0; i < WEIGHT; i++) {
            templates.add(element);
        }
        List<Pair<StructurePoolElement, Integer>> raw = new ArrayList<>(
                (List<Pair<StructurePoolElement, Integer>>) rawField.get(pool));
        raw.add(Pair.of(element, WEIGHT));
        rawField.set(pool, raw);
        return true;
    }
}
