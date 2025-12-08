package bookfronterab.service;

import bookfronterab.dto.AvailabilityDto;
import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import bookfronterab.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;


@Testcontainers
@SpringBootTest
class AvailabilityServiceTest {

    /**
     * Define y gestiona el contenedor Docker de PostgreSQL para la prueba.
     */
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("bookfronterab-test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Sobreescribe las propiedades de conexión de Spring para usar el contenedor.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    // Mockea el servicio de tiempo para controlar la zona horaria durante las pruebas.
    @MockitoBean private TimeService timeService;

    // Inyección del servicio bajo prueba y los repositorios.
    @Autowired private AvailabilityService availabilityService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private UserRepository userRepository;

    // Constantes para definir la fecha y zona horaria de las pruebas.
    private static final LocalDate TEST_DATE = LocalDate.of(2025, 11, 20);
    private static final ZoneId TEST_ZONE = ZoneId.of("America/Santiago");
    
    // Entidades persistidas
    private Room roomA;
    private Room roomB;
    private User testUser;

    /**
     * Configuración ejecutada antes de cada prueba.
     * 1. Limpia las tablas.
     * 2. Mockea la zona horaria del sistema.
     * 3. Persiste un usuario y dos salas de prueba.
     */
    @BeforeEach
    void setUp() {
        // Limpieza de datos (rollback manual en integración).
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Asegura que el servicio use la zona horaria de prueba.
        when(timeService.zone()).thenReturn(TEST_ZONE);

        // Persiste el usuario de prueba
        testUser = userRepository.save(User.builder()
                .email("test@example.com")
                .nombre("Test User")
                .rol(UserRole.STUDENT)
                .build());

        // Persiste las salas de prueba
        roomA = roomRepository.save(Room.builder().name("Sala A").capacity(10).floor(1).equipment(List.of("TV")).build());
        roomB = roomRepository.save(Room.builder().name("Sala B").capacity(5).floor(2).equipment(List.of("Pizarra")).build());
    }

    /**
     * Limpieza ejecutada después de cada prueba.
     */
    @AfterEach
    void tearDown() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * Verifica que el servicio genere la cantidad y el rango de slots de tiempo esperados,
     * basándose en la configuración horaria (ej. bloques UFRO).
     */
    @Test
    @DisplayName("Debe generar 11 slots correspondientes a los bloques de la UFRO")
    void getDailyAvailability_GeneratesCorrectTimeSlots() {
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        assertEquals(11, response.getSlots().size(), "Debe haber 11 slots de tiempo definidos.");
        // Verificación de los extremos de los slots.
        assertEquals("08:30-09:30", response.getSlots().get(0).getId());
        assertEquals("20:20-21:20", response.getSlots().get(10).getId());
    }

    /**
     * Verifica que si no hay reservas, todos los slots para todas las salas
     * se marquen como disponibles.
     */
    @Test
    @DisplayName("Si no hay reservas, todos los slots deben estar disponibles")
    void getDailyAvailability_WhenNoReservations_AllSlotsAreAvailable() {
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        // Debe haber 22 items: 11 slots * 2 salas.
        assertEquals(22, response.getAvailability().size(), "Debe haber una entrada por slot por sala (11 * 2).");

        // Verifica que la propiedad 'isAvailable' sea verdadera para todos los ítems.
        boolean allAvailable = response.getAvailability().stream()
                .allMatch(AvailabilityDto.AvailabilityMatrixItemDto::isAvailable);
        assertTrue(allAvailable, "Todos los slots deberían estar disponibles.");
    }

    /**
     * Verifica que una reserva que coincide exactamente con un bloque horario
     * marque ese slot como ocupado para la sala correspondiente.
     */
    @Test
    @DisplayName("Una reserva en el primer bloque debe ocuparlo correctamente")
    void getDailyAvailability_WhenReservationMatchesSlot_SlotIsOccupied() {
        // Arrange: Reserva exacta del primer bloque para Sala A (08:30-09:30)
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(8, 30), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 30), TEST_ZONE);
        crearReserva(roomA, start, end); 

        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert:
        assertSlotAvailability(response, roomA, "08:30-09:30", false); // Sala A: Ocupado
        assertSlotAvailability(response, roomB, "08:30-09:30", true);  // Sala B: Disponible
        assertSlotAvailability(response, roomA, "09:40-10:40", true);  // Sala A (siguiente slot): Disponible
    }

    /**
     * Verifica que una reserva que se superpone a dos bloques horarios,
     * marque correctamente ambos slots como ocupados.
     */
    @Test
    @DisplayName("Una reserva que solapa dos bloques debe ocupar ambos")
    void getDailyAvailability_WhenReservationOverlapsTwoSlots_BothSlotsAreOccupied() {
        // Arrange: Reserva de 09:00 a 10:00 (solapa 08:30-09:30 y 09:40-10:40)
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 0), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(10, 0), TEST_ZONE);
        crearReserva(roomA, start, end);
        
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        assertSlotAvailability(response, roomA, "08:30-09:30", false); // Bloque 1: Ocupado (solapa)
        assertSlotAvailability(response, roomA, "09:40-10:40", false); // Bloque 2: Ocupado (solapa)
        assertSlotAvailability(response, roomA, "10:50-11:50", true);  // Bloque 3: Disponible
    }
    
    /**
     * Verifica que una reserva que abarca todo el horario de servicio o más,
     * marque todos los slots como ocupados para esa sala.
     */
    @Test
    @DisplayName("Una reserva de todo el día debe ocupar todos los slots")
    void getDailyAvailability_WhenReservationIsAllDay_AllSlotsAreOccupied() {
        // Arrange: Reserva de 08:00 a 22:00 para Sala B.
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(8, 0), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(22, 0), TEST_ZONE);
        crearReserva(roomB, start, end);
        
        // Act
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        // Assert
        long slotsOcupadosRoomB = response.getAvailability().stream()
                .filter(item -> item.getRoomId().equals(String.valueOf(roomB.getId())))
                .filter(item -> !item.isAvailable()) // Contar los slots no disponibles
                .count();
        assertEquals(11, slotsOcupadosRoomB, "Todos los 11 slots de la Sala B debían estar ocupados.");
    }

    /**
     * Método auxiliar para crear y persistir una reserva.
     * @param room Sala a reservar.
     * @param startAt Hora de inicio de la reserva.
     * @param endAt Hora de fin de la reserva.
     */
    private void crearReserva(Room room, ZonedDateTime startAt, ZonedDateTime endAt) {
        Reservation res = Reservation.builder()
                .room(room)
                .startAt(startAt)
                .endAt(endAt)
                .user(testUser)
                .build();
        reservationRepository.save(res);
    }
    
    /**
     * Método auxiliar para verificar el estado (disponible/ocupado) de un slot específico.
     * @param response El DTO de respuesta de disponibilidad.
     * @param room La sala cuya disponibilidad se verifica.
     * @param slotId El ID del slot de tiempo (ej. "08:30-09:30").
     * @param expectedAvailability El valor booleano esperado (true=Disponible, false=Ocupado).
     */
    private void assertSlotAvailability(AvailabilityDto.DailyAvailabilityResponse response, Room room, String slotId, boolean expectedAvailability) {
        Optional<AvailabilityDto.AvailabilityMatrixItemDto> item = response.getAvailability().stream()
                .filter(a -> a.getRoomId().equals(String.valueOf(room.getId())) && a.getSlotId().equals(slotId))
                .findFirst();

        assertTrue(item.isPresent(), "El slot " + slotId + " para la sala " + room.getName() + " debe existir.");
        assertEquals(expectedAvailability, item.get().isAvailable(), 
                     "El slot " + slotId + " para la sala " + room.getName() + 
                     (expectedAvailability ? " debería estar disponible." : " debería estar ocupado."));
    }
}