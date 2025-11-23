package bookfronterab.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * DTO para encapsular la respuesta de disponibilidad diaria.
 * Contenedor de clases estáticas para la estructura de respuesta JSON.
 */
public class AvailabilityDto {

    // Añadimos un constructor privado para "ocultar" el público implícito.
    // Esto previene que alguien instancie 'new AvailabilityDto()' por error.
    private AvailabilityDto() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * DTO para la respuesta JSON principal.
     */
    @Getter
    @AllArgsConstructor
    public static class DailyAvailabilityResponse {
        private List<RoomDto> rooms;
        private List<TimeSlotDto> slots;
        private List<AvailabilityMatrixItemDto> availability;
    }

    @Getter
    @AllArgsConstructor
    public static class TimeSlotDto {
        private String id;
        private String label;
        private String start;
        private String end;
    }

    @Getter
    @AllArgsConstructor
    public static class AvailabilityMatrixItemDto {
        private String roomId;
        private String slotId;
        private boolean available;
    }
}