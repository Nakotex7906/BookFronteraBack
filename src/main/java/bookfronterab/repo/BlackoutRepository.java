package bookfronterab.repo;

import bookfronterab.model.Blackout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface BlackoutRepository extends JpaRepository<Blackout, Long> {
    List<Blackout> findByRoomIdAndEndAtAfterAndStartAtBefore(Long roomId, OffsetDateTime from, OffsetDateTime to);
}
