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
import org.springframework.test.context.bean.override.mockito.MockitoBean; // Usamos la nueva anotación
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    // Usamos @MockitoBean (Spring Boot 3.4+) para evitar los warnings de deprecated
    @MockitoBean private GoogleCalendarService googleCalendarService;
    @MockitoBean private GoogleCredentialsService googleCredentialsService;
    @MockitoBean private TimeService timeService;
    @MockitoBean private Credential mockCredential;

    @Autowired private ReservationService reservationService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private ReservationRepository reservationRepository;

    private User testUser;
    private User otherUser;
    private User adminUser;
    private Room testRoom;
    private static final ZoneId TEST_ZONE = ZoneId.of("America/Santiago"); // Usamos tu zona horaria real

    // Esta variable almacenará una fecha futura válida calculada dinámicamente
    private ZonedDateTime nextMonday;

    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // --- LÓGICA DINÁMICA DE FECHAS ---
        // 1. Obtenemos la fecha real actual (la misma que usa tu Service)
        ZonedDateTime now = ZonedDateTime.now(TEST_ZONE);

        // 2. Calculamos el "Próximo Lunes" para asegurar que:
        //    a) Sea FUTURO (evita error "ya ha finalizado").
        //    b) Esté cerca (evita error "más de 3 meses").
        //    c) Sea Lunes (para que el test de límite semanal Lunes-Viernes funcione).
        nextMonday = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        // Alineamos el mock de TimeService con nuestra fecha calculada
        when(timeService.nowOffset()).thenReturn(nextMonday.toOffsetDateTime());
        when(timeService.zone()).thenReturn(TEST_ZONE);

        testUser = userRepository.save(User.builder().email("test@ufromail.cl").nombre("Test User").rol(UserRole.STUDENT).build());
        otherUser = userRepository.save(User.builder().email("other@ufromail.cl").nombre("Other User").rol(UserRole.STUDENT).build());
        adminUser = userRepository.save(User.builder().email("admin@ufromail.cl").nombre("Admin User").rol(UserRole.ADMIN).build());

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
        // Usamos el próximo lunes
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);

        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);

        reservationService.create(testUser.getEmail(), request);

        assertEquals(1, reservationRepository.count());
        verify(googleCalendarService, never()).createEventForReservation(any(), any());
    }

    @Test
    @DisplayName("create() debe fallar si hay conflicto de horario")
    void create_ShouldFail_WhenTimeSlotConflicts() {
        // Creamos una reserva para el próximo lunes
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);
        createTestReservation(testUser, testRoom, start, end);

        // Intentamos crear OTRA en el mismo horario
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);

        assertThrows(IllegalStateException.class, () -> reservationService.create(testUser.getEmail(), request));
    }

    @Test
    @DisplayName("create() debe fallar si el estudiante excede el límite semanal")
    void create_ShouldFail_WhenWeeklyLimitExceeded() {
        // 1. Reserva el Lunes (Próximo Lunes)
        ZonedDateTime mondayStart = nextMonday;
        createTestReservation(testUser, testRoom, mondayStart, mondayStart.plusHours(1));

        // 2. Intenta reservar el Viernes DE LA MISMA SEMANA
        ZonedDateTime fridayStart = nextMonday.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));

        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), fridayStart, fridayStart.plusHours(1), false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.create(testUser.getEmail(), request));

        // Verificamos que sea el error de límite y no otro
        assertTrue(ex.getMessage().contains("Límite alcanzado"));
    }

    @Test
    @DisplayName("create() debe permitir al ADMIN exceder el límite semanal")
    void create_ShouldSucceed_WhenWeeklyLimitExceeded_IfAdmin() {
        // 1. Admin reserva el Lunes
        ZonedDateTime mondayStart = nextMonday;
        createTestReservation(adminUser, testRoom, mondayStart, mondayStart.plusHours(1));

        // 2. Admin intenta reservar el Viernes (Misma semana) -> DEBE PASAR
        ZonedDateTime fridayStart = nextMonday.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));

        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), fridayStart, fridayStart.plusHours(1), false);

        assertDoesNotThrow(() -> reservationService.create(adminUser.getEmail(), request));
        assertEquals(2, reservationRepository.findAll().size());
    }

    @Test
    @DisplayName("cancel() debe fallar si el usuario no es dueño ni Admin")
    void cancel_ShouldFail_IfNotOwnerOrAdmin() {
        // Usamos una reserva futura
        Reservation res = createTestReservation(testUser, testRoom, nextMonday, nextMonday.plusHours(1));

        assertThrows(SecurityException.class, () ->
                reservationService.cancel(res.getId(), otherUser.getEmail()));

        assertEquals(1, reservationRepository.count());
    }

    @Test
    @DisplayName("cancel() debe permitir al Admin cancelar cualquier reserva")
    void cancel_ShouldAllowAdminToCancel() {
        // Usamos una reserva futura
        Reservation res = createTestReservation(testUser, testRoom, nextMonday, nextMonday.plusHours(1));

        reservationService.cancel(res.getId(), adminUser.getEmail());

        assertEquals(0, reservationRepository.count());
    }

    private Reservation createTestReservation(User user, Room room, ZonedDateTime startAt, ZonedDateTime endAt) {
        return reservationRepository.save(Reservation.builder().user(user).room(room).startAt(startAt).endAt(endAt).build());
    }
}