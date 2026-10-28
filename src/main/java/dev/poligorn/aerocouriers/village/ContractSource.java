package dev.poligorn.aerocouriers.village;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * A generator of NPC contracts. MC-Ikaria is a sky-island world with almost no villages (CLARIFICATIONS), so
 * villages are only the first implementation; "post stations" placed by admins or structures will be another.
 * Nothing in the contract system may depend on villages directly.
 */
public interface ContractSource {
    /** Stable id, e.g. {@code aerocouriers:villages} or {@code aerocouriers:post_stations}. */
    ResourceLocation id();

    /** Called every {@code village_generation_interval_min}; may publish new contracts to the board. */
    void generate(ServerLevel level);
}
