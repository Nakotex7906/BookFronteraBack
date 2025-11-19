package bookfronterab.service;

import bookfronterab.dto.ReservationDto;
import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import bookfronterab.repo.UserRepository;
import bookfronterab.service.google.GoogleCalendarService;
import bookfronterab.service.google.GoogleCredentialsService;
import com.google.api.client.auth.oauth2.Credential;
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

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de integración para ReservationService.
 * Cubre creación, validación de conflictos y permisos de cancelación.
 */
@Testcontainers
@SpringBootTest
class ReservationServiceTest {

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

    @MockBean private GoogleCalendarService googleCalendarService;
    @MockBean private GoogleCredentialsService googleCredentialsService;
    @MockBean private TimeService timeService;
    @MockBean private Credential mockCredential;

    @Autowired private ReservationService reservationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ReservationRepository reservationRepository;

    private User testUser;
    private User otherUser;
    private User adminUser;
    private Room testRoom;
    private static final ZoneId TEST_ZONE = ZoneId.of("UTC");
    private ZonedDateTime fixedNow;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        fixedNow = ZonedDateTime.of(2025, 11, 20, 10, 30, 0, 0, TEST_ZONE);
        when(timeService.nowOffset()).thenReturn(fixedNow.toOffsetDateTime());
        when(timeService.zone()).thenReturn(TEST_ZONE);

        testUser = userRepository.save(User.builder().email("test@example.com").nombre("Test User").rol(UserRole.STUDENT).build());
        otherUser = userRepository.save(User.builder().email("other@example.com").nombre("Other User").rol(UserRole.STUDENT).build());
        adminUser = userRepository.save(User.builder().email("admin@example.com").nombre("Admin User").rol(UserRole.ADMIN).build());

        testRoom = roomRepository.save(Room.builder().name("Sala de Pruebas").capacity(10).floor(1).build());
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("create() debe crear reserva exitosamente")
    void create_ShouldSucceed_WhenNoGoogleCalendar() throws IOException {
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);

        reservationService.create(testUser.getEmail(), request);

        assertEquals(1, reservationRepository.count());
        verify(googleCalendarService, never()).createEventForReservation(any(), any());
    }

    @Test
    @DisplayName("create() debe fallar si hay conflicto de horario")
    void create_ShouldFail_WhenTimeSlotConflicts() {
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        createTestReservation(testUser, testRoom, start, end);

        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);

        assertThrows(IllegalStateException.class, () -> reservationService.create(testUser.getEmail(), request));
    }

    @Test
    @DisplayName("create() debe fallar si el estudiante excede el límite semanal")
    void create_ShouldFail_WhenWeeklyLimitExceeded() {
        ZonedDateTime mondayStart = ZonedDateTime.of(2025, 11, 17, 10, 0, 0, 0, TEST_ZONE);
        createTestReservation(testUser, testRoom, mondayStart, mondayStart.plusHours(1));

        ZonedDateTime fridayStart = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), fridayStart, fridayStart.plusHours(1), false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> 
            reservationService.create(testUser.getEmail(), request));
        assertTrue(ex.getMessage().contains("Límite alcanzado"));
    }

    @Test
    @DisplayName("create() debe permitir al ADMIN exceder el límite semanal")
    void create_ShouldSucceed_WhenWeeklyLimitExceeded_IfAdmin() {
        ZonedDateTime mondayStart = ZonedDateTime.of(2025, 11, 17, 10, 0, 0, 0, TEST_ZONE);
        createTestReservation(adminUser, testRoom, mondayStart, mondayStart.plusHours(1));

        ZonedDateTime fridayStart = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), fridayStart, fridayStart.plusHours(1), false);

        assertDoesNotThrow(() -> reservationService.create(adminUser.getEmail(), request));
        assertEquals(2, reservationRepository.findAll().size());
    }

    @Test
    @DisplayName("cancel() debe fallar si el usuario no es dueño ni Admin")
    void cancel_ShouldFail_IfNotOwnerOrAdmin() {
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(1).plusHours(1));

        assertThrows(SecurityException.class, () -> 
            reservationService.cancel(res.getId(), otherUser.getEmail()));
        
        assertEquals(1, reservationRepository.count());
    }

    @Test
    @DisplayName("cancel() debe permitir al Admin cancelar cualquier reserva")
    void cancel_ShouldAllowAdminToCancel() {
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(1).plusHours(1));

        reservationService.cancel(res.getId(), adminUser.getEmail());

        assertEquals(0, reservationRepository.count());
    }

    private Reservation createTestReservation(User user, Room room, ZonedDateTime startAt, ZonedDateTime endAt) {
        return reservationRepository.save(Reservation.builder().user(user).room(room).startAt(startAt).endAt(endAt).build());
    }
}