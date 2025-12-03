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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

/**
 * Pruebas de integración para AvailabilityService.
 * Valida la generación de slots y el cálculo de disponibilidad.
 */
@Testcontainers
@SpringBootTest
class AvailabilityServiceTest {

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

    @MockBean private TimeService timeService;

    @Autowired private AvailabilityService availabilityService;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private UserRepository userRepository;

    private static final LocalDate TEST_DATE = LocalDate.of(2025, 11, 20);
    private static final ZoneId TEST_ZONE = ZoneId.of("America/Santiago");
    
    private Room roomA;
    private Room roomB;
    private User testUser;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        when(timeService.zone()).thenReturn(TEST_ZONE);

        testUser = userRepository.save(User.builder()
                .email("test@example.com")
                .nombre("Test User")
                .rol(UserRole.STUDENT)
                .build());

        roomA = roomRepository.save(Room.builder().name("Sala A").capacity(10).floor(1).equipment(List.of("TV")).build());
        roomB = roomRepository.save(Room.builder().name("Sala B").capacity(5).floor(2).equipment(List.of("Pizarra")).build());
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Debe generar 11 slots correspondientes a los bloques de la UFRO")
    void getDailyAvailability_GeneratesCorrectTimeSlots() {
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        assertEquals(11, response.getSlots().size());
        assertEquals("08:30-09:30", response.getSlots().get(0).getId());
        assertEquals("20:20-21:20", response.getSlots().get(10).getId());
    }

    @Test
    @DisplayName("Si no hay reservas, todos los slots deben estar disponibles")
    void getDailyAvailability_WhenNoReservations_AllSlotsAreAvailable() {
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        assertEquals(11, response.getSlots().size());
        assertEquals(22, response.getAvailability().size()); 

        boolean allAvailable = response.getAvailability().stream()
                .allMatch(AvailabilityDto.AvailabilityMatrixItemDto::isAvailable);
        assertTrue(allAvailable, "Todos los slots deberían estar disponibles");
    }

    @Test
    @DisplayName("Una reserva en el primer bloque debe ocuparlo correctamente")
    void getDailyAvailability_WhenReservationMatchesSlot_SlotIsOccupied() {
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(8, 30), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 30), TEST_ZONE);
        crearReserva(roomA, start, end); 

        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        assertSlotAvailability(response, roomA, "08:30-09:30", false);
        assertSlotAvailability(response, roomB, "08:30-09:30", true);
        assertSlotAvailability(response, roomA, "09:40-10:40", true);
    }

    @Test
    @DisplayName("Una reserva que solapa dos bloques debe ocupar ambos")
    void getDailyAvailability_WhenReservationOverlapsTwoSlots_BothSlotsAreOccupied() {
        // Reserva de 09:00 a 10:00 solapa el bloque 1 (08:30-09:30) y bloque 2 (09:40-10:40)
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 0), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(10, 0), TEST_ZONE);
        crearReserva(roomA, start, end);
        
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        assertSlotAvailability(response, roomA, "08:30-09:30", false);
        assertSlotAvailability(response, roomA, "09:40-10:40", false);
        assertSlotAvailability(response, roomA, "10:50-11:50", true);
    }
    
    @Test
    @DisplayName("Una reserva de todo el día debe ocupar todos los slots")
    void getDailyAvailability_WhenReservationIsAllDay_AllSlotsAreOccupied() {
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(8, 0), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(22, 0), TEST_ZONE);
        crearReserva(roomB, start, end);
        
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);

        long slotsOcupadosRoomB = response.getAvailability().stream()
                .filter(item -> item.getRoomId().equals(String.valueOf(roomB.getId())))
                .filter(item -> !item.isAvailable())
                .count();
        assertEquals(11, slotsOcupadosRoomB, "Todos los slots de la Sala B debían estar ocupados");
    }

    private void crearReserva(Room room, ZonedDateTime startAt, ZonedDateTime endAt) {
        Reservation res = Reservation.builder()
                .room(room)
                .startAt(startAt)
                .endAt(endAt)
                .user(testUser)
                .build();
        reservationRepository.save(res);
    }

    private void assertSlotAvailability(AvailabilityDto.DailyAvailabilityResponse response, Room room, String slotId, boolean expectedAvailability) {
        String roomId = String.valueOf(room.getId()); 
        
        Optional<AvailabilityDto.AvailabilityMatrixItemDto> slot = response.getAvailability().stream()
                .filter(item -> item.getRoomId().equals(roomId) && item.getSlotId().equals(slotId))
                .findFirst();
        
        assertTrue(slot.isPresent(), "No se encontró el slot: " + slotId + " para la sala: " + roomId);
        
        assertEquals(expectedAvailability, slot.get().isAvailable(),
                String.format("Disponibilidad Sala %s Slot %s: Esperado %b, Actual %b",
                        roomId, slotId, expectedAvailability, slot.get().isAvailable()));
    }
    @Test
    @DisplayName("Una reserva en el intervalo entre slots no debe ocupar ningún slot")
    void getDailyAvailability_ReservationInSlotGap_ShouldNotOccupyAnySlot() {
        // El primer slot es 08:30-09:30. El segundo slot empieza a las 09:40.
        // El intervalo (gap) es de 09:30 a 09:40.
        ZonedDateTime start = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 30), TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(TEST_DATE, LocalTime.of(9, 40), TEST_ZONE);
        crearReserva(roomA, start, end); 
        
        AvailabilityDto.DailyAvailabilityResponse response = availabilityService.getDailyAvailability(TEST_DATE);
        
        // Verificamos que el slot anterior (08:30-09:30) esté disponible.
        assertSlotAvailability(response, roomA, "08:30-09:30", true); 
        
        // Verificamos que el slot siguiente (09:40-10:40) esté disponible.
        assertSlotAvailability(response, roomA, "09:40-10:40", true);
        
        // Verificamos que ningún slot de esta sala esté ocupado por la reserva del gap.
        long occupiedSlots = response.getAvailability().stream()
                .filter(item -> item.getRoomId().equals(String.valueOf(roomA.getId())))
                .filter(item -> !item.isAvailable())
                .count();
        
        assertEquals(0, occupiedSlots, "Ningún slot de la Sala A debería estar ocupado por una reserva en el gap.");
    }
}