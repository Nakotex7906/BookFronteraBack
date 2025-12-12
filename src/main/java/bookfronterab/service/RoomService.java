package bookfronterab.service;

import bookfronterab.exception.ResourceNotFoundException;
import bookfronterab.dto.RoomDto;
import bookfronterab.model.Room;
import bookfronterab.repo.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // <-- 1. IMPORTA ESTO
import org.springframework.web.multipart.MultipartFile;
import bookfronterab.exception.ImageUploadException;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.service.google.GoogleCalendarService;
import bookfronterab.service.google.GoogleCredentialsService;
import com.google.api.client.auth.oauth2.Credential;
import bookfronterab.model.Reservation;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService {

    private final RoomRepository roomRepo;
    private final CloudinaryService cloudinaryService;
    private final ReservationRepository reservationRepo;
    private final GoogleCalendarService googleCalendarService;
    private final GoogleCredentialsService googleCredentialsService;

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

    /**
     * Elimina una sala por su ID.
     * Realiza un borrado en cascada: primero elimina las reservas asociadas
     * y luego la sala para evitar problemas de Foreign Key.
     *
     * @param roomId El ID de la sala a eliminar.
     */
    @Transactional
    public void deleteRoom(Long roomId) {
        Room room = roomRepo.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Sala no encontrada con id " + roomId));

        // Buscar reservas antes de borrarlas
        List<Reservation> reservations = reservationRepo.findByRoomIdOrderByStartAtAsc(roomId);

        for (Reservation res : reservations) {
            // Si la reserva tiene un evento de Google asociado
            if (res.getGoogleEventId() != null) {
                try {
                    // Obtenemos las credenciales del usuario dueño de la reserva
                    Credential credential = googleCredentialsService.getCredential(res.getUser());

                    // Borramos el evento en Google Calendar
                    googleCalendarService.deleteEvent(res.getGoogleEventId(), credential.getAccessToken());

                } catch (Exception e) {
                    // Logueamos pero no detenemos el proceso. La prioridad es borrar la sala.
                    log.warn("Error al borrar evento Google para reserva {}: {}", res.getId(), e.getMessage());
                }
            }
        }

        // borrar las reservas de la base de datos
        reservationRepo.deleteAll(reservations);

        //  Borrar imagen de Cloudinary
        if (room.getImageUrl() != null && !room.getImageUrl().isBlank()) {
            try {
                String publicId = extractPublicIdFromUrl(room.getImageUrl());
                if (publicId != null) {
                    cloudinaryService.deleteFile(publicId);
                }
            } catch (Exception e) {
                log.warn("No se pudo borrar la imagen de Cloudinary para la sala {}: {}", roomId, e.getMessage());
            }
        }

        roomRepo.delete(room);
    }

    private String extractPublicIdFromUrl(String url) {
        try {
            int uploadIndex = url.indexOf("/upload/");
            if (uploadIndex == -1) return null;

            String path = url.substring(uploadIndex + 8);

            if (path.matches("^v\\d+/.*")) {
                int slashIndex = path.indexOf('/');
                if (slashIndex != -1) {
                    path = path.substring(slashIndex + 1);
                }
            }

            int dotIndex = path.lastIndexOf('.');
            if (dotIndex != -1) {
                path = path.substring(0, dotIndex);
            }

            return path;
        } catch (Exception e) {
            return null;
        }
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