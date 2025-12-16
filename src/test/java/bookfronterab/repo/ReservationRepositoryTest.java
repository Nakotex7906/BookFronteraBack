package bookfronterab.repo;

import bookfronterab.model.Reservation;
import bookfronterab.model.Room;
import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationRepositoryTest {

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
    }

    @Autowired private ReservationRepository reservationRepository;
    @Autowired private RoomRepository roomRepository;
    @Autowired private UserRepository userRepository;

    private Room roomA;
    private User student;
    private ZonedDateTime baseTime;

    @BeforeEach
    void setUp() {
        // Limpieza previa
        reservationRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        // Datos base
        roomA = roomRepository.save(Room.builder().name("Sala A").capacity(5).floor(1).build());
        student = userRepository.save(User.builder().email("student@ufromail.cl").nombre("Student").rol(UserRole.STUDENT).build());

        // Referencia: Lunes 10:00 AM
        baseTime = ZonedDateTime.now().withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    // --- TEST: findConflictingReservations ---

    @Test
    @DisplayName("Debe detectar conflicto si la nueva reserva solapa exactamente a una existente")
    void findConflicting_ExactMatch() {
        // Existe: 10:00 - 11:00
        persistReservation(baseTime, baseTime.plusHours(1));

        // Intento: 10:00 - 11:00
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                roomA.getId(), baseTime, baseTime.plusHours(1)
        );

        assertThat(conflicts).isNotEmpty();
    }

    @Test
    @DisplayName("Debe detectar conflicto si la nueva reserva encierra a una existente")
    void findConflicting_Enclosing() {
        // Existe: 10:30 - 11:00
        persistReservation(baseTime.plusMinutes(30), baseTime.plusHours(1));

        // Intento: 10:00 - 11:30 (Engloba a la existente)
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                roomA.getId(), baseTime, baseTime.plusMinutes(90)
        );

        assertThat(conflicts).isNotEmpty();
    }

    @Test
    @DisplayName("Debe detectar conflicto si la nueva reserva solapa parcialmente el inicio")
    void findConflicting_PartialStart() {
        // Existe: 10:00 - 11:00
        persistReservation(baseTime, baseTime.plusHours(1));

        // Intento: 09:30 - 10:30 (Choca con la primera mitad)
        List<Reservation> conflicts = reservationRepository.findConflictingReservations(
                roomA.getId(), baseTime.minusMinutes(30), baseTime.plusMinutes(30)
        );

        assertThat(conflicts).isNotEmpty();
    }

    @Test
    @DisplayName("NO debe haber conflicto si las reservas son adyacentes (termina justo cuando empieza)")
    void findConflicting_Adjacent_NoConflict() {
        // Existe: 10:00 - 11:00
        persistReservation(baseTime, baseTime.plusHours(1));

        // Intento 1: 11:00 - 12:00 (Empieza justo al terminar la otra)
        List<Reservation> conflictsAfter = reservationRepository.findConflictingReservations(
                roomA.getId(), baseTime.plusHours(1), baseTime.plusHours(2)
        );

        // Intento 2: 09:00 - 10:00 (Termina justo al empezar la otra)
        List<Reservation> conflictsBefore = reservationRepository.findConflictingReservations(
                roomA.getId(), baseTime.minusHours(1), baseTime
        );

        assertThat(conflictsAfter).isEmpty();
        assertThat(conflictsBefore).isEmpty();
    }

    // --- TEST: countByUserEmailAndStartAtBetweenAndIdNot ---

    @Test
    @DisplayName("Debe contar reservas en el rango excluyendo el ID especificado (para ediciones)")
    void countExcludingId() {
        ZonedDateTime start = baseTime;
        ZonedDateTime end = baseTime.plusHours(1);

        // Creamos 2 reservas del mismo usuario
        Reservation r1 = persistReservation(start, end);

        // Rango de búsqueda: toda la semana
        ZonedDateTime weekStart = baseTime.minusDays(1);
        ZonedDateTime weekEnd = baseTime.plusDays(5);

        // Caso 1: Contar todo (sin excluir nada relevante o excluyendo un ID random) -> Debería haber 2
        long countTotal = reservationRepository.countByUserEmailAndStartAtBetweenAndIdNot(
                student.getEmail(), weekStart, weekEnd, 999L
        );
        assertThat(countTotal).isEqualTo(2);

        // Caso 2: Contar excluyendo r1 (simulando que estoy editando r1) -> Debería ver solo r2 (1)
        long countExcludingR1 = reservationRepository.countByUserEmailAndStartAtBetweenAndIdNot(
                student.getEmail(), weekStart, weekEnd, r1.getId()
        );
        assertThat(countExcludingR1).isEqualTo(1);
    }

    private Reservation persistReservation(ZonedDateTime start, ZonedDateTime end) {
        return reservationRepository.save(Reservation.builder()
                .room(roomA)
                .user(student)
                .startAt(start)
                .endAt(end)
                .build());
    }
}