package bookfronterab.controller;

import bookfronterab.dto.RoomDto;
import bookfronterab.service.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Collections; // Importante para listas vacías seguras

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoomControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RoomService roomService;

    private Authentication authMock;

    @BeforeEach
    void setUp() {
        // Configuramos seguridad manual para simular un ADMIN
        authMock = mock(Authentication.class);
        when(authMock.isAuthenticated()).thenReturn(true);
        // Le damos el rol de ADMIN para pasar el @PreAuthorize
        when(authMock.getAuthorities()).thenReturn((List) List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        SecurityContextHolder.getContext().setAuthentication(authMock);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 1. GET ALL ROOMS
    @Test
    void getAllRooms_DeberiaRetornarLista() throws Exception {
        when(roomService.getAllRooms()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isOk());

        verify(roomService).getAllRooms();
    }

    // 2. CREATE ROOM
    @Test
    void createRoom_DeberiaSubirImagenYJson() throws Exception {
        // CORRECCIÓN: Constructor con 6 argumentos (Long, String, int, List, int, String)
        RoomDto roomDtoInput = new RoomDto(
                1L,                 // id
                "Sala A",           // name
                10,                 // capacity
                Collections.emptyList(), // images/amenities (Lista vacía segura)
                1,                  // floor/level
                "Descripción"       // description
        );

        // Simulamos la imagen
        MockMultipartFile imageFile = new MockMultipartFile(
                "image", "foto.jpg", "image/jpeg", "bytes".getBytes()
        );

        // Simulamos el JSON
        MockMultipartFile jsonPart = new MockMultipartFile(
                "room", "", "application/json",
                objectMapper.writeValueAsBytes(roomDtoInput)
        );

        when(roomService.createRoom(any(RoomDto.class), any(MultipartFile.class))).thenReturn(roomDtoInput);

        mockMvc.perform(multipart("/api/v1/rooms")
                        .file(imageFile)
                        .file(jsonPart))
                .andExpect(status().isCreated());
    }

    // 3. DELETE ROOM
    @Test
    void deleteRoom_DeberiaRetornarNoContent() throws Exception {
        Long roomId = 10L;

        mockMvc.perform(delete("/api/v1/rooms/{id}", roomId))
                .andExpect(status().isNoContent());

        // Nota: Mantenemos 'delateRoom' porque así está escrito en tu controlador (con el typo 'a')
        verify(roomService).delateRoom(roomId);
    }

    // 4. PUT ROOM
    @Test
    void updateRoom_DeberiaActualizar() throws Exception {
        Long roomId = 1L;
        // CORRECCIÓN: Constructor con 6 argumentos
        RoomDto roomDto = new RoomDto(
                roomId, "Sala Modificada", 20, Collections.emptyList(), 2, "Nueva Desc"
        );

        when(roomService.putRoom(eq(roomId), any(RoomDto.class))).thenReturn(roomDto);

        mockMvc.perform(put("/api/v1/rooms/{id}", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomDto)))
                .andExpect(status().isOk());
    }

    // 5. PATCH ROOM
    @Test
    void patchRoom_DeberiaActualizarParcialmente() throws Exception {
        Long roomId = 1L;
        // CORRECCIÓN: Constructor con 6 argumentos
        RoomDto roomDto = new RoomDto(
                roomId, "Solo Nombre", 0, Collections.emptyList(), 0, null
        );

        when(roomService.patchRoom(eq(roomId), any(RoomDto.class))).thenReturn(roomDto);

        mockMvc.perform(patch("/api/v1/rooms/{id}", roomId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomDto)))
                .andExpect(status().isOk());
    }
}