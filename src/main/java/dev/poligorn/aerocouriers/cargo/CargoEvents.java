package dev.poligorn.aerocouriers.cargo;

import dev.poligorn.aerocouriers.config.CouriersServerConfig;
import dev.poligorn.aerocouriers.contract.ContractManager;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Cargo damage (GDD §9.7). Breaking runs at the lowest priority and ignores cancelled events, so claim mods that
 * cancel {@link BlockEvent.BreakEvent} protect containers on their land.
 */
public final class CargoEvents {
    private CargoEvents() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CargoEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, CargoEvents::onDetonate);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof CargoBlock cargo)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getPlayer();
        if (!CouriersServerConfig.PIRACY_ENABLED.get() && !player.getAbilities().instabuild) {
            event.setCanceled(true);
            return;
        }
        BlockPos controller = cargo.controllerPos(event.getPos(), event.getState());
        damage(level, controller, player);
        CargoStructure.dismantle(level, controller, cargo.size());
        event.setCanceled(true);
    }

    private static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player cause = event.getExplosion().getIndirectSourceEntity() instanceof Player p ? p : null;
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockState(pos).getBlock() instanceof CargoBlock cargo) {
                BlockPos controller = cargo.controllerPos(pos, level.getBlockState(pos));
                damage(level, controller, cause);
                CargoStructure.dismantle(level, controller, cargo.size());
            }
        }
    }

    private static void damage(ServerLevel level, BlockPos pos, @Nullable Player breaker) {
        if (!(level.getBlockEntity(pos) instanceof CargoContainerBlockEntity be) || be.isDebug()) {
            return;
        }
        UUID contractId = be.contractId();
        if (contractId == null) {
            return;
        }
        Vector3d world = SableBridge.worldCenter(level, pos);
        ContractManager.get(level.getServer()).damage(contractId,
                new ContractManager.DropSite(level, new Vec3(world.x, world.y, world.z), breaker));
    }
}
