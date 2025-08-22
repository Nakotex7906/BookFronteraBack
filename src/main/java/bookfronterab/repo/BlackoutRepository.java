package bookfronterab.repo;

import bookfronterab.model.Blackout;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlackoutRepository extends JpaRepository<Blackout, Long> {
}
