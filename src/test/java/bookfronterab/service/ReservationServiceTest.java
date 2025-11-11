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

import java.io.IOException; // <--- Importante
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
class ReservationServiceTest {

    // --- Configuración de Testcontainers (BBDD real) ---
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

    // --- Mocks de Dependencias ---
    @MockBean
    private GoogleCalendarService googleCalendarService;
    @MockBean
    private GoogleCredentialsService googleCredentialsService;
    @MockBean
    private TimeService timeService;
    
    @MockBean
    private Credential mockCredential; // Mockeamos la clase Credential

    // --- Inyección de Servicio (Real) y Repositorios (Reales) ---
    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    // --- Datos de Prueba ---
    private User testUser;
    private User adminUser;
    private Room testRoom;
    private static final ZoneId TEST_ZONE = ZoneId.of("UTC");
    private ZonedDateTime fixedNow;

    @BeforeEach
    void setUp() {
        // Limpiamos la BBDD
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Configuramos los Mocks
        fixedNow = ZonedDateTime.of(2025, 11, 20, 10, 30, 0, 0, TEST_ZONE);
        when(timeService.nowOffset()).thenReturn(fixedNow.toOffsetDateTime()); 

        // Creamos datos base en la BBDD de test
        testUser = userRepository.save(User.builder()
                .email("test@example.com")
                .nombre("Test User")
                .rol(UserRole.STUDENT)
                .build());

        adminUser = userRepository.save(User.builder()
                .email("admin@example.com")
                .nombre("Admin User")
                .rol(UserRole.ADMIN)
                .build());

        testRoom = roomRepository.save(Room.builder()
                .name("Sala de Pruebas")
                .capacity(10)
                .floor(1)
                .build());
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    // --- TESTS PARA EL MÉTODO create() ---

    @Test
    @DisplayName("create() debe crear reserva exitosamente (sin Google Calendar)")
    // --- CORRECCIÓN 3: Añadir 'throws IOException' ---
    void create_ShouldSucceed_WhenNoGoogleCalendar() throws IOException {
        // Arrange
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(
                testRoom.getId(), start, end, false
        );

        // Act
        reservationService.create(testUser.getEmail(), request);

        // Assert
        assertEquals(1, reservationRepository.count());
        Reservation res = reservationRepository.findAll().get(0);
        assertEquals(testUser.getId(), res.getUser().getId());
        assertEquals(testRoom.getId(), res.getRoom().getId());
        assertNull(res.getGoogleEventId());
        
        // Verificamos que NO se llamó a Google
        verify(googleCalendarService, never()).createEventForReservation(any(), any());
    }

    @Test
    @DisplayName("create() debe crear reserva y sincronizar con Google Calendar")
    void create_ShouldSucceed_WithGoogleCalendarSync() throws IOException {
        // Arrange
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(
                testRoom.getId(), start, end, true
        );
        
        String fakeGoogleEventId = "google-id-12345";
        // Usamos any(User.class) para el setup
        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential); 
        when(mockCredential.getAccessToken()).thenReturn("fake-token");
        when(googleCalendarService.createEventForReservation(any(Reservation.class), anyString()))
                .thenReturn(fakeGoogleEventId);

        // Act
        reservationService.create(testUser.getEmail(), request);

        // Assert
        assertEquals(1, reservationRepository.count());
        Reservation res = reservationRepository.findAll().get(0);
        
        // --- CORRECCIÓN 1: Usar any(User.class) para la verificación ---
        verify(googleCredentialsService, times(1)).getCredential(any(User.class));
        verify(googleCalendarService, times(1)).createEventForReservation(any(Reservation.class), eq("fake-token"));
        
        assertEquals(fakeGoogleEventId, res.getGoogleEventId());
    }

    // ... (El resto de tus tests 'create_ShouldFail_...' se quedan igual) ...

