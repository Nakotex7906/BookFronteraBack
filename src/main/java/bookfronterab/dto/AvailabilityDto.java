package bookfronterab.dto;

import java.util.List;

public record AvailabilityDto(Long roomId,
                              String date, // YYYY-MM-DD (zona America/Santiago)
                              String open,
                              String close,
                              int slotSizeMinutes,
                              List<Slot> booked,
                              List<Slot> free) {
    public record Slot(String start, String end) {}
}
