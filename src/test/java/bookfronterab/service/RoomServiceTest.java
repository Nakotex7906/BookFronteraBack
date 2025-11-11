package bookfronterab.service;

import bookfronterab.dto.RoomDto;
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

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
class RoomServiceTest {

    // --- 1. Configuración de Testcontainers (BBDD real) ---
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

    // --- 2. Inyección de Servicio y Repositorios Reales ---
    @Autowired
    private RoomService roomService; // El servicio que estamos probando

    @Autowired
    private RoomRepository roomRepository; // El repo real para meter datos

    @BeforeEach
    void setUp() {
        // Limpiamos la BBDD antes de CADA test
        roomRepository.deleteAllInBatch();
    }

    @AfterEach
    void tearDown() {
        roomRepository.deleteAllInBatch();
    }

    // --- 3. Los Tests ---

    @Test
    @DisplayName("getAllRooms debe devolver una lista de DTOs cuando hay salas")
    void getAllRooms_ShouldReturnRoomDtos_WhenRoomsExist() {
        // Arrange
        // Creamos y guardamos datos de prueba directamente en la BBDD
        Room room1 = Room.builder()
                .name("Sala 1")
                .capacity(10)
                .floor(1)
                .equipment(List.of("TV", "Pizarra")) // Dato clave
                .build();
        roomRepository.save(room1);

        Room room2 = Room.builder()
                .name("Sala 2")
                .capacity(5)
                .floor(2)
                .equipment(List.of("Proyector")) // Dato clave
                .build();
        roomRepository.save(room2);

        // Act
        // Llamamos al método del servicio que queremos probar
        List<RoomDto> resultDtos = roomService.getAllRooms();

        // Assert
        // Verificamos que los datos se hayan mapeado correctamente
        assertEquals(2, resultDtos.size());
        
        assertEquals("Sala 1", resultDtos.get(0).getName());
        assertEquals(10, resultDtos.get(0).getCapacity());
        assertEquals(2, resultDtos.get(0).getEquipment().size()); // Verificamos la lista lazy
        assertTrue(resultDtos.get(0).getEquipment().contains("TV"));
        
        assertEquals("Sala 2", resultDtos.get(1).getName());
        assertEquals(1, resultDtos.get(1).getEquipment().size()); // Verificamos la lista lazy
        assertTrue(resultDtos.get(1).getEquipment().contains("Proyector"));
    }

    @Test
    @DisplayName("getAllRooms debe devolver una lista vacía si no hay salas")
    void getAllRooms_ShouldReturnEmptyList_WhenNoRoomsExist() {
        // Arrange
        // No hay datos (gracias al @BeforeEach)

        // Act
        List<RoomDto> resultDtos = roomService.getAllRooms();

        // Assert
        assertNotNull(resultDtos);
        assertTrue(resultDtos.isEmpty());
    }
}