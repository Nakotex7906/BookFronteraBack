package bookfronterab.repo;

import bookfronterab.model.Room;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Integer> {
    Room findById(int idRoom);
}
