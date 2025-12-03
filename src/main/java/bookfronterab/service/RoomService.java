package bookfronterab.service;

import bookfronterab.exception.ImageUploadException;
import bookfronterab.exception.ResourceNotFoundException;
import bookfronterab.dto.RoomDto;
import bookfronterab.model.Room;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <-- 1. IMPORTA ESTO
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepo;
    private final CloudinaryService cloudinaryService;

    /**
     * Obtiene todas las salas y las convierte a DTOs.
     */
    // <-- 2. AÑADE ESTA LÍNEA
    @Transactional(readOnly = true) 
    public List<RoomDto> getAllRooms() {
        return roomRepo.findAll()
                .stream()
                .map(this::mapToDto) // Ahora la sesión sigue abierta aquí
                .toList();
    } // <-- La sesión se cierra aquí (después del mapeo)

    public RoomDto createRoom(RoomDto roomDto, MultipartFile imageFile) {
        String imageUrl = null;

        // Subir imagen si existe
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                imageUrl = cloudinaryService.uploadFile(imageFile);
            } catch (Exception e) {
                throw new ImageUploadException("Error al subir imagen a Cloudinary", e);
            }
        }

        // 2. Crear entidad
        Room room = Room.builder()
                .name(roomDto.getName())
                .capacity(roomDto.getCapacity())
                .equipment(roomDto.getEquipment())
                .floor(roomDto.getFloor())
                .imageUrl(imageUrl)
                .build();

        room = roomRepo.save(room);
        return mapToDto(room); // Asegúrate que mapToDto incluya el imageUrl de vuelta
    }

    public void delateRoom(Long roomId) {
        roomRepo.deleteById(roomId);
    }

    public RoomDto patchRoom(Long id, RoomDto roomDto, MultipartFile imageFile) {
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
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                String newUrl = cloudinaryService.uploadFile(imageFile);
                existingRoom.setImageUrl(newUrl);
            } catch (Exception e) {
                throw new ImageUploadException("Error al actualizar imagen en Cloudinary", e);
            }
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
                .equipment(room.getEquipment()) // <-- Esto ya no fallará
                .floor(room.getFloor())
                .imageUrl(room.getImageUrl())
                .build();
    }
}