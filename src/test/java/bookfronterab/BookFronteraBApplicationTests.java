package bookfronterab;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Prueba de integración principal para la aplicación {@link BookFronteraBApplication}.
 *
 * <p>Esta clase utiliza {@link SpringBootTest} para cargar el contexto completo
 * de la aplicación Spring Boot.
 *
 * <p>Emplea {@link Testcontainers} para iniciar un contenedor de base de datos
 * PostgreSQL real y efímero. Esto asegura que la aplicación se prueba
 * contra una base de datos de alta fidelidad, validando la configuración
 * del DataSource, las entidades de JPA y la conectividad general.</p>
 */
@Testcontainers
@SpringBootTest
class BookFronteraBApplicationTests {

    /**
     * Define y configura el contenedor Docker de PostgreSQL que se
     * iniciará antes de que se ejecuten las pruebas.
     *
     * <p>Al ser {@code static}, el contenedor se inicia una sola vez y se
     * comparte entre todos los métodos de prueba de esta clase, mejorando
     * el rendimiento de la suite de pruebas.</p>
     */
    @Container
    public static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("bookfronterab-test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Configura dinámicamente las propiedades del {@code DataSource} de Spring
     * ANTES de que se inicie el contexto de la aplicación.
     *
     * <p>Este método intercepta el arranque de Spring y sobrescribe las
     * propiedades de la base de datos (URL, usuario, contraseña) con los
     * valores dinámicos generados por el {@link PostgreSQLContainer}
     * (como la URL y el puerto aleatorio asignado por Docker).</p>
     *
     * <p>También establece {@code ddl-auto} en {@code create}, asegurando que el
     * esquema de la base de datos se cree desde cero para cada ejecución de prueba.</p>
     *
     * @param registry El registro de propiedades dinámicas de Spring.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
        registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    /**
     * Prueba de "humo" (smoke test) estándar de Spring Boot.
     *
     * <p>El propósito de esta prueba es simple: verificar que el
     * {@link org.springframework.context.ApplicationContext} de Spring
     * se carga exitosamente sin lanzar excepciones.</p>
     *
     * <p>Si este test pasa, confirma que la inyección de dependencias,
     * la configuración de la aplicación y la conexión a la base de datos
     * de Testcontainers fueron todas exitosas.</p>
     */
    @Test
    @DisplayName("El contexto de la aplicación debe cargar exitosamente")
    void contextLoads() {
        System.out.println("Contexto cargado y conectado a la BBDD de Testcontainers!");
    }

}