package bookfronterab.controller;

import bookfronterab.dto.ReservationDto;
import bookfronterab.service.ReservationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationService reservationService;

    private Authentication authMock;
    private OAuth2User oauth2UserMock;

    @BeforeEach
    void setUp() {
        authMock = mock(Authentication.class);
        oauth2UserMock = mock(OAuth2User.class);

        when(authMock.getPrincipal()).thenReturn(oauth2UserMock);
        when(oauth2UserMock.getAttribute("email")).thenReturn("usuario@test.com");

        // Inyectamos la autenticación manualmente
        SecurityContextHolder.getContext().setAuthentication(authMock);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // 1. TEST DE CREAR RESERVA
    @Test
    void create_DeberiaLlamarAlServicio_CuandoUsuarioAutenticado() throws Exception {
        // CORRECCIÓN APLICADA: Se pasan los 4 argumentos requeridos (Long, Date, Date, boolean)
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(
                1L,
                ZonedDateTime.now(),
                ZonedDateTime.now().plusHours(1),
                false
        );

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(reservationService).create(eq("usuario@test.com"), any(ReservationDto.CreateRequest.class));
    }

    // 2. TEST DE MIS RESERVAS
    @Test
    void getMyReservations_DeberiaRetornarLista() throws Exception {
        // CORRECCIÓN APLICADA: Se usa null para evitar conflictos de tipos con Listas
        ReservationDto.MyReservationsResponse responseMock = new ReservationDto.MyReservationsResponse(
                null, null, null
        );

        when(reservationService.getMyReservations("usuario@test.com")).thenReturn(responseMock);

        mockMvc.perform(get("/api/v1/reservations/my-reservations"))
                .andExpect(status().isOk());
    }

    // 3. TEST DE DETALLE POR ID
    @Test
    void get_DeberiaRetornarDetalle() throws Exception {
        Long reservaId = 100L;
        // CORRECCIÓN APLICADA: Devolvemos null para no tener problemas con el constructor de Detail
        when(reservationService.getById(reservaId)).thenReturn(null);

        mockMvc.perform(get("/api/v1/reservations/{id}", reservaId))
                .andExpect(status().isOk());

        verify(reservationService).getById(reservaId);
    }

    // 4. TEST DE CANCELAR
    @Test
    void cancel_DeberiaLlamarAlServicio() throws Exception {
        Long reservaId = 50L;

        mockMvc.perform(delete("/api/v1/reservations/{id}", reservaId))
                .andExpect(status().isNoContent());

        verify(reservationService).cancel(reservaId, "usuario@test.com");
    }

    // 5. TEST DE ERROR SIN USUARIO
    @Test
    void create_DeberiaFallar_CuandoNoHayUsuario() throws Exception {
        SecurityContextHolder.clearContext();

        // CORRECCIÓN APLICADA: Se pasan los argumentos requeridos también aquí
        ReservationDto.CreateRequest request = new ReservationDto.CreateRequest(
                1L, ZonedDateTime.now(), ZonedDateTime.now().plusHours(1), false
        );

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(result -> {
                    if (result.getResponse().getStatus() == 201) {
                        throw new AssertionError("ERROR: Se creó la reserva sin estar autenticado");
                    }
                });
    }
}