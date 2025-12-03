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

@Testcontainers
@SpringBootTest
class ReservationServiceIntegrationTest {

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

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoomRepository roomRepository;
    static ZonedDateTime start;

    @BeforeAll
    static void setUpAll() {
        start  = ZonedDateTime.now().plusWeeks(1);
    }
    @BeforeEach
    void setUp() {
        reservationRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("createOnBehalf debería crear una reserva en nombre de otra persona")
    @Transactional
    void createOnBehalf_ShouldSaveAReservation(){
        ReservationDto.CreateRequest req = new ReservationDto.CreateRequest(
                1L,
                start,
                start.plusHours(1),
                false);
        User user = new User(null,
                "admin@example.com",
                "root",
                UserRole.ADMIN,
                ZonedDateTime.now().toOffsetDateTime(),
                null,
                null,
                null);
        User other = new User(null,
                "john.doe@example.com",
                "john doe",
                UserRole.STUDENT,
                ZonedDateTime.now().toOffsetDateTime(),
                null,
                null,
                null);
        Room room = new Room(null,"test",4,new ArrayList<>(),1,"");
        roomRepository.save(room);
        userRepository.save(user);
        userRepository.save(other);
        reservationService.createOnBehalf(user.getEmail(), other.getEmail(),req);
        Reservation response=reservationRepository.findAll().getFirst();
        System.out.println(response.getId()+ ", "+response.getStartAt()+", "+response.getEndAt()+", "+response.getUser());
        assertEquals("john.doe@example.com", response.getUser().getEmail());
    }
}
