package bookfronterab.repo;

import bookfronterab.model.Room;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomRepositoryTest {

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

    @Autowired
    private RoomRepository roomRepository;

    @Test
    @DisplayName("findByIdWithLock debe recuperar la sala correctamente aplicando el bloqueo")
    void findByIdWithLock_ShouldReturnRoom() {
        // Arrange
        Room room = roomRepository.save(Room.builder()
                .name("Sala Bloqueada")
                .capacity(10)
                .floor(1)
                .build());

        // Act
        // Al ejecutar esto, Hibernate generará un "SELECT ... FOR UPDATE" o "FOR NO KEY UPDATE"
        // Si la base de datos o el dialecto no lo soportaran, esto fallaría.
        Optional<Room> found = roomRepository.findByIdWithLock(room.getId());

        // Assert
        assertTrue(found.isPresent());
        assertEquals("Sala Bloqueada", found.get().getName());
    }

    @Test
    @DisplayName("findByIdWithLock debe retornar empty si el ID no existe")
    void findByIdWithLock_ShouldReturnEmpty_WhenNotFound() {
        Optional<Room> found = roomRepository.findByIdWithLock(9999L);
        assertTrue(found.isEmpty());
    }
}