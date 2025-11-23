package bookfronterab.repo;

import bookfronterab.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RoomRepository extends JpaRepository<Room, Long> {

    /**
     * Busca una sala por su ID y aplica un bloqueo pesimista (PESSIMISTIC_WRITE).
     * Esto asegura que cualquier otra transacción que intente modificar esta
     * fila de sala deba esperar a que la transacción actual (la que posee el bloqueo) termine.
     * Es crucial para prevenir "race conditions" al crear reservas para la misma sala.
     *
     * @param id El ID de la sala a buscar y bloquear.
     * @return Un Optional que contiene la Room si se encuentra.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdWithLock(@Param("id") Long id);


}
