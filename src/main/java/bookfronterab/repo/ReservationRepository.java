package bookfronterab.repo;

import bookfronterab.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Repositorio para acceder a los datos de las entidades {@link Reservation}.
 * Proporciona métodos CRUD y consultas personalizadas.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * Busca reservas que se solapen con un intervalo de tiempo específico para una sala determinada.
     * <p>
     * Una reserva conflictiva es aquella que cumple:
     * (Su hora de inicio es anterior a la nueva hora de fin) Y (Su hora de fin es posterior a la nueva hora de inicio).
     * <p>
     * Lógica de solapamiento:
     * (existing.startAt < new.endAt) AND (existing.endAt > new.startAt)
     *
     * @param roomId El ID de la sala a comprobar.
     * @param newStartAt La hora de inicio del nuevo intervalo de reserva (excluyente para el fin).
     * @param newEndAt La hora de fin del nuevo intervalo de reserva (excluyente para el inicio).
     * @return Una lista de reservas que entran en conflicto con el horario solicitado.
     */
    @Query("SELECT r FROM Reservation r WHERE r.room.id = :roomId AND r.startAt < :newEndAt AND r.endAt > :newStartAt")
    List<Reservation> findConflictingReservations(
            @Param("roomId") Long roomId,
            @Param("newStartAt") ZonedDateTime newStartAt,
            @Param("newEndAt") ZonedDateTime newEndAt
    );

}