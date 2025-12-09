package bookfronterab.controller;

import bookfronterab.service.RateLimitingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    //  Mockear la dependencia que necesita el RateLimitFilter
    @MockitoBean
    private RateLimitingService rateLimitingService;

    // TEST 1: Verificar comportamiento cuando NO hay usuario logueado
    @Test
    void debug_DeberiaRetornarNoAutenticado_CuandoAuthEsNull() throws Exception {
        mockMvc.perform(get("/api/v1/auth-debug"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("No autenticado"));
    }

    // TEST 2: Verificar comportamiento cuando SÍ hay usuario logueado
    @Test
    void debug_DeberiaRetornarDetalles_CuandoAuthExiste() throws Exception {
        Authentication authMock = mock(Authentication.class);

        when(authMock.getName()).thenReturn("usuario_prueba");
        when(authMock.getPrincipal()).thenReturn("SoyUnPrincipalDePrueba");
        when(authMock.getAuthorities()).thenReturn((List) List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/v1/auth-debug")
                        .principal(authMock))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("usuario_prueba"))
                .andExpect(jsonPath("$.principal_type").value("java.lang.String"))
                .andExpect(jsonPath("$.roles_detectados[0]").value("ROLE_ADMIN"));
    }
}