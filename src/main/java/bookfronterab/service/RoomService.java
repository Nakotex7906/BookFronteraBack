package bookfronterab.service;

import bookfronterab.dto.RoomDto;
import bookfronterab.model.Room;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <-- 1. IMPORTA ESTO

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepo;

    /**
     * Obtiene todas las salas y las convierte a DTOs.
     */
    // <-- 2. AÑADE ESTA LÍNEA
    @Transactional(readOnly = true) 
    public List<RoomDto> getAllRooms() {
        return roomRepo.findAll()
                .stream()
                .map(this::mapToDto) // Ahora la sesión sigue abierta aquí
                .collect(Collectors.toList());
    } // <-- La sesión se cierra aquí (después del mapeo)

    /**
     * mapea la entidad Room al RoomDto.
     */
    private RoomDto mapToDto(Room room) {
        return RoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .capacity(room.getCapacity())
                .equipment(room.getEquipment()) // <-- Esto ya no fallará
                .floor(room.getFloor())
                .build();
    }
}