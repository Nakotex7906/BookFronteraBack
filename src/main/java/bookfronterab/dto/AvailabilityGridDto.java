package bookfronterab.dto;

import bookfronterab.model.Room;

import java.util.List;

public record AvailabilityGridDto(
        List<RoomDto> rooms,
        List<TimeSlotDto> slots,
        List<AvailabilityStatusDto> availability
) {
}
