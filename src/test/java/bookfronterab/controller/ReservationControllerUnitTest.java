package bookfronterab.controller;

import bookfronterab.config.CustomAuthenticationFailureHandler;
import bookfronterab.config.CustomAuthenticationSuccessHandler;
import bookfronterab.config.SecurityConfig;
import bookfronterab.dto.ReservationDto;
import bookfronterab.service.RateLimitingService; // <--- 1. IMPORTAR ESTO
import bookfronterab.service.ReservationService;
import bookfronterab.service.google.CustomOidcUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;

@WebMvcTest(ReservationController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, ReservationControllerUnitTest.SecurityTestConfig.class})
class ReservationControllerUnitTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ReservationService reservationService;

    // SOLUCIÓN: Mockear el servicio requerido por el filtro RateLimitFilter
    @MockitoBean
    private RateLimitingService rateLimitingService;

    // Configurar el mock del RateLimiter antes de cada test
    @BeforeEach
    void setUpRateLimiter() {
        // 1. Crear un Bucket mock
        Bucket mockBucket = Mockito.mock(Bucket.class);
        // 2. Configurar el bucket para que siempre permita el consumo (devuelva true)
        when(mockBucket.tryConsume(1)).thenReturn(true);

        // 3. Configurar el servicio para que devuelva ese bucket mock cuando se le llame
        when(rateLimitingService.resolveBucket(anyString())).thenReturn(mockBucket);
    }

    //  CONFIGURACIÓN PARA MOCKS DE SEGURIDAD
    @TestConfiguration
    static class SecurityTestConfig {
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

    // --- DATOS DE PRUEBA ---
    private final String STUDENT_EMAIL = "student@ufromail.cl";
    private final String ADMIN_EMAIL = "admin@ufromail.cl";

    // Configuración OAuth2 para Estudiante
    private final SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor studentLogin =
            oauth2Login()
                    .attributes(attrs -> {
                        attrs.put("email", STUDENT_EMAIL);
                        attrs.put("name", "Student User");
                    })
                    .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));

    // Configuración OAuth2 para Admin
    private final SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor adminLogin =
            oauth2Login()
                    .attributes(attrs -> {
                        attrs.put("email", ADMIN_EMAIL);
                        attrs.put("name", "Admin User");
                    })
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));


    // =================================================================================================
    // 1. POST /reservations (Crear Reserva Normal)
    // =================================================================================================

    @Test
    @DisplayName("create() debe devolver 201 CREATED cuando el usuario está autenticado")
    void create_ShouldReturnCreated_WhenAuthenticated() throws Exception {
        ReservationDto.CreateRequest req = new ReservationDto.CreateRequest(
                1L, ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2), false
        );

        mockMvc.perform(post("/api/v1/reservations")
                        .with(studentLogin) // Simulamos login
                        .with(csrf())       // Token CSRF obligatorio
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(reservationService).create(eq(STUDENT_EMAIL), any(ReservationDto.CreateRequest.class));
    }

    @Test
    @DisplayName("create() debe devolver 401 UNAUTHORIZED si no hay usuario")
    void create_ShouldReturnUnauthorized_WhenNotAuthenticated() throws Exception {
        ReservationDto.CreateRequest req = new ReservationDto.CreateRequest(1L, ZonedDateTime.now(), ZonedDateTime.now().plusHours(1), false);

        mockMvc.perform(post("/api/v1/reservations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // =================================================================================================
    // 2. POST /reservations/on-behalf (Crear en nombre de otro - Solo ADMIN)
    // =================================================================================================

    @Test
    @DisplayName("createOnBehalf() debe devolver 201 CREATED si es ADMIN")
    void createOnBehalf_ShouldReturnCreated_IfAdmin() throws Exception {

        ReservationDto.CreateOnBehalfRequest req = new ReservationDto.CreateOnBehalfRequest(
                1L,
                ZonedDateTime.now().plusHours(1),
                ZonedDateTime.now().plusHours(2),
                STUDENT_EMAIL,
                false
        );

        mockMvc.perform(post("/api/v1/reservations/on-behalf")
                        .with(adminLogin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        verify(reservationService).createOnBehalf(eq(ADMIN_EMAIL), eq(STUDENT_EMAIL), any());
    }

    @Test
    @DisplayName("createOnBehalf() debe devolver 403 FORBIDDEN si es STUDENT")
    void createOnBehalf_ShouldReturnForbidden_IfStudent() throws Exception {
        String validPayload = "{"
                + "\"roomId\": 1,"
                + "\"startAt\": \"" + ZonedDateTime.now().plusDays(5).withHour(10) + "\","
                + "\"endAt\": \"" + ZonedDateTime.now().plusDays(5).withHour(11) + "\","
                + "\"othersEmail\": \"other@ufromail.cl\""
                + "}";

        doNothing().when(reservationService).createOnBehalf(anyString(), anyString(), any());

        mockMvc.perform(post("/api/v1/reservations/on-behalf")
                        .with(studentLogin)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload))
                .andExpect(status().isForbidden());
    }

    // =================================================================================================
    // 3. GET /reservations/my-reservations (Mis Reservas)
    // =================================================================================================

    @Test
    @DisplayName("getMyReservations() debe devolver 200 OK y llamar al servicio")
    void getMyReservations_ShouldReturnOk() throws Exception {
        ReservationDto.MyReservationsResponse mockResponse = new ReservationDto.MyReservationsResponse(
                null, Collections.emptyList(), Collections.emptyList()
        );

        when(reservationService.getMyReservations(STUDENT_EMAIL)).thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/reservations/my-reservations")
                        .with(studentLogin))
                .andExpect(status().isOk());

        verify(reservationService).getMyReservations(STUDENT_EMAIL);
    }

    // =================================================================================================
    // 4. GET /reservations/{id} (Detalle por ID)
    // =================================================================================================

    @Test
    @DisplayName("get() debe devolver 200 OK y el detalle")
    void getById_ShouldReturnOk() throws Exception {
        Long resId = 10L;
        when(reservationService.getById(resId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/reservations/{id}", resId)
                        .with(studentLogin))
                .andExpect(status().isOk());

        verify(reservationService).getById(resId);
    }

    // =================================================================================================
    // 5. DELETE /reservations/{id} (Cancelar)
    // =================================================================================================

    @Test
    @DisplayName("cancel() debe devolver 204 NO CONTENT")
    void cancel_ShouldReturnNoContent() throws Exception {
        Long resId = 55L;

        mockMvc.perform(delete("/api/v1/reservations/{id}", resId)
                        .with(studentLogin)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(reservationService).cancel(resId, STUDENT_EMAIL);
    }

    // =================================================================================================
    // 6. GET /room/{roomId} (Reservas por Sala)
    // =================================================================================================

    @Test
    @DisplayName("getByRoom() debe devolver 200 OK")
    void getByRoom_ShouldReturnOk() throws Exception {
        Long roomId = 1L;
        when(reservationService.getReservationsByRoom(roomId, ADMIN_EMAIL)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/room/{roomId}", roomId)
                        .with(adminLogin))
                .andExpect(status().isOk());

        verify(reservationService).getReservationsByRoom(roomId, ADMIN_EMAIL);
    }
}