    @Test
    @DisplayName("create() debe fallar si las fechas son inválidas (fin antes de inicio)")
    void create_ShouldFail_WhenEndDateIsBeforeStartDate() {
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);
        Exception e = assertThrows(IllegalArgumentException.class, () -> reservationService.create(testUser.getEmail(), request));
        assertEquals("La fecha de inicio debe ser anterior a la fecha de fin.", e.getMessage());
    }
    
    @Test
    @DisplayName("create() debe fallar si la sala no existe")
    void create_ShouldFail_WhenRoomNotFound() {
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        Long nonExistentRoomId = 999L;
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(nonExistentRoomId, start, end, false);
        Exception e = assertThrows(IllegalArgumentException.class, () -> reservationService.create(testUser.getEmail(), request));
        assertTrue(e.getMessage().contains("Sala no encontrada"));
    }

    @Test
    @DisplayName("create() debe fallar si hay conflicto de horario (solapamiento exacto)")
    void create_ShouldFail_WhenTimeSlotConflicts() {
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        createTestReservation(testUser, testRoom, start, end); 
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);
        Exception e = assertThrows(IllegalStateException.class, () -> reservationService.create(testUser.getEmail(), request));
        assertTrue(e.getMessage().contains("La sala ya está reservada en ese horario"));
    }

    // --- TESTS PARA getMyReservations() ---

    @Test
    @DisplayName("getMyReservations() debe clasificar correctamente (Pasada, Actual, Futura)")
    void getMyReservations_ShouldClassifyCorrectly() {
        // Recordar: "now" está fijado a 2025-11-20 a las 10:30 UTC
        createTestReservation(testUser, testRoom, fixedNow.minusHours(2), fixedNow.minusHours(1)); // Pasada
        createTestReservation(testUser, testRoom, fixedNow.minusMinutes(30), fixedNow.plusMinutes(30)); // Actual
        createTestReservation(testUser, testRoom, fixedNow.plusHours(2), fixedNow.plusHours(3)); // Futura

        // Act
        ReservationDto.MyReservationsResponse response = reservationService.getMyReservations(testUser.getEmail());

        // Assert (Usando los métodos correctos del 'record': .current(), .past(), .future())
        assertNotNull(response.current());
        assertEquals(1, response.past().size());
        assertEquals(1, response.future().size());
        assertEquals("Sala de Pruebas", response.current().room().getName());
    }

    // --- TESTS PARA cancel() ---

    @Test
    @DisplayName("cancel() debe permitir al dueño cancelar (con Google Sync)")
    void cancel_ShouldAllowOwnerToCancel_WithGoogleSync() throws IOException {
        // Arrange
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(2));
        String fakeGoogleEventId = "google-id-to-delete";
        res.setGoogleEventId(fakeGoogleEventId);
        reservationRepository.save(res);

        // --- CORRECCIÓN 2: Usar any(User.class) para el setup ---
        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential);
        when(mockCredential.getAccessToken()).thenReturn("fake-token");
        doNothing().when(googleCalendarService).deleteEvent(anyString(), anyString());

        // Act
        reservationService.cancel(res.getId(), testUser.getEmail(), false);

        // Assert
        assertEquals(0, reservationRepository.count());
        verify(googleCalendarService, times(1)).deleteEvent(fakeGoogleEventId, "fake-token");
    }

    @Test
    @DisplayName("cancel() debe permitir al Admin cancelar")
    void cancel_ShouldAllowAdminToCancel() throws IOException { // <-- Añadido throws IOException por si acaso
        // Arrange
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(2));

        // Act
        reservationService.cancel(res.getId(), adminUser.getEmail(), true);

        // Assert
        assertEquals(0, reservationRepository.count());
    }

    @Test
    @DisplayName("cancel() debe fallar si el usuario no es dueño ni Admin")
    void cancel_ShouldFail_IfNotOwnerOrAdmin() {
        // Arrange
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(2));

        // Act & Assert
        Exception e = assertThrows(SecurityException.class, () -> {
            reservationService.cancel(res.getId(), adminUser.getEmail(), false);
        });
        
        assertEquals("No tienes permiso para cancelar esta reserva. Solo el dueño o un administrador pueden hacerlo.", e.getMessage());
        assertEquals(1, reservationRepository.count());
    }
    
    // --- (Método Helper) ---
    private Reservation createTestReservation(User user, Room room, ZonedDateTime startAt, ZonedDateTime endAt) {
        Reservation res = Reservation.builder()
                .user(user)
                .room(room)
                .startAt(startAt)
                .endAt(endAt)
                .build();
        return reservationRepository.save(res);
    }
}