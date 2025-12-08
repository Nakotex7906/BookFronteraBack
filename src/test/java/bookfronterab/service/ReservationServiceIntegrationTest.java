package bookfronterab.service;

import bookfronterab.dto.ReservationDto;
import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.ReservationRepository;
import bookfronterab.repo.RoomRepository;
import bookfronterab.repo.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Habilita el uso de Testcontainers en la clase.
@Testcontainers
// Indica a Spring Boot que cargue el contexto completo de la aplicación para las pruebas de integración.
@SpringBootTest
class ReservationServiceIntegrationTest {

    /**
     * Define y gestiona el contenedor Docker de PostgreSQL.
     * Se inicia antes de las pruebas de la clase y se destruye al finalizar.
     */
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("bookfronterab-test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Sobreescribe dinámicamente las propiedades de la fuente de datos de Spring Boot
     * para apuntar al contenedor de PostgreSQL creado por Testcontainers.
     * Esto aísla la prueba de la base de datos de desarrollo/producción.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        // Asegura que Spring use el dialecto correcto y que la DDL se cree en cada ejecución.
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    // Inyección de dependencias (el servicio bajo prueba y los repositorios para la configuración/verificación).
    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;

    // Variable estática para definir el tiempo de inicio de las reservas de prueba.
    static ZonedDateTime start;

    /**
     * Configuración ejecutada una vez antes de todas las pruebas.
     * Inicializa la hora de inicio de las reservas (una semana en el futuro).
     */
    @BeforeAll
    static void setUpAll() {
        start  = ZonedDateTime.now().plusWeeks(1);
    }

    /**
     * Configuración ejecutada antes de cada prueba.
     * Limpia la base de datos de prueba para asegurar que cada test sea independiente.
     */
    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    /**
     * Prueba de integración para el método createOnBehalf().
     * Verifica que una reserva se pueda crear exitosamente en nombre de otro usuario.
     */
    @Test
    @DisplayName("createOnBehalf debería crear una reserva en nombre de otra persona")
    // Ejecuta la prueba dentro de una transacción para asegurar la reversión de la base de datos.
    @Transactional
    void createOnBehalf_ShouldSaveAReservation(){
        // 1. Configuración (Arrange)
        ReservationDto.CreateRequest req = new ReservationDto.CreateRequest(
                1L, // ID de sala (se usará el de la sala persistida)
                start,
                start.plusHours(1),
                false);

        // Usuarios y Sala de prueba
        User user = new User(null,
                "admin@example.com", // Usuario que realiza la petición
                "root",
                UserRole.ADMIN,
                ZonedDateTime.now().toOffsetDateTime(),
                null, null, null);
        User other = new User(null,
                "john.doe@example.com", // Usuario para quien se hace la reserva
                "john doe",
                UserRole.STUDENT,
                ZonedDateTime.now().toOffsetDateTime(),
                null, null, null);
        Room room = new Room(null,"test",4,new ArrayList<>(),1,"");

        // Persistir entidades en la DB real.
        roomRepository.save(room);
        userRepository.save(user);
        userRepository.save(other);
        
        // 2. Ejecución (Act)
        // El ADMIN crea la reserva (user.getEmail()) para el STUDENT (other.getEmail()).
        reservationService.createOnBehalf(user.getEmail(), other.getEmail(), req);
        
        // 3. Verificación (Assert)
        // Recuperar la reserva de la base de datos.
        Reservation response = reservationRepository.findAll().getFirst();
        
        // Se verifica que el usuario asociado a la reserva sea el 'other' (el STUDENT), 
        // y no el 'user' (el ADMIN) que ejecutó la acción.
        assertEquals("john.doe@example.com", response.getUser().getEmail(), 
                     "La reserva debe estar asociada al usuario 'other'.");
    }
}