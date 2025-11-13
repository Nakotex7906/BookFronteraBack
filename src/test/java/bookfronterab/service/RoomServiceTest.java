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

/**
 * Pruebas de integración para {@link RoomService}.
 *
 * <p>Esta clase de prueba levanta el contexto completo de Spring Boot
 * (@SpringBootTest) y utiliza Testcontainers (@Testcontainers) para
 * ejecutar los tests contra una base de datos PostgreSQL real y efímera.
 * Esto asegura que la lógica del servicio, el mapeo de JPA y las
 * consultas SQL personalizadas funcionan como se espera en un entorno
 * similar al de producción.</p>
 */
@Testcontainers
@SpringBootTest
class RoomServiceTest {

    // --- 1. Configuración de Testcontainers (BBDD real) ---

    /**
     * Define y configura el contenedor Docker de PostgreSQL que se
     * iniciará antes de que se ejecuten las pruebas de esta clase.
     * Es estático para que se comparta entre todos los métodos de prueba.
     */
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("bookfronterab-test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Intercepta la configuración de la aplicación ANTES de que arranque
     * y sobrescribe las propiedades de la base de datos (URL, usuario, contraseña)
     * con los valores dinámicos generados por el contenedor de Testcontainers.
     *
     * @param registry El registro de propiedades de Spring.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    // --- 2. Inyección de Servicio y Repositorios Reales ---

    /**
     * El servicio bajo prueba (System Under Test - SUT).
     * Spring inyecta la instancia real del servicio.
     */
    @Autowired
    private RoomService roomService;

    /**
     * Repositorio real de Room, inyectado por Spring.
     * Se utiliza para configurar el estado de la base de datos (Arrange)
     * y para limpiar (Teardown).
     */
    @Autowired
    private RoomRepository roomRepository;

    /**
     * Método de configuración que se ejecuta ANTES de cada prueba (@Test).
     * Asegura que la tabla de 'rooms' esté vacía, garantizando el
     * aislamiento de cada prueba.
     */
    @BeforeEach
    void setUp() {
        // Limpiamos la BBDD antes de CADA test
        roomRepository.deleteAllInBatch();
    }

    /**
     * Método de limpieza que se ejecuta DESPUÉS de cada prueba (@Test).
     * Limpia los datos creados durante la prueba.
     */
    @AfterEach
    void tearDown() {
        roomRepository.deleteAllInBatch();
    }

    // --- 3. Los Tests ---

    /**
     * Prueba el escenario "feliz" donde existen salas en la base de datos.
     * Verifica que el servicio:
     * 1. Encuentra todas las salas.
     * 2. Las mapea correctamente a {@link RoomDto}.
     * 3. Maneja correctamente la carga de colecciones (como 'equipment').
     */
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
        
        // Verificamos el mapeo de la Sala 1
        assertEquals("Sala 1", resultDtos.get(0).getName());
        assertEquals(10, resultDtos.get(0).getCapacity());
        assertEquals(2, resultDtos.get(0).getEquipment().size()); // Verifica la carga de la colección
        assertTrue(resultDtos.get(0).getEquipment().contains("TV"));
        
        // Verificamos el mapeo de la Sala 2
        assertEquals("Sala 2", resultDtos.get(1).getName());
        assertEquals(1, resultDtos.get(1).getEquipment().size()); // Verifica la carga de la colección
        assertTrue(resultDtos.get(1).getEquipment().contains("Proyector"));
    }

    /**
     * Prueba el escenario donde la base de datos está vacía.
     * Verifica que el servicio devuelve una lista vacía en lugar de nulo.
     */
    @Test
    @DisplayName("getAllRooms debe devolver una lista vacía si no hay salas")
    void getAllRooms_ShouldReturnEmptyList_WhenNoRoomsExist() {
        // Arrange
        // No hay datos (gracias al @BeforeEach)

        // Act
        List<RoomDto> resultDtos = roomService.getAllRooms();

        // Assert
        assertNotNull(resultDtos, "La lista no debe ser nula");
        assertTrue(resultDtos.isEmpty(), "La lista debe estar vacía");
    }
}