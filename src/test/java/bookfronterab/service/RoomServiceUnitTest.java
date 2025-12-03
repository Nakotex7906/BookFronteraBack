package bookfronterab.service;

import bookfronterab.dto.RoomDto;
import bookfronterab.exception.ResourceNotFoundException;
import bookfronterab.model.Room;
import bookfronterab.repo.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoomServiceUnitTest {

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private RoomService roomService;

    // ================================================================
    // TESTS PARA: getAllRooms()
    // ================================================================

    @Test
    @DisplayName("Unitario: getAllRooms retorna lista de DTOs")
    void getAllRooms_ShouldReturnListOfDtos() {
        // Arrange
        Room room = Room.builder().id(1L).name("Sala A").build();
        when(roomRepo.findAll()).thenReturn(List.of(room));

        // Act
        List<RoomDto> result = roomService.getAllRooms();

        // Assert
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("Sala A", result.get(0).getName());
        verify(roomRepo).findAll();
    }
    
    @Test
    @DisplayName("Unitario: getAllRooms retorna lista vacía si no hay salas")
    void getAllRooms_ShouldReturnEmptyList() {
        when(roomRepo.findAll()).thenReturn(Collections.emptyList());

        List<RoomDto> result = roomService.getAllRooms();

        assertTrue(result.isEmpty());
    }

    // ================================================================
    // TESTS PARA: createRoom()
    // ================================================================

    @Test
    @DisplayName("Unitario: createRoom guarda URL si Cloudinary responde ok")
    void createRoom_ShouldSaveUrl() throws IOException {
        RoomDto dto = RoomDto.builder().name("Sala Test").capacity(5).floor(1).build();
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false); // Simulamos que el archivo TIENE contenido
        when(cloudinaryService.uploadFile(file)).thenReturn("http://url-falsa.com/img.jpg");

        when(roomRepo.save(any(Room.class))).thenAnswer(invocation -> {
            Room r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });

        RoomDto result = roomService.createRoom(dto, file);

        assertNotNull(result.getId());
        assertEquals("http://url-falsa.com/img.jpg", result.getImageUrl());
        verify(roomRepo).save(any(Room.class));
    }

    @Test
    @DisplayName("Unitario: createRoom guarda sin URL si el archivo es nulo o vacío")
    void createRoom_ShouldSaveWithoutUrl_WhenFileIsNull() throws IOException {
        RoomDto dto = RoomDto.builder().name("Sala Sin Foto").build();
        
        // Simulamos el guardado
        when(roomRepo.save(any(Room.class))).thenAnswer(i -> {
            Room r = i.getArgument(0);
            r.setId(2L);
            return r;
        });

        // Pasamos null como archivo
        RoomDto result = roomService.createRoom(dto, null);

        assertNotNull(result.getId());
        assertNull(result.getImageUrl());
        verify(cloudinaryService, never()).uploadFile(any()); // Cloudinary no debió llamarse
    }

    @Test
    @DisplayName("Unitario: createRoom lanza excepción RuntimeException si Cloudinary falla")
    void createRoom_ShouldThrowException_WhenCloudinaryFails() throws IOException {
        RoomDto dto = RoomDto.builder().name("Sala Error").build();
        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenThrow(new IOException("Error de red simulado"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                roomService.createRoom(dto, file)
        );

        assertTrue(ex.getMessage().contains("Error al subir imagen"));
        verify(roomRepo, never()).save(any());
    }

    // ================================================================
    // TESTS PARA: delateRoom()
    // ================================================================

    @Test
    @DisplayName("Unitario: delateRoom elimina por ID")
    void delateRoom_ShouldCallDeleteById() {
        Long roomId = 5L;
        
        // Act
        roomService.delateRoom(roomId);

        // Assert
        verify(roomRepo).deleteById(roomId);
    }

    // ================================================================
    // TESTS PARA: patchRoom()
    // ================================================================

    @Test
    @DisplayName("Unitario: patchRoom actualiza todos los campos si no son nulos")
    void patchRoom_ShouldUpdateAllFields_WhenNotNull() throws IOException {
        // Sala existente
        Room existing = Room.builder().id(1L).name("Viejo").capacity(5).floor(1).equipment(List.of("TV")).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        // DTO con cambios
        RoomDto patchDto = RoomDto.builder()
                .name("Nuevo Nombre")
                .floor(2)         // != 0
                .capacity(20)     // != 0
                .equipment(List.of("Proyector")) // != null
                .build();

        // Archivo nuevo
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenReturn("http://new-image.com");

        when(roomRepo.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        RoomDto result = roomService.patchRoom(1L, patchDto, file);

        // Assert
        assertEquals("Nuevo Nombre", result.getName());
        assertEquals(2, result.getFloor());
        assertEquals(20, result.getCapacity());
        assertEquals("Proyector", result.getEquipment().get(0));
        assertEquals("http://new-image.com", result.getImageUrl());
    }

    @Test
    @DisplayName("Unitario: patchRoom NO actualiza campos si vienen vacíos/nulos")
    void patchRoom_ShouldNotUpdate_WhenFieldsAreNullOrZero() {
        // Sala existente
        Room existing = Room.builder().id(1L).name("Original").capacity(10).floor(1).equipment(List.of("TV")).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        // DTO vacío (name=null, capacity=0, floor=0, equipment=null)
        RoomDto patchDto = RoomDto.builder().build();

        when(roomRepo.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        // Act (sin archivo)
        RoomDto result = roomService.patchRoom(1L, patchDto, null);

        // Assert (Deben mantenerse los valores originales)
        assertEquals("Original", result.getName());
        assertEquals(10, result.getCapacity());
        assertEquals(1, result.getFloor());
        assertEquals("TV", result.getEquipment().get(0));
    }

    @Test
    @DisplayName("Unitario: patchRoom lanza excepción si falla la subida de imagen")
    void patchRoom_ShouldThrowException_WhenImageUploadFails() throws IOException {
        Room existing = Room.builder().id(1L).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        RoomDto patchDto = RoomDto.builder().build();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenThrow(new IOException("Fallo Cloudinary"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> 
            roomService.patchRoom(1L, patchDto, file)
        );

        assertTrue(ex.getMessage().contains("Error al actualizar imagen"));
    }

    @Test
    @DisplayName("Unitario: patchRoom lanza error 404 si ID no existe")
    void patchRoom_ShouldThrow404_IfNotFound() {
        when(roomRepo.findById(99L)).thenReturn(Optional.empty());

        RoomDto patchDto = RoomDto.builder().name("X").build();

        assertThrows(ResourceNotFoundException.class, () ->
                roomService.patchRoom(99L, patchDto, null)
        );
    }

    // ================================================================
    // TESTS PARA: putRoom()
    // ================================================================

    @Test
    @DisplayName("Unitario: putRoom reemplaza todos los campos")
    void putRoom_ShouldReplaceAllFields() {
        Room existing = Room.builder().id(1L).name("Viejo").capacity(5).floor(1).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        RoomDto putDto = RoomDto.builder()
                .name("Reemplazo")
                .capacity(50)
                .floor(3)
                .equipment(List.of("Silla"))
                .build();

        when(roomRepo.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        RoomDto result = roomService.putRoom(1L, putDto);

        assertEquals("Reemplazo", result.getName());
        assertEquals(50, result.getCapacity());
        assertEquals(3, result.getFloor());
        assertEquals("Silla", result.getEquipment().get(0));
    }

    @Test
    @DisplayName("Unitario: putRoom lanza 404 si ID no existe")
    void putRoom_ShouldThrow404_IfNotFound() {
        when(roomRepo.findById(99L)).thenReturn(Optional.empty());
        RoomDto dto = RoomDto.builder().build();

        assertThrows(ResourceNotFoundException.class, () -> 
            roomService.putRoom(99L, dto)
        );
    }
}