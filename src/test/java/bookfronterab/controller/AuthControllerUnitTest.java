package bookfronterab.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 1. Indicamos que vamos a probar solo este controlador
@WebMvcTest(AuthController.class)
// 2. Desactivamos los filtros de seguridad automática para tener control manual nosotros
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    // TEST 1: Verificar comportamiento cuando NO hay usuario logueado
    @Test
    void debug_DeberiaRetornarNoAutenticado_CuandoAuthEsNull() throws Exception {
        mockMvc.perform(get("/api/v1/auth-debug"))
                .andExpect(status().isOk()) // Esperamos HTTP 200
                .andExpect(jsonPath("$.status").value("No autenticado"));
    }

    // TEST 2: Verificar comportamiento cuando SÍ hay usuario logueado
    @Test
    void debug_DeberiaRetornarDetalles_CuandoAuthExiste() throws Exception {
        // A. Preparamos el "Mock" (el usuario falso)
        Authentication authMock = mock(Authentication.class);

        // Configuramos qué responderá este usuario falso cuando el controlador le pregunte
        when(authMock.getName()).thenReturn("usuario_prueba");
        when(authMock.getPrincipal()).thenReturn("SoyUnPrincipalDePrueba"); // Para evitar NullPointerException en .getClass()
        // Mockeamos una lista de autoridades (roles) usando 'doReturn' o 'when'
        when(authMock.getAuthorities()).thenReturn((List) List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // B. Ejecutamos la petición inyectando nuestro usuario falso (.principal)
        mockMvc.perform(get("/api/v1/auth-debug")
                        .principal(authMock)) // <--- Aquí pasamos el auth mockeado
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("usuario_prueba"))
                .andExpect(jsonPath("$.principal_type").value("java.lang.String")) // Porque el principal que retornamos arriba es un String
                .andExpect(jsonPath("$.roles_detectados[0]").value("ROLE_ADMIN"));
    }
}