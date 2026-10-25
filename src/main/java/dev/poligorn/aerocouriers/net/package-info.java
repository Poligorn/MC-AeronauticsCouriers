/**
 * Network payloads (GDD §14.1), registered in {@link dev.poligorn.aerocouriers.registry.ModPayloads}.
 * C2S (intents only): {@link dev.poligorn.aerocouriers.net.OpenBoardPayload},
 * {@link dev.poligorn.aerocouriers.net.ContractIntentPayload},
 * {@link dev.poligorn.aerocouriers.net.CreateContractPayload},
 * {@link dev.poligorn.aerocouriers.net.ClaimMailPayload},
 * {@link dev.poligorn.aerocouriers.net.OperatorBoardPayload}.
 * S2C: {@link dev.poligorn.aerocouriers.net.BoardSnapshotPayload} (pages of 20),
 * {@link dev.poligorn.aerocouriers.net.ZoneSyncPayload}.
 */
package dev.poligorn.aerocouriers.net;
