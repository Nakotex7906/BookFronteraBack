package bookfronterab.dto;

public record AvailabilityStatusDto(
        String roomId,
        String slotId,
        boolean available
) {
}
