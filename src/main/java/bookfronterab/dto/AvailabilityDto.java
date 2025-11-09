package bookfronterab.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * DTO para encapsular la respuesta de disponibilidad diaria.
 * Este formato coincide con lo que el frontend (useAvailability.ts) espera,
 * enviando las salas, los bloques horarios y la matriz de disponibilidad
 * como tres listas separadas.
 */
public class AvailabilityDto {

    /**
     * DTO para la respuesta JSON principal.
     */
    @Getter
    @AllArgsConstructor
    public static class DailyAvailabilityResponse {
        /**
         * La lista de todas las salas disponibles.
         * Coincide con `setRooms` del frontend.
         */
        private List<RoomDto> rooms;

        /**
         * La lista de todos los bloques horarios del día.
         * Coincide con `setSlots` del frontend.
         */
        private List<TimeSlotDto> slots;

        /**
         * La "matriz" de disponibilidad.
         * Coincide con `setMatrix` (tipo Availability[]) del frontend.
         */
        private List<AvailabilityMatrixItemDto> availability;
    }

    /**
     * Representa un único bloque horario (ej. "09:00 - 10:00").
     * Coincide con el tipo `TimeSlot` del frontend.
     */
    @Getter
    @AllArgsConstructor
    public static class TimeSlotDto {
        /** El ID del slot, ej. "09:00-10:00" */
        private String id;
        /** La etiqueta a mostrar, ej. "09:00 - 10:00" */
        private String label;
        /** Hora de inicio (usada para ordenar) */
        private String start;
        /** Hora de fin */
        private String end;
    }

    /**
     * Representa una celda en la matriz (Room ID + Slot ID).
     * Coincide con el tipo `Availability` del frontend.
     */
    @Getter
    @AllArgsConstructor
    public static class AvailabilityMatrixItemDto {
        /** El ID de la sala (String, como espera el frontend) */
        private String roomId;
        /** El ID del bloque horario */
        private String slotId;
        /** true si está disponible, false si está ocupado */
        private boolean available;
    }
}