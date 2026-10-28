/**
 * Contracts (GDD §5, §7, §9.5–9.6): state machine, SavedData stores (ContractManager, Escrow, Mailbox) and the
 * rules (RankRules, Pricing, Weights, XpCalculator). The server is the source of truth; cargo items live only in
 * Escrow, never in the container block.
 */
package dev.poligorn.aerocouriers.contract;
