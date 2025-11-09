package bookfronterab.dto;

import java.time.ZonedDateTime;

/**
 * Contenedor para los Data Transfer Objects (DTOs) relacionados con Reservation.
 */
public class ReservationDto {

    /**
     * DTO para la creación de una nueva reserva.
     * Recibido desde el frontend.
     *
     * @param roomId El ID de la sala a reservar.
     * @param startAt La fecha/hora de inicio.
     * @param endAt La fecha/hora de fin.
     * @param addToGoogleCalendar Flag para sincronizar con Google Calendar.
     */
    public record CreateRequest(
            Long roomId,
            ZonedDateTime startAt,
            ZonedDateTime endAt,
            boolean addToGoogleCalendar
    ) {}

    /**
     * DTO para enviar los detalles completos de una reserva al frontend.
     * Incluye información anidada de la sala y el usuario.
     *
     * @param id El ID de la reserva.
     * @param startAt La fecha/hora de inicio.
     * @param endAt La fecha/hora de fin.
     * @param room Los detalles de la sala reservada.
     * @param user Los detalles del usuario que reservó.
     */
    public record Detail(
            Long id,
            ZonedDateTime startAt,
            ZonedDateTime endAt,
            RoomDto room,
            UserDto user
    ) {}
}