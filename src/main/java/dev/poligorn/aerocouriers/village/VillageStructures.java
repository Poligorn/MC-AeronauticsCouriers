package dev.poligorn.aerocouriers.village;

import com.mojang.datafixers.util.Pair;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Adds the post station to the village house pools (GDD §6.0). Only appends an element to pools already loaded
 * from datapacks, so vanilla and other mods' pool JSON stay untouched. Affects villages generated from now on.
 *
 * <p>The 3×3×3 piece always fits, so jigsaw uses it as a fallback when larger houses fail — a village can get
 * several station houses. {@link OncePerVillageStationProcessor} keeps the first controller
 * {@code post_station} (the 1×1×2 stand) and turns extra lowers into cartography tables (their upper
 * halves become air) while the piece is placed.
 */
public final class VillageStructures {
    public static final ResourceLocation STRUCTURE = AeronauticsCouriers.id("village/post_station");
    public static final ResourceKey<StructureProcessorList> PROCESSOR_LIST = ResourceKey.create(
            Registries.PROCESSOR_LIST, AeronauticsCouriers.id("once_per_village_station"));
    private static final int WEIGHT = 4;
    private static final List<String> VILLAGES = List.of("plains", "desert", "savanna", "snowy", "taiga");

    private VillageStructures() {
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        if (!CouriersServerConfig.SPEC.isLoaded() || !CouriersServerConfig.STATIONS_IN_VILLAGES.get()) {
            return;
        }
        RegistryAccess access = event.getServer().registryAccess();
        Registry<StructureTemplatePool> pools = access.registryOrThrow(Registries.TEMPLATE_POOL);
        StructurePoolElement element = stationPoolElement(access);
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

    /** Same pool element worldgen injects: rigid 3×3 house plus the once-per-village processor. */
    public static StructurePoolElement stationPoolElement() {
        return stationPoolElement(null);
    }

    public static StructurePoolElement stationPoolElement(@Nullable RegistryAccess access) {
        return StructurePoolElement.single(STRUCTURE.toString(), processorList(access))
                .apply(StructureTemplatePool.Projection.RIGID);
    }

    static Holder<StructureProcessorList> processorList(@Nullable RegistryAccess access) {
        if (access != null) {
            var held = access.registry(Registries.PROCESSOR_LIST).flatMap(r -> r.getHolder(PROCESSOR_LIST));
            if (held.isPresent()) {
                return held.get();
            }
            AeronauticsCouriers.LOGGER.warn("[station] Processor list {} missing; using an inline holder",
                    PROCESSOR_LIST.location());
        }
        return Holder.direct(new StructureProcessorList(List.of(OncePerVillageStationProcessor.INSTANCE)));
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
