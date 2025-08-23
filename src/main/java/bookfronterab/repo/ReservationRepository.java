package bookfronterab.repo;

import bookfronterab.model.Reservation;
import bookfronterab.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("select r from Reservation r where r.room.id = :roomId and r.estado = :estado and r.startAt < :end and r.endAt > :start")
    List<Reservation> overlaps(@Param("roomId") Long roomId,
                               @Param("start") OffsetDateTime start,
                               @Param("end") OffsetDateTime end,
                               @Param("estado") ReservationStatus estado);

    List<Reservation> findByUserIdAndEndAtAfterOrderByStartAtAsc(Long userId, OffsetDateTime now);
    List<Reservation> findByRoomIdAndStartAtGreaterThanEqualAndEndAtLessThanEqual(Long roomId, OffsetDateTime from, OffsetDateTime to);

}
