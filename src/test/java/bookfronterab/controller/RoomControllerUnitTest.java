package bookfronterab.controller;

import bookfronterab.config.CustomAuthenticationFailureHandler;
import bookfronterab.config.CustomAuthenticationSuccessHandler;
import bookfronterab.config.SecurityConfig;
import bookfronterab.dto.RoomDto;
import bookfronterab.service.RoomService;
import bookfronterab.service.google.CustomOidcUserService;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito; // IMPORT NECESARIO
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration; // IMPORT NECESARIO
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean; // IMPORT NECESARIO
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas Unitarias (Slice Test) para RoomController.
 * Verifica:
 * 1. Acceso permitido (200/201/204) solo con ROLE_ADMIN.
 * 2. Acceso denegado (403 FORBIDDEN) con ROLE_STUDENT.
 * 3. Integración correcta con el RoomService.
 */
@WebMvcTest(RoomController.class)
@ActiveProfiles("test")
// Importamos la Configuración de Seguridad y la Configuración de Mocks para que el contexto arranque.
@Import({SecurityConfig.class, RoomControllerUnitTest.SecurityTestConfig.class})
class RoomControllerUnitTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // Solo necesitamos mockear RoomService, ya que las dependencias de SecurityConfig
    // son provistas como @Bean en SecurityTestConfig.
    @MockBean private RoomService roomService;

    // --- ELIMINADOS los @MockBean de seguridad para usar el @TestConfiguration ---

    // --- Objetos de Prueba ---
    private final RoomDto mockRoomDto = RoomDto.builder()
            .id(1L)
            .name("Sala Test")
            .capacity(10)
            .build();
    
    private final MockMultipartFile mockImage = new MockMultipartFile(
            "image", "test.jpg", "image/jpeg", "image data".getBytes());


    // --- HELPERS para la Seguridad ---
    private final String ADMIN_USER = "admin@ufro.cl";
    private final String STUDENT_USER = "student@ufro.cl";


    // =================================================================================================
    // CONFIGURACIÓN ANIDADA PARA PROVEER MOCKS DE SEGURIDAD (SOLUCIÓN AL ERROR DE DEPENDENCIAS)
    // =================================================================================================
    @TestConfiguration
    static class SecurityTestConfig {
        
        // Proveemos Mocks reales como Beans para que Spring pueda inyectarlos en SecurityConfig.
        @Bean
        CustomOidcUserService customOidcUserService() {
            return Mockito.mock(CustomOidcUserService.class);
        }

        @Bean
        CustomAuthenticationSuccessHandler authenticationSuccessHandler() {
            return Mockito.mock(CustomAuthenticationSuccessHandler.class);
        }

        @Bean
        CustomAuthenticationFailureHandler authenticationFailureHandler() {
            return Mockito.mock(CustomAuthenticationFailureHandler.class);
        }
    }
    // =================================================================================================
    
    
    // =================================================================================================
    // TESTS: GET /api/v1/rooms
    // =================================================================================================

    @Test
    @DisplayName("GET /rooms (ADMIN) debe devolver 200 OK y la lista")
    @WithMockUser(username = ADMIN_USER, roles = {"ADMIN"})
    void getAllRooms_AsAdmin_ShouldReturnOk() throws Exception {
        when(roomService.getAllRooms()).thenReturn(List.of(mockRoomDto));

        mockMvc.perform(get("/api/v1/rooms")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
        
        verify(roomService).getAllRooms();
    }

    @Test
    @DisplayName("GET /rooms (STUDENT) debe denegar acceso (403 FORBIDDEN)")
    @WithMockUser(username = STUDENT_USER, roles = {"STUDENT"})
    void getAllRooms_AsStudent_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isForbidden());
        
        verify(roomService, never()).getAllRooms();
    }

    // =================================================================================================
    // TESTS: POST /api/v1/rooms (Creación con Multipart)
    // =================================================================================================

    @Test
    @DisplayName("POST /rooms (ADMIN) debe crear sala y devolver 201 CREATED")
    @WithMockUser(username = ADMIN_USER, roles = {"ADMIN"})
    void createRoom_AsAdmin_ShouldReturnCreated() throws Exception {
        // Arrange
        when(roomService.createRoom(any(RoomDto.class), any())).thenReturn(mockRoomDto);
        MockMultipartFile roomPart = new MockMultipartFile(
                "room", "", "application/json", objectMapper.writeValueAsBytes(mockRoomDto));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/rooms")
                        .file(roomPart)
                        .file(mockImage)
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        verify(roomService).createRoom(any(RoomDto.class), any());
    }

    @Test
    @DisplayName("POST /rooms (SIN AUTH) debe denegar acceso (401 UNAUTHORIZED)")
    void createRoom_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        MockMultipartFile roomPart = new MockMultipartFile(
                "room", "", "application/json", objectMapper.writeValueAsBytes(mockRoomDto));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/rooms")
                        .file(roomPart)
                        .file(mockImage)
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        
        verify(roomService, never()).createRoom(any(), any());
    }
    
    // =================================================================================================
    // TESTS: DELETE /api/v1/rooms/{id}
    // =================================================================================================

    @Test
    @DisplayName("DELETE /rooms/{id} (ADMIN) debe eliminar y devolver 204 NO_CONTENT")
    @WithMockUser(username = ADMIN_USER, roles = {"ADMIN"})
    void deleteRoom_AsAdmin_ShouldReturnNoContent() throws Exception {
        // Arrange
        doNothing().when(roomService).delateRoom(1L);

        // Act & Assert
        mockMvc.perform(delete("/api/v1/rooms/{id}", 1L)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(roomService).delateRoom(1L);
    }

    @Test
    @DisplayName("DELETE /rooms/{id} (STUDENT) debe denegar acceso (403 FORBIDDEN)")
    @WithMockUser(username = STUDENT_USER, roles = {"STUDENT"})
    void deleteRoom_AsStudent_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/rooms/{id}", 1L)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(roomService, never()).delateRoom(anyLong());
    }

    // =================================================================================================
    // TESTS: PATCH /api/v1/rooms/{id} (Actualización parcial con Multipart)
    // =================================================================================================

    @Test
    @DisplayName("PATCH /rooms/{id} (ADMIN) debe actualizar sala y devolver 200 OK")
    @WithMockUser(username = ADMIN_USER, roles = {"ADMIN"})
    void patchRoom_AsAdmin_ShouldReturnOk() throws Exception {
        // Arrange
        when(roomService.patchRoom(anyLong(), any(RoomDto.class), any())).thenReturn(mockRoomDto);
        MockMultipartFile roomPart = new MockMultipartFile(
                "room", "", "application/json", objectMapper.writeValueAsBytes(mockRoomDto));

        // Act & Assert
        mockMvc.perform(multipart("/api/v1/rooms/{id}", 1L)
                        .file(roomPart)
                        .file(mockImage)
                        .with(request -> {
                            // Simulamos PATCH usando la utilidad de MockMvcRequestBuilders.multipart
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(csrf())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(roomService).patchRoom(eq(1L), any(RoomDto.class), any());
    }
    
    @Test
    @DisplayName("PATCH /rooms/{id} (STUDENT) debe denegar acceso (403 FORBIDDEN)")
    @WithMockUser(username = STUDENT_USER, roles = {"STUDENT"})
    void patchRoom_AsStudent_ShouldReturnForbidden() throws Exception {
        MockMultipartFile roomPart = new MockMultipartFile(
                "room", "", "application/json", objectMapper.writeValueAsBytes(mockRoomDto));
        
        mockMvc.perform(multipart("/api/v1/rooms/{id}", 1L)
                        .file(roomPart)
                        .with(request -> {
                            request.setMethod("PATCH");
                            return request;
                        })
                        .with(csrf()))
                .andExpect(status().isForbidden());
        
        verify(roomService, never()).patchRoom(anyLong(), any(), any());
    }

    // =================================================================================================
    // TESTS: PUT /api/v1/rooms/{id} (Actualización Completa)
    // =================================================================================================
    
    @Test
    @DisplayName("PUT /rooms/{id} (ADMIN) debe actualizar sala y devolver 200 OK")
    @WithMockUser(username = ADMIN_USER, roles = {"ADMIN"})
    void putRoom_AsAdmin_ShouldReturnOk() throws Exception {
        // Arrange
        when(roomService.putRoom(anyLong(), any(RoomDto.class))).thenReturn(mockRoomDto);

        // Act & Assert
        mockMvc.perform(put("/api/v1/rooms/{id}", 1L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockRoomDto)))
                .andExpect(status().isOk());
        
        verify(roomService).putRoom(eq(1L), any(RoomDto.class));
    }

    @Test
    @DisplayName("PUT /rooms/{id} (SIN AUTH) debe denegar acceso (401 UNAUTHORIZED)")
    void putRoom_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/rooms/{id}", 1L)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(mockRoomDto)))
                .andExpect(status().isUnauthorized());

        verify(roomService, never()).putRoom(anyLong(), any());
    }
}