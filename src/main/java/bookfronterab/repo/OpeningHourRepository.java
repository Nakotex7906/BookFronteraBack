package bookfronterab.repo;

import bookfronterab.model.OpeningHour;
import bookfronterab.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

public interface OpeningHourRepository extends JpaRepository<OpeningHour, Long> {
    Optional<OpeningHour> findByRoomAndWeekday(Room room, DayOfWeek weekday);
    List<OpeningHour> findByRoom(Room room);
}
