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

// Habilita la integración de Mockito para manejar la creación de mocks.
@ExtendWith(MockitoExtension.class)
class RoomServiceUnitTest {

    // Mock del repositorio para simular la interacción con la base de datos.
    @Mock
    private RoomRepository roomRepo;

    // Mock del servicio Cloudinary para simular la subida de imágenes.
    @Mock
    private CloudinaryService cloudinaryService;

    // Inyecta los mocks anteriores en la instancia real de RoomService (el SUT - System Under Test).
    @InjectMocks
    private RoomService roomService;

    // ================================================================
    // TESTS PARA: getAllRooms() - Obtener todas las salas
    // ================================================================

    /**
     * Prueba si getAllRooms() retorna una lista de DTOs cuando hay salas en DB.
     */
    @Test
    @DisplayName("Unitario: getAllRooms retorna lista de DTOs")
    void getAllRooms_ShouldReturnListOfDtos() {
        // Arrange (Configuración): Crea una sala mock para ser retornada por el repositorio.
        Room room = Room.builder().id(1L).name("Sala A").build();
        // Simula que el repositorio devuelve la lista con la sala mock.
        when(roomRepo.findAll()).thenReturn(List.of(room));

        // Act (Ejecución): Llama al método a probar.
        List<RoomDto> result = roomService.getAllRooms();

        // Assert (Aserción): Verifica el resultado.
        assertFalse(result.isEmpty(), "La lista no debería estar vacía.");
        assertEquals(1, result.size());
        assertEquals("Sala A", result.get(0).getName());
        // Verifica que el método del repositorio fue llamado exactamente una vez.
        verify(roomRepo).findAll();
    }
    
    /**
     * Prueba si getAllRooms() retorna una lista vacía si no hay registros.
     */
    @Test
    @DisplayName("Unitario: getAllRooms retorna lista vacía si no hay salas")
    void getAllRooms_ShouldReturnEmptyList() {
        // Simula que el repositorio devuelve una lista vacía.
        when(roomRepo.findAll()).thenReturn(Collections.emptyList());

        List<RoomDto> result = roomService.getAllRooms();

        assertTrue(result.isEmpty(), "La lista debe estar vacía.");
    }

    // ================================================================
    // TESTS PARA: createRoom() - Crear una sala
    // ================================================================

    /**
     * Prueba que createRoom() guarda la URL de la imagen si Cloudinary responde con éxito.
     */
    @Test
    @DisplayName("Unitario: createRoom guarda URL si Cloudinary responde ok")
    void createRoom_ShouldSaveUrl() throws IOException {
        // Arrange
        RoomDto dto = RoomDto.builder().name("Sala Test").capacity(5).floor(1).build();
        MultipartFile file = mock(MultipartFile.class);

        // Simulamos que el archivo TIENE contenido y la subida es exitosa.
        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenReturn("http://url-falsa.com/img.jpg");

        // Simula el guardado en DB, asegurando que se asigne un ID al objeto retornado.
        when(roomRepo.save(any(Room.class))).thenAnswer(invocation -> {
            Room r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });

        // Act
        RoomDto result = roomService.createRoom(dto, file);

        // Assert
        assertNotNull(result.getId());
        assertEquals("http://url-falsa.com/img.jpg", result.getImageUrl());
        verify(roomRepo).save(any(Room.class));
        verify(cloudinaryService).uploadFile(file); // Verifica que se llamó a Cloudinary
    }

    /**
     * Prueba que createRoom() guarda la sala sin URL si el archivo es nulo.
     */
    @Test
    @DisplayName("Unitario: createRoom guarda sin URL si el archivo es nulo o vacío")
    void createRoom_ShouldSaveWithoutUrl_WhenFileIsNull() throws IOException {
        // Arrange
        RoomDto dto = RoomDto.builder().name("Sala Sin Foto").build();
        
        // Simula el guardado y la asignación de ID
        when(roomRepo.save(any(Room.class))).thenAnswer(i -> {
            Room r = i.getArgument(0);
            r.setId(2L);
            return r;
        });

        // Act: Pasamos null como archivo
        RoomDto result = roomService.createRoom(dto, null);

        // Assert
        assertNotNull(result.getId());
        assertNull(result.getImageUrl());
        // Verifica que Cloudinary nunca fue llamado, ya que el archivo era nulo.
        verify(cloudinaryService, never()).uploadFile(any());
    }

    /**
     * Prueba que createRoom() lanza una RuntimeException si Cloudinary falla.
     */
    @Test
    @DisplayName("Unitario: createRoom lanza excepción RuntimeException si Cloudinary falla")
    void createRoom_ShouldThrowException_WhenCloudinaryFails() throws IOException {
        // Arrange
        RoomDto dto = RoomDto.builder().name("Sala Error").build();
        MultipartFile file = mock(MultipartFile.class);

        // Simula que el archivo tiene contenido
        when(file.isEmpty()).thenReturn(false);
        // Simula que la subida lanza una excepción.
        when(cloudinaryService.uploadFile(file)).thenThrow(new IOException("Error de red simulado"));

        // Act & Assert: Verifica que se lance la excepción esperada.
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                roomService.createRoom(dto, file)
        );

