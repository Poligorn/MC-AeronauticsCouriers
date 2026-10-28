package dev.poligorn.aerocouriers.client.board;

import dev.poligorn.aerocouriers.license.LicenseRank;
import dev.poligorn.aerocouriers.net.BoardSnapshotPayload;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** What the order overlays need from the board screen (data from the last server snapshot + intents). */
public interface BoardOverlayHost {
    Font font();

    Optional<BoardSnapshotPayload.BoardEntry> entry(UUID id);

    long balance();

    LicenseRank viewerRank();

    long collateralDue(BoardSnapshotPayload.BoardEntry entry);

    boolean traineeFree(BoardSnapshotPayload.BoardEntry entry);

    /** Whether «Принять» is offered at all (open order on the board that the viewer may take). */
    boolean offersAccept(BoardSnapshotPayload.BoardEntry entry);

    /** Licence/rank/limit reason the order cannot be taken (money is checked by the confirm window). */
    Optional<Component> acceptDenial(BoardSnapshotPayload.BoardEntry entry);

    boolean mapAvailable();

    void showOnMap(BoardSnapshotPayload.BoardEntry entry);

    void openCustomerProfile(BoardSnapshotPayload.BoardEntry entry);

    void openAcceptConfirm(BoardSnapshotPayload.BoardEntry entry);

    void closeDetails();

    void closeAcceptConfirm();

    void confirmAccept(BoardSnapshotPayload.BoardEntry entry);
}
