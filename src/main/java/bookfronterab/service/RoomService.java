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

}
