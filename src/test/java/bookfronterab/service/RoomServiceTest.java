package bookfronterab.service;

import bookfronterab.dto.RoomDto;
import bookfronterab.exception.ResourceNotFoundException;
import bookfronterab.model.Room;
import bookfronterab.repo.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas de integración para RoomService.
 * Verifica CRUD completo contra base de datos real.
 */
@Testcontainers
@SpringBootTest
class RoomServiceTest {

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

    @Autowired private RoomService roomService;
    @Autowired private RoomRepository roomRepository;
    @MockitoBean
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        roomRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("createRoom() con imagen debe guardar la URL devuelta por Cloudinary")
    void createRoom_WithImage_ShouldSaveUrl() throws IOException {
        RoomDto request = RoomDto.builder()
                .name("Sala Con Imagen")
                .capacity(10)
                .floor(1)
                .equipment(List.of("PC"))
                .build();

        MockMultipartFile fakeImage = new MockMultipartFile(
                "image",
                "sala.jpg",
                "image/jpeg",
                "fake-image-content".getBytes()
        );

        String fakeUrl = "https://res.cloudinary.com/demo/image/upload/v1/sala.jpg";
        when(cloudinaryService.uploadFile(any())).thenReturn(fakeUrl);

        RoomDto response = roomService.createRoom(request, fakeImage);

        assertNotNull(response.getId());
        assertEquals("Sala Con Imagen", response.getName());
        assertEquals(fakeUrl, response.getImageUrl());

        Optional<Room> saved = roomRepository.findById(response.getId());
        assertTrue(saved.isPresent());
        assertEquals(fakeUrl, saved.get().getImageUrl());
    }

    @Test
    @DisplayName("createRoom() sin imagen debe guardar null en imageUrl")
    void createRoom_WithoutImage_ShouldHaveNullUrl() {
        RoomDto request = RoomDto.builder().name("Sala Sin Imagen").capacity(20).floor(3).build();

        RoomDto response = roomService.createRoom(request, null);

        assertNotNull(response.getId());
        assertNull(response.getImageUrl());
    }

    @Test
    @DisplayName("getAllRooms debe devolver lista de salas")
    void getAllRooms_ShouldReturnList() {
        roomRepository.save(Room.builder().name("S1").capacity(5).floor(1).equipment(List.of("A")).build());
        roomRepository.save(Room.builder().name("S2").capacity(5).floor(1).equipment(List.of("B")).build());

        List<RoomDto> dtos = roomService.getAllRooms();
        assertEquals(2, dtos.size());
    }

    @Test
    @DisplayName("delateRoom() debe eliminar la sala de la BD")
    void deleteRoom_ShouldRemoveFromDb() {
        Room r = roomRepository.save(Room.builder().name("Borrar").capacity(5).build());

        roomService.delateRoom(r.getId());

        Optional<Room> check = roomRepository.findById(r.getId());
        assertTrue(check.isEmpty());
    }
}