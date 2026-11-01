package dev.poligorn.aerocouriers.village;

import com.mojang.datafixers.util.Pair;
import dev.poligorn.aerocouriers.AeronauticsCouriers;
import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Guarantees one post station in every newly generated village (GDD §6.0). Vanilla and other mods' pool
 * JSON stay untouched: at {@link ServerAboutToStartEvent} we attach
 * {@link VillageTownCenterStationProcessor} to already-loaded {@code village/<type>/town_centers} elements.
 * The 1x1x2 stand is written inside the current chunk clip, next to the bell when possible.
 *
 * <p>The 3x3 house piece and {@link OncePerVillageStationProcessor} stay for tests and leftover copies.
 * House pools are no longer injected: the tiny fallback stole house slots and still left some villages
 * with no station. {@code generate_in_villages=false} skips the town-center wrap. Already generated
 * villages are not rewritten.
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
        int wrapped = 0;
        for (String village : VILLAGES) {
            ResourceLocation id = ResourceLocation.withDefaultNamespace("village/" + village + "/town_centers");
            StructureTemplatePool pool = pools.get(id);
            if (pool == null) {
                AeronauticsCouriers.LOGGER.warn("[station] Village town-center pool {} not found", id);
                continue;
            }
            try {
                if (attachPlanter(pool)) {
                    wrapped++;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                AeronauticsCouriers.LOGGER.error("[station] Could not plant a post station in {}", id, e);
            }
        }
        AeronauticsCouriers.LOGGER.info("[station] Post station planter added to {} village town-center pools", wrapped);
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
    private static boolean attachPlanter(StructureTemplatePool pool) throws ReflectiveOperationException {
        Field rawField = StructureTemplatePool.class.getDeclaredField("rawTemplates");
        rawField.setAccessible(true);
        List<Pair<StructurePoolElement, Integer>> raw =
                (List<Pair<StructurePoolElement, Integer>>) rawField.get(pool);
        boolean changed = false;
        for (Pair<StructurePoolElement, Integer> pair : raw) {
            if (attachPlanter(pair.getFirst())) {
                changed = true;
            }
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static boolean attachPlanter(StructurePoolElement element) throws ReflectiveOperationException {
        boolean changed = false;
        Class<?> type = element.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                // StructurePoolElement.EMPTY is a static Holder<StructureProcessorList>; writing it
                // throws and used to abort the whole town_centers wrap.
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                Object value;
                try {
                    value = field.get(element);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (value instanceof Holder<?> holder && holder.value() instanceof StructureProcessorList list) {
                    if (list.list().stream().anyMatch(p -> p instanceof VillageTownCenterStationProcessor)) {
                        continue;
                    }
                    List<StructureProcessor> combined = new ArrayList<>(list.list());
                    combined.add(VillageTownCenterStationProcessor.INSTANCE);
                    try {
                        field.set(element, Holder.direct(new StructureProcessorList(combined)));
                        changed = true;
                    } catch (IllegalAccessException e) {
                        AeronauticsCouriers.LOGGER.warn(
                                "[station] Could not wrap processors on {}", type.getSimpleName());
                    }
                    continue;
                }
                if (value instanceof List<?> children && !children.isEmpty()
                        && children.getFirst() instanceof StructurePoolElement) {
                    for (Object child : children) {
                        if (child instanceof StructurePoolElement nested && attachPlanter(nested)) {
                            changed = true;
                        }
                    }
                }
            }
            type = type.getSuperclass();
        }
        return changed;
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
