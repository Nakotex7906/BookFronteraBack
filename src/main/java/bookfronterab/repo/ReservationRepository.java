package bookfronterab.repo;

import bookfronterab.model.Reservation;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Integer> {
    List<Reservation> findByRoomId(int roomId);
    List<Reservation> findByRoomDate(ZonedDateTime date);
    List<Reservation> findByRoomIdAndDate(int roomId,ZonedDateTime date);
    Reservation findByRoomIdDateReservationCell(int roomId,ZonedDateTime date, int reservationCell);
}