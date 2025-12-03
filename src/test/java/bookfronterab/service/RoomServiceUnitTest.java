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

    @Test
    @DisplayName("Unitario: createRoom guarda URL si Cloudinary responde ok")
    void createRoom_ShouldSaveUrl() throws IOException {
        // Se preparan los datos de entrada (DTO y archivo mock)
        RoomDto dto = RoomDto.builder().name("Sala Test").capacity(5).floor(1).build();
        MultipartFile file = mock(MultipartFile.class);

        // Se define el comportamiento esperado de los mocks para un caso exitoso
        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenReturn("http://url-falsa.com/img.jpg");

        when(roomRepo.save(any(Room.class))).thenAnswer(invocation -> {
            Room r = invocation.getArgument(0);
            r.setId(1L);
            return r;
        });

        // Se ejecuta el método a probar
        RoomDto result = roomService.createRoom(dto, file);

        // Se verifica que el resultado contenga la URL retornada por Cloudinary
        assertNotNull(result.getId());
        assertEquals("http://url-falsa.com/img.jpg", result.getImageUrl());

        // Se confirma que se haya llamado al método save del repositorio
        verify(roomRepo).save(any(Room.class));
    }

    @Test
    @DisplayName("Unitario: createRoom lanza excepción RuntimeException si Cloudinary falla")
    void createRoom_ShouldThrowException_WhenCloudinaryFails() throws IOException {
        // Se configuran datos básicos para la prueba
        RoomDto dto = RoomDto.builder().name("Sala").build();
        MultipartFile file = mock(MultipartFile.class);

        // Se simula un error de conexión al intentar subir el archivo
        when(file.isEmpty()).thenReturn(false);
        when(cloudinaryService.uploadFile(file)).thenThrow(new IOException("Error de red simulado"));

        // Se verifica que el servicio capture la excepción y la relance adecuadamente
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                roomService.createRoom(dto, file)
        );

        assertTrue(ex.getMessage().contains("Error al subir imagen"));

        // Se asegura que no se haya intentado guardar nada en la base de datos ante el fallo
        verify(roomRepo, never()).save(any());
    }

    @Test
    @DisplayName("Unitario: patchRoom actualiza solo los campos no nulos")
    void patchRoom_ShouldUpdateOnlyNonNullFields() throws IOException {
        // Se simula la existencia de una sala previa en la base de datos
        Room existing = Room.builder().id(1L).name("Viejo").capacity(5).floor(1).build();
        when(roomRepo.findById(1L)).thenReturn(Optional.of(existing));

        // Se crea el DTO de actualización con solo el campo 'name' modificado
        RoomDto patchDto = RoomDto.builder().name("Nuevo Nombre").build();

        when(roomRepo.save(any(Room.class))).thenAnswer(i -> i.getArgument(0));

        // Se ejecuta la actualización sin enviar archivo de imagen
        RoomDto result = roomService.patchRoom(1L, patchDto, null);

        // Se comprueba que el nombre cambió pero la capacidad se mantuvo igual
        assertEquals("Nuevo Nombre", result.getName());
        assertEquals(5, result.getCapacity());

        // Se verifica que no se haya invocado al servicio de Cloudinary
        verify(cloudinaryService, never()).uploadFile(any());
    }

    @Test
    @DisplayName("Unitario: patchRoom lanza error 404 si ID no existe")
    void patchRoom_ShouldThrow404_IfNotFound() {
        // Se configura el repositorio para devolver vacío (no encontrado)
        when(roomRepo.findById(99L)).thenReturn(Optional.empty());

        RoomDto patchDto = RoomDto.builder().name("X").build();

        // Se espera que se lance la excepción personalizada de recurso no encontrado
        assertThrows(ResourceNotFoundException.class, () ->
                roomService.patchRoom(99L, patchDto, null)
        );
    }
}