        assertTrue(ex.getMessage().contains("Error al subir imagen"));
        // Verifica que el método de guardado en DB nunca fue llamado.
        verify(roomRepo, never()).save(any());
    }

    // ================================================================
    // TESTS PARA: delateRoom() - Eliminar una sala
    // ================================================================

    /**
     * Prueba que delateRoom() llama al método de eliminación por ID del repositorio.
     */
    @Test
    @DisplayName("Unitario: delateRoom elimina por ID")
    void delateRoom_ShouldCallDeleteById() {
        Long roomId = 5L;
        
        // Act
        roomService.deleteRoom(roomId);

        // Assert: Verifica que roomRepo.deleteById() fue llamado exactamente con el ID.
        verify(roomRepo).deleteById(roomId);
    }

    // ================================================================
    // TESTS PARA: patchRoom() - Actualización parcial (PATCH)
    // ================================================================

    /**
     * Prueba que patchRoom() actualiza todos los campos provistos y la imagen.
     */
    @Test
    @DisplayName("Unitario: patchRoom actualiza todos los campos si no son nulos")
    void patchRoom_ShouldUpdateAllFields_WhenNotNull() throws IOException {
        // Arrange: Sala existente
        Room existing = Room.builder().id(1L).name("Viejo").capacity(5).floor(1).equipment(List.of("TV")).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        // DTO con cambios (Ningún campo es nulo o cero)
        RoomDto patchDto = RoomDto.builder()
                .name("Nuevo Nombre")
                .floor(2)
                .capacity(20)
                .equipment(List.of("Proyector"))
                .build();

        // Archivo nuevo y simulación de subida
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenReturn("http://new-image.com");

        // Simula el guardado: retorna el objeto modificado.
        when(roomRepo.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        RoomDto result = roomService.patchRoom(1L, patchDto, file);

        // Assert: Se verifican los cambios
        assertEquals("Nuevo Nombre", result.getName());
        assertEquals(2, result.getFloor());
        assertEquals(20, result.getCapacity());
        assertEquals("Proyector", result.getEquipment().get(0));
        assertEquals("http://new-image.com", result.getImageUrl());
    }

    /**
     * Prueba que patchRoom() ignora los campos nulos o con valor 0 del DTO.
     */
    @Test
    @DisplayName("Unitario: patchRoom NO actualiza campos si vienen vacíos/nulos")
    void patchRoom_ShouldNotUpdate_WhenFieldsAreNullOrZero() {
        // Arrange: Sala existente con valores
        Room existing = Room.builder().id(1L).name("Original").capacity(10).floor(1).equipment(List.of("TV")).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        // DTO vacío (name=null, capacity=0, floor=0, equipment=null)
        RoomDto patchDto = RoomDto.builder().build();

        when(roomRepo.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        // Act (sin archivo)
        RoomDto result = roomService.patchRoom(1L, patchDto, null);

        // Assert: Los valores deben mantenerse como los originales
        assertEquals("Original", result.getName());
        assertEquals(10, result.getCapacity());
        assertEquals(1, result.getFloor());
        assertEquals("TV", result.getEquipment().get(0));
    }

    /**
     * Prueba que patchRoom() lanza una excepción si falla la subida de imagen.
     */
    @Test
    @DisplayName("Unitario: patchRoom lanza excepción si falla la subida de imagen")
    void patchRoom_ShouldThrowException_WhenImageUploadFails() throws IOException {
        // Arrange
        Room existing = Room.builder().id(1L).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        RoomDto patchDto = RoomDto.builder().build();
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenThrow(new IOException("Fallo Cloudinary"));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> 
            roomService.patchRoom(1L, patchDto, file)
        );

        assertTrue(ex.getMessage().contains("Error al actualizar imagen"));
    }

    /**
     * Prueba que patchRoom() lanza ResourceNotFoundException si la sala no existe.
     */
    @Test
    @DisplayName("Unitario: patchRoom lanza error 404 si ID no existe")
    void patchRoom_ShouldThrow404_IfNotFound() {
        // Simula que la búsqueda por ID no encuentra nada.
        when(roomRepo.findById(99L)).thenReturn(Optional.empty());

        RoomDto patchDto = RoomDto.builder().name("X").build();

        // Assert
        assertThrows(ResourceNotFoundException.class, () ->
                roomService.patchRoom(99L, patchDto, null)
        );
    }

    // ================================================================
    // TESTS PARA: putRoom() - Reemplazo completo (PUT)
    // ================================================================

    /**
     * Prueba que putRoom() reemplaza todos los campos de la sala existente.
     */
    @Test
    @DisplayName("Unitario: putRoom reemplaza todos los campos")
    void putRoom_ShouldReplaceAllFields() {
        // Arrange: Sala existente
        Room existing = Room.builder().id(1L).name("Viejo").capacity(5).floor(1).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        // DTO de reemplazo (debería sobrescribir)
        RoomDto putDto = RoomDto.builder()
                .name("Reemplazo")
                .capacity(50)
                .floor(3)
                .equipment(List.of("Silla"))
                .build();

        when(roomRepo.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        RoomDto result = roomService.putRoom(1L, putDto);

        // Assert: Se verifican los nuevos valores.
        assertEquals("Reemplazo", result.getName());
        assertEquals(50, result.getCapacity());
        assertEquals(3, result.getFloor());
        assertEquals("Silla", result.getEquipment().get(0));
    }

    /**
     * Prueba que putRoom() lanza ResourceNotFoundException si la sala no existe.
     */
    @Test
    @DisplayName("Unitario: putRoom lanza 404 si ID no existe")
    void putRoom_ShouldThrow404_IfNotFound() {
        // Simula que la búsqueda por ID no encuentra nada.
        when(roomRepo.findById(99L)).thenReturn(Optional.empty());
        RoomDto dto = RoomDto.builder().build();

        // Assert
        assertThrows(ResourceNotFoundException.class, () -> 
            roomService.putRoom(99L, dto)
        );
    }
}