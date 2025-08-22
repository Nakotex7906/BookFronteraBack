package bookfronterab.dto;

import jakarta.validation.constraints.NotNull;
import bookfronterab.model.ReservationStatus;

import java.time.OffsetDateTime;

public class ReservationDtos {

    public record CreateRequest(@NotNull long roomId,
                                @NotNull OffsetDateTime startAt,
                                @NotNull OffsetDateTime endAt){}

    public record Response(
            Long id,
            Long roomId,
            Long userId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            ReservationStatus estado
    ) {}
}
