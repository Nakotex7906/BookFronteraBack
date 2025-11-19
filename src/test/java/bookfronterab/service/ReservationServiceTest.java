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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Pruebas de integración para {@link ReservationService}.
 *
 * <p>Esta clase prueba la lógica de negocio de {@link ReservationService}
 * en un entorno de alta fidelidad. Utiliza:</p>
 * <ul>
 * <li>{@link SpringBootTest} para cargar el contexto completo de la aplicación.</li>
 * <li>{@link Testcontainers} para ejecutar pruebas contra una base de datos
 * PostgreSQL real en un contenedor Docker.</li>
 * <li>{@link MockBean} para simular dependencias externas (servicios de Google, TimeService)
 * y hacer las pruebas determinísticas.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest
class ReservationServiceTest {

    // --- Configuración de Testcontainers (BBDD real) ---

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

    // --- Mocks de Dependencias ---

    /**
     * Mock para el servicio de Google Calendar. Evita llamadas reales a la API.
     */
    @MockBean
    private GoogleCalendarService googleCalendarService;
    /**
     * Mock para el servicio de credenciales de Google. Evita llamadas reales a la API.
     */
    @MockBean
    private GoogleCredentialsService googleCredentialsService;
    /**
     * Mock para {@link TimeService}. Permite fijar la hora ("now") para
     * que los tests de clasificación (pasado, presente, futuro) sean predecibles.
     */
    @MockBean
    private TimeService timeService;
    
    /**
     * Mock para la clase {@link Credential} de Google, necesaria para
     * simular la obtención del token de acceso.
     */
    @MockBean
    private Credential mockCredential;

    // --- Inyección de Servicio (Real) y Repositorios (Reales) ---

    /**
     * El servicio bajo prueba (System Under Test). Se inyecta la instancia real
     * gestionada por Spring.
     */
    @Autowired
    private ReservationService reservationService;

    /**
     * Repositorio real para gestionar entidades {@link User}. Se usa para
     * crear usuarios de prueba en el {@code setUp}.
     */
    @Autowired
    private UserRepository userRepository;
    /**
     * Repositorio real para gestionar entidades {@link Room}. Se usa para
     * crear salas de prueba en el {@code setUp}.
     */
    @Autowired
    private RoomRepository roomRepository;
    /**
     * Repositorio real para gestionar entidades {@link Reservation}. Se usa para
     * crear reservas de prueba y verificar los resultados.
     */
    @Autowired
    private ReservationRepository reservationRepository;

    // --- Datos de Prueba ---
    private User testUser;
    private User adminUser;
    private Room testRoom;
    private static final ZoneId TEST_ZONE = ZoneId.of("UTC");
    private ZonedDateTime fixedNow;

    /**
     * Configuración ejecutada ANTES de CADA test (@Test).
     * <p>Se encarga de:</p>
     * <ol>
     * <li>Limpiar todas las tablas de la BBDD para asegurar aislamiento.</li>
     * <li>Configurar el mock de {@link TimeService} para que devuelva una fecha fija.</li>
     * <li>Crear y guardar entidades (User, Room) de prueba en la BBDD.</li>
     * </ol>
     */
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

    // --- TESTS PARA EL MÉTODO create() ---

    /**
     * Prueba el "camino feliz" de creación de una reserva cuando el usuario
     * no solicita la sincronización con Google Calendar.
     * Verifica que la reserva se guarda localmente y que no se
     * interactúa con los servicios de Google.
     */
    @Test
    @DisplayName("create() debe crear reserva exitosamente (sin Google Calendar)")
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

