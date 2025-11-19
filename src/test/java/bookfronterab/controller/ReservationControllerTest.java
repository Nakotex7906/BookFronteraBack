package bookfronterab.controller;

import bookfronterab.dto.ReservationDto;
import bookfronterab.service.ReservationService;
import bookfronterab.service.google.CustomOAuth2UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas Unitarias de Controlador (Slice Test) para {@link ReservationController}.
 * <p>
 * Utiliza {@code @WebMvcTest} para cargar solo la capa web de Spring, simulando
 * las peticiones HTTP con {@code MockMvc}.
 * Verifica el mapeo de endpoints, códigos de estado HTTP y la seguridad básica.
 */
@WebMvcTest(ReservationController.class)
class ReservationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // Mockeamos el servicio para no ejecutar lógica de negocio real en este test
    @MockBean private ReservationService reservationService;
    // Mockeamos el servicio de usuarios OAuth2 para levantar el contexto de seguridad
    @MockBean private CustomOAuth2UserService customOAuth2UserService;

    /**
     * Helper para crear un usuario OAuth2 simulado.
     */
    private OAuth2User mockOAuth2User(String email) {
        return new DefaultOAuth2User(
                Collections.emptyList(),
                Map.of("email", email, "name", "Test User"),
                "email"
        );
    }

    /**
     * Verifica que una petición POST autenticada cree una reserva y devuelva 201 Created.
     */
    @Test
    @DisplayName("POST /reservations crea reserva devuelve 201")
    void createReservation_ShouldReturn201() throws Exception {
        ReservationDto.CreateRequest req = new ReservationDto.CreateRequest(
                1L,
                ZonedDateTime.now().plusHours(1),
                ZonedDateTime.now().plusHours(2),
                false
        );

        doNothing().when(reservationService).create(any(), any());

        mockMvc.perform(post("/api/v1/reservations")
                        // Simulamos usuario logueado con OAuth2
                        .with(SecurityMockMvcRequestPostProcessors.oauth2Login().oauth2User(mockOAuth2User("test@ufromail.cl")))
                        .with(csrf()) // Token CSRF es obligatorio para POST
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // Verificamos que el controlador llamó al servicio con el email correcto
        verify(reservationService).create(eq("test@ufromail.cl"), any(ReservationDto.CreateRequest.class));
    }

    /**
     * Verifica que una petición POST SIN autenticación sea rechazada (Redirección a login).
     */
    @Test
    @DisplayName("POST /reservations sin login devuelve 302 (redirección a login) o 401")
    void createReservation_WithoutAuth_ShouldFail() throws Exception {
        ReservationDto.CreateRequest req = new ReservationDto.CreateRequest(1L, ZonedDateTime.now(), ZonedDateTime.now(), false);

        mockMvc.perform(post("/api/v1/reservations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is3xxRedirection()); // Spring Security redirige a /login por defecto
    }
}