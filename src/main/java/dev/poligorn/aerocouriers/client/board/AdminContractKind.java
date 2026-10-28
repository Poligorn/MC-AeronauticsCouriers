package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.contract.ContractType;

/** Admin «Заказы» type filter (NPC vs player). */
public enum AdminContractKind {
    ALL,
    NPC,
    P2P;

    public boolean matches(ContractType type) {
        return switch (this) {
            case ALL -> true;
            case NPC -> type.isNpc();
            case P2P -> !type.isNpc();
        };
    }
}
