package bookfronterab.dto;

import java.time.ZonedDateTime;
import java.util.List;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
    public record CreateOnBehalfRequest(
       Long roomId,
       ZonedDateTime startAt,
       ZonedDateTime endAt,
       @Email(message = "Debe ser un correo válido")
       @Size(max = 100, message = "El correo es demasiado largo")
       @Pattern(regexp = "^[A-Za-z0-9._%+-]+@ufromail\\.cl$", message = "Solo se permiten correos @ufromail.cl")
       String othersEmail,
       boolean addToGoogleCalendar
    ){}

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

    /**
     * DTO para la respuesta de la página "Mis Reservas".
     * Agrupa las reservas del usuario en categorías.
     */
    public record MyReservationsResponse(
            Detail current, // Puede ser null
            List<Detail> future,
            List<Detail> past
    ) {}

}