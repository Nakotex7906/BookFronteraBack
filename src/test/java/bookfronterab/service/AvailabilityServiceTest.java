package bookfronterab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.dto.RoomDto;
import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import bookfronterab.repo.UserRepository;

/**
 * Pruebas de integración para {@link AvailabilityService}.
 *
 * <p>Esta clase prueba la lógica de negocio de {@link AvailabilityService}
 * en un entorno de alta fidelidad. Utiliza:</p>
 * <ul>
 * <li>{@link SpringBootTest} para cargar el contexto completo de la aplicación.</li>
 * <li>{@link Testcontainers} para ejecutar pruebas contra una base de datos
 * PostgreSQL real en un contenedor Docker.</li>
 * <li>{@link MockBean} para simular {@link TimeService} y asegurar
 * que la zona horaria de la prueba es determinística.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
class AvailabilityServiceTest {

    // --- 1. Configuración de Testcontainers (BBDD real) ---

    /**
     * Define el contenedor de PostgreSQL que se iniciará una vez
     * para todas las pruebas de esta clase.
     */
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("bookfronterab-test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Sobrescribe dinámicamente las propiedades de configuración de Spring
     * (como la URL de la BBDD) para apuntar al contenedor de Testcontainers.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

// --- 2. Mock de Dependencias Externas ---

    /**
     * Mock del servicio {@link TimeService}, generalmente inyectado por
     * el framework de pruebas (ej. Mockito).
     * <p>
     * Se utiliza para simular y controlar el comportamiento del tiempo
     * (como fijar la zona horaria o una hora específica). Esto es
     * esencial para garantizar que los tests que dependen del tiempo
     * (ej. cálculo de slots) sean deterministas y repetibles.
     */
    @Mock // O @MockBean si estás en un contexto de Spring Boot Test
    private TimeService timeService;

    // --- 3. Inyección de Servicio y Repositorios Reales ---

    /**
     * El servicio bajo prueba (System Under Test). Se inyecta la instancia real
     * gestionada por Spring.
     */
    @Autowired
    private AvailabilityService availabilityService;

    /**
     * Repositorio real de Room, inyectado por Spring.
     * Se utiliza para configurar el estado de la base de datos (Arrange).
     */
    @Autowired
    private RoomRepository roomRepository;

    /**
     * Repositorio real de Reservation, inyectado por Spring.
     * Se utiliza para configurar el estado de la base de datos (Arrange).
     */
    @Autowired
    private ReservationRepository reservationRepository;
    
    /**
     * Repositorio real de User, inyectado por Spring.
     * Se utiliza para crear el usuario necesario para las reservas.
     */
    @Autowired
    private UserRepository userRepository;

    // --- 4. Datos de Prueba ---
    private static final LocalDate TEST_DATE = LocalDate.of(2025, 11, 20);
    private static final ZoneId TEST_ZONE = ZoneId.of("Europe/Madrid"); // Una zona fija para tests
    
    private Room roomA;
    private Room roomB;
    private User testUser; // El usuario que creará las reservas

    /**
     * Configuración ejecutada ANTES de CADA test (@Test).
     * <p>Se encarga de:</p>
     * <ol>
     * <li>Limpiar todas las tablas de la BBDD para asegurar aislamiento.</li>
     * <li>Configurar el mock de {@link TimeService} para que devuelva una zona fija.</li>
     * <li>Crear y guardar entidades (User, Room) de prueba en la BBDD.</li>
     * </ol>
     */
    @BeforeEach
    void setUp() {
        // Limpiamos la BBDD (orden importante: hijos primero)
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Configuramos el mock de TimeService
        when(timeService.zone()).thenReturn(TEST_ZONE);

        // Creamos el usuario de prueba
        testUser = userRepository.save(User.builder()
                .email("test@example.com")
                .nombre("Test User")
                .rol(UserRole.STUDENT)
                .build());

        // Creamos salas de prueba
        roomA = roomRepository.save(Room.builder()
                .name("Sala A")
                .capacity(10)
                .floor(1)
                .equipment(List.of("TV"))
                .build());

        roomB = roomRepository.save(Room.builder()
                .name("Sala B")
                .capacity(5)
                .floor(2)
                .equipment(List.of("Pizarra"))
                .build());
    }

    /**
     * Limpieza ejecutada DESPUÉS de CADA test (@Test).
     * Borra todos los datos creados durante la prueba.
     */
    @AfterEach
    void tearDown() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // --- 5. Los Tests ---

    /**
     * Prueba la lógica de generación de slots ({@code generateTimeSlots}).
     * Verifica que se cree el número correcto de slots entre la hora
     * de inicio y fin definidas en el servicio.
     */
    @Test
    @DisplayName("Debe generar 10 slots de 1 hora entre las 8:00 y las 18:00")
    void getDailyAvailability_GeneratesCorrectTimeSlots() {
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        // (8-9, 9-10, 10-11, 11-12, 12-13, 13-14, 14-15, 15-16, 16-17, 17-18)
        assertEquals(10, response.getSlots().size());
        assertEquals("08:00-09:00", response.getSlots().get(0).getId());
        assertEquals("17:00-18:00", response.getSlots().get(9).getId());
    }

    /**
     * Prueba la lógica de mapeo de Entidad a DTO.
     * Verifica que las propiedades de la entidad {@link Room} (incluyendo
     * la lista 'equipment' cargada vía {@code FetchType.EAGER})
     * se mapean correctamente al {@link RoomDto}.
     */
    @Test
    @DisplayName("Debe mapear los datos de Room a RoomDto correctamente")
    void getDailyAvailability_MapsRoomToDto() {
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        assertEquals(2, response.getRooms().size());

        RoomDto roomADto = response.getRooms().stream()
                .filter(r -> r.getName().equals("Sala A"))
                .findFirst()
                .orElse(null);
        
        assertNotNull(roomADto);
        assertEquals(roomA.getId(), roomADto.getId());
        assertEquals(10, roomADto.getCapacity());
        assertEquals(1, roomADto.getFloor());
        assertTrue(roomADto.getEquipment().contains("TV"));
    }

    /**
     * Prueba el escenario "feliz" donde no existen reservas.
     * Verifica que la matriz de disponibilidad marca todos los
     * slots como disponibles.
     */
    @Test
    @DisplayName("Si no hay reservas, todos los slots deben estar disponibles")
    void getDailyAvailability_WhenNoReservations_AllSlotsAreAvailable() {
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        assertEquals(2, response.getRooms().size());
        assertEquals(10, response.getSlots().size());
        assertEquals(20, response.getAvailability().size()); // 2 salas * 10 slots

        // Verificamos que todos los items en la matriz tengan isAvailable = true
        boolean allAvailable = response.getAvailability().stream()
                .allMatch(AvailabilityDto.AvailabilityMatrixItemDto::isAvailable);
        assertTrue(allAvailable, "Todos los slots deberían estar disponibles");
    }

    /**
     * Prueba la lógica de detección de conflictos.
     * Verifica que una reserva que coincide exactamente con un slot
     * marca ese slot como NO disponible (ocupado).
     */
    @Test
    @DisplayName("Una reserva que coincide exactamente con un slot debe ocuparlo")
    void getDailyAvailability_WhenReservationMatchesSlot_SlotIsOccupied() {
        // Arrange
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 0), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(10, 0), TEST_ZONE);
        crearReserva(roomA, start, end); // Reserva de 9:00 a 10:00 en Sala A

        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        // Slot 09:00-10:00 en Sala A debe estar NO DISPONIBLE
        assertSlotAvailability(response, roomA, "09:00-10:00", false);

        // Slot 09:00-10:00 en Sala B debe estar DISPONIBLE
        assertSlotAvailability(response, roomB, "09:00-10:00", true);

        // Slot 10:00-11:00 en Sala A debe estar DISPONIBLE
        assertSlotAvailability(response, roomA, "10:00-11:00", true);
    }

    /**
     * Prueba la lógica de detección de conflictos ({@code isOccupied})
     * cuando una reserva se solapa parcialmente con dos slots.
     * (Ej. Reserva de 9:30 a 10:30)
     * Verifica que AMBOS slots (9:00-10:00 y 10:00-11:00) se marcan
     * como NO disponibles.
     */
    @Test
    @DisplayName("Una reserva que solapa dos slots (9:30-10:30) debe ocupar AMBOS (9-10 y 10-11)")
    void getDailyAvailability_WhenReservationOverlapsTwoSlots_BothSlotsAreOccupied() {
        // Arrange
        // Reserva de 9:30 a 10:30 en Sala A
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 30), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(10, 30), TEST_ZONE);
        crearReserva(roomA, start, end);
        
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        assertSlotAvailability(response, roomA, "08:00-09:00", true); // Antes (libre)
        assertSlotAvailability(response, roomA, "09:00-10:00", false); // Solapado
        assertSlotAvailability(response, roomA, "10:00-11:00", false); // Solapado
        assertSlotAvailability(response, roomA, "11:00-12:00", true); // Después (libre)
    }
    
    /**
     * Prueba un caso límite donde una reserva cubre todo el horario de apertura.
     * Verifica que todos los slots para esa sala se marcan como no disponibles.
     */
    @Test
    @DisplayName("Una reserva que dura todo el día debe ocupar todos los slots")
    void getDailyAvailability_WhenReservationIsAllDay_AllSlotsAreOccupied() {
        // Arrange
        // Reserva de 8:00 a 18:00 en Sala B
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(8, 0), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(18, 0), TEST_ZONE);
        crearReserva(roomB, start, end);
        
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        // Contamos cuántos slots están NO disponibles (isAvailable = false) para la Sala B
        long slotsOcupadosRoomB = response.getAvailability().stream()
                .filter(item -> item.getRoomId().equals(String.valueOf(roomB.getId())))
                .filter(item -> !item.isAvailable())
                .count();
        assertEquals(10, slotsOcupadosRoomB, "Todos los 10 slots de la Sala B debían estar ocupados");
        
        // Verificamos que la Sala A sigue completamente libre
        long slotsOcupadosRoomA = response.getAvailability().stream()
                .filter(item -> item.getRoomId().equals(String.valueOf(roomA.getId())))
                .filter(item -> !item.isAvailable())
                .count();
        assertEquals(0, slotsOcupadosRoomA, "La Sala A debía estar totalmente disponible");
    }


    // --- Métodos de Ayuda (Helpers) ---

    /**
     * Método de ayuda privado para crear y guardar una {@link Reservation}
     * con los datos mínimos requeridos para los tests.
     *
     * @param room La sala reservada.
     * @param startAt La fecha/hora de inicio.
     * @param endAt La fecha/hora de fin.
     */
    private void crearReserva(Room room, ZonedDateTime startAt, ZonedDateTime endAt) {
        Reservation res = Reservation.builder()
                .room(room)
                .startAt(startAt)
                .endAt(endAt)
                .user(testUser) // Usamos el usuario de prueba creado en setUp()
                .build();
        
        reservationRepository.save(res);
    }

    /**
     * Método de ayuda para verificar la disponibilidad ({@code isAvailable})
     * de un item específico en la matriz de disponibilidad.
     *
     * @param response El DTO de respuesta que contiene la matriz.
     * @param room La sala a verificar.
     * @param slotId El ID del slot a verificar (ej. "09:00-10:00").
     * @param expectedAvailability El estado de disponibilidad esperado (true o false).
     */
    private void assertSlotAvailability(AvailabilityDto.DailyAvailabilityResponse response, Room room, String slotId, boolean expectedAvailability) {
        String roomId = String.valueOf(room.getId()); 
        
        Optional<AvailabilityDto.AvailabilityMatrixItemDto> slot = response.getAvailability().stream()
                .filter(item -> item.getRoomId().equals(roomId) && item.getSlotId().equals(slotId))
                .findFirst();
        
        assertTrue(slot.isPresent(), "No se encontró el slot: " + slotId + " para la sala: " + roomId);
        
        assertEquals(expectedAvailability, slot.get().isAvailable(),
                String.format("Disponibilidad esperada para Sala %s en Slot %s era %b, pero fue %b",
                        roomId, slotId, expectedAvailability, slot.get().isAvailable()));
    }
}