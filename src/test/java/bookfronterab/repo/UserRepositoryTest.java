package bookfronterab.repo;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
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

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

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
    private UserRepository userRepository;

    @Test
    @DisplayName("findByEmail debe encontrar usuario ignorando mayúsculas/minúsculas si la BD lo permite o exacto")
    void findByEmail_ShouldReturnUser() {
        // Arrange
        User user = User.builder()
                .email("test@ufromail.cl")
                .nombre("Test User")
                .rol(UserRole.STUDENT)
                .creadoEn(OffsetDateTime.now())
                .build();
        userRepository.save(user);

        // Act
        Optional<User> found = userRepository.findByEmail("test@ufromail.cl");

        // Assert
        assertTrue(found.isPresent());
        assertEquals("Test User", found.get().getNombre());
    }

    @Test
    @DisplayName("findByEmail debe retornar empty si no existe")
    void findByEmail_ShouldReturnEmpty() {
        Optional<User> found = userRepository.findByEmail("noexiste@mail.com");
        assertTrue(found.isEmpty());
    }
}