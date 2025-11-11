package bookfronterab.service;

import bookfronterab.dto.RoomDto;
import bookfronterab.model.Room;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepo;

    /**
     * Obtiene todas las salas y las convierte a DTOs.
     */
    public List<RoomDto> getAllRooms() {
        return roomRepo.findAll()
                .stream()
                .map(this::mapToDto) // Convierte cada Room a RoomDto
                .collect(Collectors.toList());
    }

    public RoomDto createRoom(RoomDto roomDto) {
        Room room = Room.builder()
                .name(roomDto.getName())
                .capacity(roomDto.getCapacity())
                .equipment(roomDto.getEquipment())
                .floor(roomDto.getFloor())
                .build();
        room = roomRepo.save(room);
        return mapToDto(room);
    }


    /**
     * mapea la entidad Room al RoomDto.
     */
    private RoomDto mapToDto(Room room) {
        return RoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .equipment(room.getEquipment())
                .floor(room.getFloor())
                .build();
    }
}