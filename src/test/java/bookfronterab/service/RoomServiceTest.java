package bookfronterab.service;

import bookfronterab.dto.RoomDto;
import bookfronterab.exception.ResourceNotFoundException;
import bookfronterab.model.Room;
import bookfronterab.repo.RoomRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas de integración para RoomService.
 * Verifica CRUD completo contra base de datos real.
 */
@Testcontainers
@SpringBootTest
class RoomServiceTest {

    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("bookfronterab-test")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @Autowired private RoomService roomService;
    @Autowired private RoomRepository roomRepository;

    @BeforeEach
    void setUp() {
        roomRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("createRoom() debe guardar la sala y devolver DTO")
    void createRoom_ShouldSaveAndReturnDto() {
        RoomDto request = RoomDto.builder().name("Nueva Sala").capacity(20).floor(3).equipment(List.of("PC")).build();

        RoomDto response = roomService.createRoom(request);

        assertNotNull(response.getId());
        assertEquals("Nueva Sala", response.getName());
        
        Optional<Room> saved = roomRepository.findById(response.getId());
        assertTrue(saved.isPresent());
    }

    @Test
    @DisplayName("getAllRooms debe devolver lista de salas")
    void getAllRooms_ShouldReturnList() {
        roomRepository.save(Room.builder().name("S1").capacity(5).floor(1).equipment(List.of("A")).build());
        roomRepository.save(Room.builder().name("S2").capacity(5).floor(1).equipment(List.of("B")).build());

        List<RoomDto> dtos = roomService.getAllRooms();
        assertEquals(2, dtos.size());
    }

    @Test
    @DisplayName("patchRoom() debe actualizar solo campos no nulos")
    void patchRoom_ShouldUpdatePartialFields() {
        Room original = roomRepository.save(Room.builder().name("Original").capacity(10).floor(1).equipment(List.of("A")).build());

        RoomDto patchDto = new RoomDto();
        patchDto.setName("Nombre Cambiado");

        RoomDto result = roomService.patchRoom(original.getId(), patchDto);

        assertEquals("Nombre Cambiado", result.getName());
        assertEquals(10, result.getCapacity());
    }

    @Test
    @DisplayName("delateRoom() debe eliminar la sala de la BD")
    void deleteRoom_ShouldRemoveFromDb() {
        Room r = roomRepository.save(Room.builder().name("Borrar").capacity(5).build());

        roomService.delateRoom(r.getId());

        Optional<Room> check = roomRepository.findById(r.getId());
        assertTrue(check.isEmpty());
    }
    
    @Test
    @DisplayName("patchRoom() lanza excepción si ID no existe")
    void patchRoom_ShouldThrow_WhenNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> 
            roomService.patchRoom(999L, new RoomDto())
        );
    }
}