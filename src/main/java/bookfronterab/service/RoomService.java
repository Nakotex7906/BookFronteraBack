package bookfronterab.service;

import bookfronterab.dto.OpeningHoursDtos;
import bookfronterab.model.OpeningHour;
import bookfronterab.model.Room;
import bookfronterab.repo.OpeningHourRepository;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepo;
    private final OpeningHourRepository openingRepo;

    public List<Room> list() { return roomRepo.findAll(); }
    public Room get(Long id) { return roomRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Sala no encontrada")); }

    @Transactional
    public void upsertOpeningHours(Long roomId, OpeningHoursDtos.UpsertRequest req) {
        Room room = get(roomId);
        // Reemplazo simple de los 7 días definidos en req
        for (OpeningHoursDtos.DayRange d : req.week()) {
            OpeningHour oh = openingRepo.findByRoomAndWeekday(room, d.weekday())
                    .orElse(OpeningHour.builder().room(room).weekday(d.weekday()).build());
            oh.setOpenTime(d.open());
            oh.setCloseTime(d.close());
            openingRepo.save(oh);
        }
    }

    public List<OpeningHour> findOpeningHours(Long roomId) {
        return openingRepo.findByRoom(get(roomId));
    }

}