    /**
     * Prueba el "camino feliz" cuando el usuario SÍ solicita la
     * sincronización con Google Calendar.
     * Verifica que los mocks de Google son llamados y que el
     * ID del evento de Google se guarda en la reserva local.
     */
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
        // Configuramos los mocks para simular una llamada exitosa a Google
        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential); 
        when(mockCredential.getAccessToken()).thenReturn("fake-token");
        when(googleCalendarService.createEventForReservation(any(Reservation.class), anyString()))
                .thenReturn(fakeGoogleEventId);

        // Act
        reservationService.create(testUser.getEmail(), request);

        // Assert
        assertEquals(1, reservationRepository.count());
        Reservation res = reservationRepository.findAll().get(0);
        
        // Verificamos que los servicios de Google fueron llamados correctamente
        verify(googleCredentialsService, times(1)).getCredential(any(User.class));
        verify(googleCalendarService, times(1)).createEventForReservation(any(Reservation.class), eq("fake-token"));
        
        // Verificamos que el ID de Google se guardó en la BBDD
        assertEquals(fakeGoogleEventId, res.getGoogleEventId());
    }

    /**
     * Prueba la validación de entrada: la fecha de fin no puede ser
     * anterior a la fecha de inicio.
     */
    @Test
    @DisplayName("create() debe fallar si las fechas son inválidas (fin antes de inicio)")
    void create_ShouldFail_WhenEndDateIsBeforeStartDate() {
        // Arrange
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE); // 11:00
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);   // 10:00
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);
        
        // Act & Assert
        Exception e = assertThrows(IllegalArgumentException.class, () -> reservationService.create(testUser.getEmail(), request));
        assertEquals("La fecha de inicio debe ser anterior a la fecha de fin.", e.getMessage());
    }
    
    /**
     * Prueba la validación de existencia: la sala debe existir en la BBDD.
     */
    @Test
    @DisplayName("create() debe fallar si la sala no existe")
    void create_ShouldFail_WhenRoomNotFound() {
        // Arrange
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        Long nonExistentRoomId = 999L;
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(nonExistentRoomId, start, end, false);
        
        // Act & Assert
        Exception e = assertThrows(IllegalArgumentException.class, () -> reservationService.create(testUser.getEmail(), request));
        assertTrue(e.getMessage().contains("Sala no encontrada"));
    }

    /**
     * Prueba la lógica de negocio de detección de conflictos.
     * Verifica que el servicio (y la consulta
     * {@code findConflictingReservations}) detecta un solapamiento
     * de horario en la misma sala.
     */
    @Test
    @DisplayName("create() debe fallar si hay conflicto de horario (solapamiento exacto)")
    void create_ShouldFail_WhenTimeSlotConflicts() {
        // Arrange
        // 1. Creamos una reserva existente en la BBDD
        ZonedDateTime start = ZonedDateTime.of(2025, 11, 21, 10, 0, 0, 0, TEST_ZONE);
        ZonedDateTime end = ZonedDateTime.of(2025, 11, 21, 11, 0, 0, 0, TEST_ZONE);
        createTestReservation(testUser, testRoom, start, end); 
        
        // 2. Creamos una petición para el mismo horario y misma sala
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(testRoom.getId(), start, end, false);
        
        // Act & Assert
        Exception e = assertThrows(IllegalStateException.class, () -> {
            reservationService.create(testUser.getEmail(), request); // Intentamos crear la segunda
        });
        assertTrue(e.getMessage().contains("La sala ya está reservada en ese horario"));
    }

    // --- TESTS PARA getMyReservations() ---

    /**
     * Prueba la lógica de clasificación de reservas (pasada, actual, futura)
     * usando la hora fija ("now") proveída por el mock de {@link TimeService}.
     */
    @Test
    @DisplayName("getMyReservations() debe clasificar correctamente (Pasada, Actual, Futura)")
    void getMyReservations_ShouldClassifyCorrectly() {
        // Arrange
        // Recordar: "now" está fijado a 2025-11-20 a las 10:30 UTC
        
        // 1. Reserva Pasada (terminó a las 09:30)
        createTestReservation(testUser, testRoom, fixedNow.minusHours(2), fixedNow.minusHours(1));
        
        // 2. Reserva Actual (ocurre de 10:00 a 11:00, "now" está en medio)
        createTestReservation(testUser, testRoom, fixedNow.minusMinutes(30), fixedNow.plusMinutes(30));
        
        // 3. Reserva Futura (empieza a las 12:30)
        createTestReservation(testUser, testRoom, fixedNow.plusHours(2), fixedNow.plusHours(3));

        // Act
        ReservationDto.MyReservationsResponse response = reservationService.getMyReservations(testUser.getEmail());

        // Assert
        assertNotNull(response.current(), "Debe existir una reserva actual");
        assertEquals(1, response.past().size(), "Debe haber una reserva pasada");
        assertEquals(1, response.future().size(), "Debe haber una reserva futura");
        assertEquals("Sala de Pruebas", response.current().room().getName());
    }

    // --- TESTS PARA cancel() ---

    /**
     * Prueba la cancelación por parte del dueño de la reserva.
     * También verifica que se intenta cancelar el evento de Google Calendar
     * asociado (si existe).
     */
    @Test
    @DisplayName("cancel() debe permitir al dueño cancelar (con Google Sync)")
    void cancel_ShouldAllowOwnerToCancel_WithGoogleSync() throws IOException {
        // Arrange
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(2));
        String fakeGoogleEventId = "google-id-to-delete";
        res.setGoogleEventId(fakeGoogleEventId);
        reservationRepository.save(res); // Guardamos la reserva con el ID de Google

        // Configuramos los mocks de Google para la cancelación
        when(googleCredentialsService.getCredential(any(User.class))).thenReturn(mockCredential);
        when(mockCredential.getAccessToken()).thenReturn("fake-token");
        doNothing().when(googleCalendarService).deleteEvent(anyString(), anyString());

        // Act
        // El dueño (testUser) cancela su propia reserva
        reservationService.cancel(res.getId(), testUser.getEmail());

        // Assert
        assertEquals(0, reservationRepository.count(), "La reserva debió ser borrada de la BBDD");
        // Verificamos que se llamó a la API de Google para borrar el evento
        verify(googleCalendarService, times(1)).deleteEvent(fakeGoogleEventId, "fake-token");
    }

    /**
     * Prueba la lógica de permisos: un administrador puede cancelar
     * la reserva de otro usuario.
     */
    @Test
    @DisplayName("cancel() debe permitir al Admin cancelar")
    void cancel_ShouldAllowAdminToCancel() throws IOException {
        // Arrange
        // La reserva pertenece a 'testUser'
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(2));

        // Act
        // 'adminUser' (marcado como isAdmin=true) cancela la reserva
        reservationService.cancel(res.getId(), adminUser.getEmail());

        // Assert
        assertEquals(0, reservationRepository.count(), "El admin debió poder borrar la reserva");
    }

    /**
     * Prueba la lógica de seguridad: un usuario que no es ni el dueño
     * ni un administrador no puede cancelar la reserva.
     */
    @Test
    @DisplayName("cancel() debe fallar si el usuario no es dueño ni Admin")
    void cancel_ShouldFail_IfNotOwnerOrAdmin() {
        // Arrange
        // La reserva pertenece a 'testUser'
        Reservation res = createTestReservation(testUser, testRoom, fixedNow.plusDays(1), fixedNow.plusDays(2));

        // Act & Assert
        // 'adminUser' (pero con isAdmin=false) intenta cancelar la reserva
        Exception e = assertThrows(SecurityException.class, () -> {
            reservationService.cancel(res.getId(), adminUser.getEmail());
        });
        
        assertEquals("No tienes permiso para cancelar esta reserva. Solo el dueño o un administrador pueden hacerlo.", e.getMessage());
        // Verificamos que la reserva NO se borró
        assertEquals(1, reservationRepository.count());
    }
    
    // --- (Método Helper) ---

    /**
     * Método de ayuda privado para crear y guardar una {@link Reservation}
     * con los datos mínimos requeridos para los tests.
     *
     * @param user El usuario que reserva.
     * @param room La sala reservada.
     * @param startAt La fecha/hora de inicio.
     * @param endAt La fecha/hora de fin.
     * @return La entidad Reservation persistida.
     */
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