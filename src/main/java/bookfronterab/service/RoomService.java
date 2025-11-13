package bookfronterab.service;

import bookfronterab.exception.ResourceNotFoundException;
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

    public void delateRoom(Long roomId) {
        roomRepo.deleteById(roomId);
    }

    public RoomDto patchRoom(Long id, RoomDto roomDto) {
        Room existingRoom = roomRepo.findById(id).
                orElseThrow(() -> new ResourceNotFoundException("Sala no encontrada con el id " + id));

        if (roomDto.getName() != null) {
            existingRoom.setName(roomDto.getName());
        }
        if (roomDto.getFloor() != 0){
            existingRoom.setFloor(roomDto.getFloor());
        }
        if (roomDto.getCapacity() != 0){
            existingRoom.setCapacity(roomDto.getCapacity());
        }
        if (roomDto.getEquipment() != null) {
            existingRoom.setEquipment(roomDto.getEquipment());
        }
        Room updateRoom = roomRepo.save(existingRoom);
        return mapToDto(updateRoom);

    }

    public RoomDto putRoom(Long id, RoomDto roomDto) {
        Room existingRoom = roomRepo.findById(id).
                orElseThrow(() -> new ResourceNotFoundException("Sala no encontrada con el id " + id));

        existingRoom.setName(roomDto.getName());
        existingRoom.setCapacity(roomDto.getCapacity());
        existingRoom.setEquipment(roomDto.getEquipment());
        existingRoom.setFloor(roomDto.getFloor());

        Room updateRoom = roomRepo.save(existingRoom);
        return mapToDto(updateRoom);
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