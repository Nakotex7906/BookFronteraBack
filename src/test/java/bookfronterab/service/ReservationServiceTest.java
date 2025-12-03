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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional; // IMPORTANTE
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest
@Transactional // CORRECCIÓN 1: Mantiene la sesión de Hibernate abierta para los asserts
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

    // Mocks de servicios externos
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
    private static final ZoneId TEST_ZONE = ZoneId.of("America/Santiago");

    private ZonedDateTime nextMonday;

    @BeforeEach
    void setUp() {
        // CORRECCIÓN 2: Configurar el mockCredential para que devuelva un token falso
        // Esto evita que getAccessToken() devuelva null y rompa los tests de Google.
        when(mockCredential.getAccessToken()).thenReturn("mock-token-abc-123");

        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Configuración de fecha y mocks de tiempo
        ZonedDateTime now = ZonedDateTime.now(TEST_ZONE);
        nextMonday = now.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .withHour(10).withMinute(0).withSecond(0).withNano(0);

        when(timeService.nowOffset()).thenReturn(nextMonday.toOffsetDateTime());
        when(timeService.zone()).thenReturn(TEST_ZONE);

        // Crear datos base
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

    // =================================================================================================
    // TESTS: Creación Básica y Validaciones de Negocio
    // =================================================================================================

    @Test
    @DisplayName("create() debe crear reserva exitosamente")
    void create_ShouldSucceed_WhenNoGoogleCalendar() throws IOException {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);

        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, false);

        reservationService.create(testUser.getEmail(), request);

        assertEquals(1, reservationRepository.count());
        verify(googleCalendarService, never()).createEventForReservation(any(), any());
    }

    @Test
    @DisplayName("create() debe fallar si hay conflicto de horario")
    void create_ShouldFail_WhenTimeSlotConflicts() {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);
        createTestReservation(testUser, testRoom, start, end);

        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, false);
        String emailTestUser = testUser.getEmail();
        assertThrows(IllegalStateException.class, () -> reservationService.create(emailTestUser, request));
    }

    @Test
    @DisplayName("create() debe fallar si el estudiante excede el límite semanal")
    void create_ShouldFail_WhenWeeklyLimitExceeded() {
        ZonedDateTime mondayStart = nextMonday;
        createTestReservation(testUser, testRoom, mondayStart, mondayStart.plusHours(1));

        ZonedDateTime fridayStart = nextMonday.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), fridayStart, fridayStart.plusHours(1), false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                reservationService.create(testUser.getEmail(), request));

        assertTrue(ex.getMessage().contains("Límite alcanzado"));
    }

    @Test
    @DisplayName("create() debe permitir al ADMIN exceder el límite semanal")
    void create_ShouldSucceed_WhenWeeklyLimitExceeded_IfAdmin() {
        ZonedDateTime mondayStart = nextMonday;
        createTestReservation(adminUser, testRoom, mondayStart, mondayStart.plusHours(1));

        ZonedDateTime fridayStart = nextMonday.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), fridayStart, fridayStart.plusHours(1), false);

        assertDoesNotThrow(() -> reservationService.create(adminUser.getEmail(), request));
        assertEquals(2, reservationRepository.findAll().size());
    }

    // =================================================================================================
    // TESTS: Validaciones de Entrada (Excepciones)
    // =================================================================================================

    @Test
    @DisplayName("create() debe fallar si la sala no existe")
    void create_ShouldFail_WhenRoomNotFound() {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);
        
        // CORRECCIÓN 3: Eliminado el 'when(roomRepo...)' incorrecto. 
        // Simplemente usamos un ID inexistente (999L) y el repo real devolverá vacío.
        
        ReservationDto.CreateRequest request = createValidRequest(999L, start, end, false);
        String emailTestUser = testUser.getEmail();
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.create(emailTestUser, request));
    }

    @Test
    @DisplayName("create() debe fallar si el usuario autenticado no se encuentra")
    void create_ShouldFail_WhenUserNotFound() {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, false);

        assertThrows(IllegalStateException.class, () -> 
                reservationService.create("nonexistent@ufromail.cl", request));
    }

    @Test
    @DisplayName("create() debe fallar si las fechas son nulas")
    void create_ShouldFail_WhenStartOrEndIsNull() {
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), nextMonday, null, false);
        
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.create(testUser.getEmail(), request));
    }

    @Test
    @DisplayName("create() debe fallar si la fecha de inicio es posterior a la fecha de fin")
    void create_ShouldFail_WhenStartIsAfterEnd() {
        ZonedDateTime start = nextMonday.plusHours(1);
        ZonedDateTime end = nextMonday;
        
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, false);
        
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.create(testUser.getEmail(), request));
    }
    
    @Test
    @DisplayName("create() debe fallar si la fecha de fin es en el pasado")
    void create_ShouldFail_WhenEndIsInThePast() {
        ZonedDateTime pastStart = nextMonday.minusDays(1); 
        ZonedDateTime pastEnd = nextMonday.minusMinutes(30); 
        
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), pastStart, pastEnd, false);
        String emailTestUser = testUser.getEmail();
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.create(emailTestUser, request));
    }

    @Test
    @DisplayName("create() debe fallar si la duración es menor a 15 minutos")
    void create_ShouldFail_WhenDurationIsTooShort() {
        ReservationDto.CreateRequest request = createMinuteRequest(testRoom.getId(), 10);
        String emailTestUser = testUser.getEmail();
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.create(emailTestUser, request));
    }

    @Test
    @DisplayName("create() debe fallar si la duración es mayor a 60 minutos")
    void create_ShouldFail_WhenDurationIsTooLong() {
        ReservationDto.CreateRequest request = createMinuteRequest(testRoom.getId(), 90);
        String emailTestUser = testUser.getEmail();
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.create(emailTestUser, request));
    }

    @Test
    @DisplayName("create() debe fallar si la fecha es más de 3 meses en el futuro")
    void create_ShouldFail_WhenTooFarInTheFuture() {
        ZonedDateTime farFutureStart = nextMonday.plusMonths(4);
        ZonedDateTime farFutureEnd = farFutureStart.plusHours(1);
        
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), farFutureStart, farFutureEnd, false);
        String emailTestUser = testUser.getEmail();
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.create(emailTestUser, request));
    }

    // =================================================================================================
    // TESTS: Integración con Google Calendar
    // =================================================================================================

    @Test
    @DisplayName("create() debe sincronizar con Google Calendar y guardar el ID del evento")
    void create_ShouldSucceedAndSync_WhenGoogleCalendarRequested() throws IOException {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);
        String mockGoogleId = "mock-event-123";

        // Configurar mocks de Google
        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential);
        // NOTA: mockCredential.getAccessToken() ya devuelve string gracias al setUp()
        when(googleCalendarService.createEventForReservation(any(Reservation.class), anyString())).thenReturn(mockGoogleId);

        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, true);

        reservationService.create(testUser.getEmail(), request);

        // Verificar
        verify(googleCalendarService).createEventForReservation(any(Reservation.class), anyString());
        Reservation savedRes = reservationRepository.findAll().get(0);
        assertEquals(mockGoogleId, savedRes.getGoogleEventId());
    }

    @Test
    @DisplayName("create() debe crear reserva localmente si la sincronización con Google falla")
    void create_ShouldSucceed_WhenGoogleCalendarSyncFails() throws IOException {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);

        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential);
        when(googleCalendarService.createEventForReservation(any(Reservation.class), anyString()))
                .thenThrow(new IOException("Google API error"));

        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, true);

        assertDoesNotThrow(() -> reservationService.create(testUser.getEmail(), request));

        assertEquals(1, reservationRepository.count());
        Reservation savedRes = reservationRepository.findAll().get(0);
        assertNull(savedRes.getGoogleEventId());
    }

    // =================================================================================================
    // TESTS: createOnBehalf (Reservar para otro)
    // =================================================================================================

    @Test
    @DisplayName("createOnBehalf() debe crear reserva exitosamente en nombre de otro")
    void createOnBehalf_ShouldSucceed() {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, false);

        reservationService.createOnBehalf(adminUser.getEmail(), otherUser.getEmail(), request);

        assertEquals(1, reservationRepository.count());
        Reservation savedRes = reservationRepository.findAll().get(0);
        // Esto ahora funciona gracias a @Transactional
        assertEquals(otherUser.getEmail(), savedRes.getUser().getEmail());
    }

    @Test
    @DisplayName("createOnBehalf() debe fallar si el usuario 'otro' no se encuentra")
    void createOnBehalf_ShouldFail_IfOtherUserNotFound() {
        ZonedDateTime start = nextMonday;
        ZonedDateTime end = nextMonday.plusHours(1);
        ReservationDto.CreateRequest request = createValidRequest(testRoom.getId(), start, end, false);
        String emailAdmin = adminUser.getEmail();

        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.createOnBehalf(emailAdmin, "other@notfound.cl", request));
    }

    // =================================================================================================
    // TESTS: cancel() (Cancelación)
    // =================================================================================================

    @Test
    @DisplayName("cancel() debe fallar si la reserva no se encuentra")
    void cancel_ShouldFail_WhenReservationNotFound() {
        String emailTestUser = testUser.getEmail();
        assertThrows(IllegalArgumentException.class, () -> 
                reservationService.cancel(999L, emailTestUser));
    }

    @Test
    @DisplayName("cancel() debe fallar si el usuario no es dueño ni Admin")
    void cancel_ShouldFail_IfNotOwnerOrAdmin() {
        Reservation res = createTestReservation(testUser, testRoom, nextMonday, nextMonday.plusHours(1));
        Long resId = res.getId();
        String emailOtherUser = otherUser.getEmail();

        assertThrows(SecurityException.class, () ->
                reservationService.cancel(resId, emailOtherUser));
    }

    @Test
    @DisplayName("cancel() debe permitir al Admin cancelar cualquier reserva")
    void cancel_ShouldAllowAdminToCancel() {
        Reservation res = createTestReservation(testUser, testRoom, nextMonday, nextMonday.plusHours(1));

        reservationService.cancel(res.getId(), adminUser.getEmail());

        assertEquals(0, reservationRepository.count());
    }
    
    @Test
    @DisplayName("cancel() debe fallar si el usuario solicitante no se encuentra")
    void cancel_ShouldFail_WhenRequestingUserNotFound() {
        Reservation res = createTestReservation(testUser, testRoom, nextMonday, nextMonday.plusHours(1));
        Long resId = res.getId();
        assertThrows(IllegalStateException.class, () -> 
                reservationService.cancel(resId, "nonexistent@ufromail.cl"));
    }

    @Test
    @DisplayName("cancel() debe ser exitoso y borrar de Google cuando existe ID")
    void cancel_ShouldSucceed_WhenOwnerCancelsWithGoogle() throws IOException {
        String mockGoogleId = "g-id-to-delete";
        Reservation res = createReservationWithGoogleId(testUser, testRoom, nextMonday, nextMonday.plusHours(1), mockGoogleId);
        
        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential);
        doNothing().when(googleCalendarService).deleteEvent(eq(mockGoogleId), anyString());

        reservationService.cancel(res.getId(), testUser.getEmail());

        assertEquals(0, reservationRepository.count());
        // Ahora funciona porque anyString() matchea con nuestro token mockeado
        verify(googleCalendarService).deleteEvent(eq(mockGoogleId), anyString());
    }

    @Test
    @DisplayName("cancel() debe borrar localmente si falla el borrado de Google Calendar")
    void cancel_ShouldSucceed_WhenGoogleDeleteFails() throws IOException {
        String mockGoogleId = "g-id-to-fail";
        Reservation res = createReservationWithGoogleId(testUser, testRoom, nextMonday, nextMonday.plusHours(1), mockGoogleId);
        
        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential);
        doThrow(new IOException("Fallo Google")).when(googleCalendarService).deleteEvent(eq(mockGoogleId), anyString());

        assertDoesNotThrow(() -> reservationService.cancel(res.getId(), testUser.getEmail()));

        assertEquals(0, reservationRepository.count());
    }

    // =================================================================================================
    // TESTS: MÉTODOS DE CONSULTA
    // =================================================================================================

    @Test
    @DisplayName("getById() debe retornar el DTO cuando la reserva es encontrada")
    void getById_ShouldSucceed() {
        Reservation res = createTestReservation(testUser, testRoom, nextMonday, nextMonday.plusHours(1));
        
        ReservationDto.Detail dto = reservationService.getById(res.getId());
        
        assertEquals(res.getId(), dto.id());
    }

    @Test
    @DisplayName("getById() debe fallar cuando la reserva no es encontrada")
    void getById_ShouldFail_WhenNotFound() {
        assertThrows(IllegalArgumentException.class, () -> reservationService.getById(999L));
    }

    @Test
    @DisplayName("getReservationsByRoom() debe retornar lista si el usuario es Admin")
    void getReservationsByRoom_ShouldSucceed_IfAdmin() {
        createTestReservation(testUser, testRoom, nextMonday, nextMonday.plusHours(1));
        
        List<ReservationDto.Detail> result = reservationService.getReservationsByRoom(testRoom.getId(), adminUser.getEmail());
        
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getReservationsByRoom() debe fallar si el usuario es estudiante")
    void getReservationsByRoom_ShouldFail_IfNonAdmin() {
        Long roomId = testRoom.getId();
        String email = testUser.getEmail();
        assertThrows(SecurityException.class, () -> 
                reservationService.getReservationsByRoom(roomId, email));
    }
    @Test
    @DisplayName("getMyReservations() debe clasificar correctamente: Pasada, Actual y Futura")
    void getMyReservations_ShouldClassifyReservationsCorrectly() {
        // 1. Definimos un "AHORA" fijo (Lunes 12:00 PM)
        ZonedDateTime now = nextMonday.withHour(12).withMinute(0);
        when(timeService.nowOffset()).thenReturn(now.toOffsetDateTime());

        // 2. Crear Reservas
        // PASADA (10:00 - 11:00)
        createTestReservation(testUser, testRoom, now.minusHours(2), now.minusHours(1));

        // ACTUAL (11:30 - 12:30) - Cruza el "ahora"
        Reservation currentRes = createTestReservation(testUser, testRoom, now.minusMinutes(30), now.plusMinutes(30));

        // FUTURA (13:00 - 14:00)
        createTestReservation(testUser, testRoom, now.plusHours(1), now.plusHours(2));

        // 3. Ejecutar servicio
        ReservationDto.MyReservationsResponse response = reservationService.getMyReservations(testUser.getEmail());

        // 4. Verificar usando los accesores correctos del RECORD (.current(), .future(), .past())
        
        // Verificar Actual
        assertNotNull(response.current(), "Debería existir una reserva actual");
        assertEquals(currentRes.getId(), response.current().id());

        // Verificar Futura
        assertEquals(1, response.future().size(), "Debería haber 1 reserva futura");
        
        // Verificar Pasada
        assertEquals(1, response.past().size(), "Debería haber 1 reserva pasada");
    }

    @Test
    @DisplayName("getMyReservations() debe retornar listas vacías si el usuario no tiene reservas")
    void getMyReservations_ShouldHandleNoReservations() {
        // Ejecutar sin crear reservas
        ReservationDto.MyReservationsResponse response = reservationService.getMyReservations(testUser.getEmail());

        // Verificar nulos y vacíos
        assertNull(response.current(), "No debería haber reserva actual");
        assertTrue(response.future().isEmpty(), "La lista futura debería estar vacía");
        assertTrue(response.past().isEmpty(), "La lista pasada debería estar vacía");
    }


    // =================================================================================================
    // MÉTODOS AUXILIARES (HELPERS)
    // =================================================================================================

    private Reservation createTestReservation(User user, Room room, ZonedDateTime startAt, ZonedDateTime endAt) {
        return reservationRepository.save(Reservation.builder()
                .user(user).room(room).startAt(startAt).endAt(endAt).build());
    }

    private ReservationDto.CreateRequest createValidRequest(Long roomId, ZonedDateTime start, ZonedDateTime end, boolean addToGoogle) {
        return new ReservationDto.CreateRequest(roomId, start, end, addToGoogle);
    }

    private ReservationDto.CreateRequest createMinuteRequest(Long roomId, int minutes) {
        ZonedDateTime start = nextMonday.withHour(11);
        ZonedDateTime end = start.plusMinutes(minutes);
        return new ReservationDto.CreateRequest(roomId, start, end, false);
    }

    private Reservation createReservationWithGoogleId(User user, Room room, ZonedDateTime startAt, ZonedDateTime endAt, String googleId) {
        return reservationRepository.save(Reservation.builder()
                .user(user).room(room).startAt(startAt).endAt(endAt)
                .googleEventId(googleId).build());
    }
}