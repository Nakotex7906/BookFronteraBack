package bookfronterab.controller;

import bookfronterab.model.User;
import bookfronterab.model.UserRole;
import bookfronterab.repo.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    // Variables para simular la seguridad compleja
    private OAuth2User oauth2UserMock;
    private OAuth2AuthenticationToken oauth2AuthToken;

    @BeforeEach
    void setUp() {
        // 1. Preparamos el usuario OAuth2 (el "Principal")
        oauth2UserMock = mock(OAuth2User.class);
        when(oauth2UserMock.getAttribute("email")).thenReturn("student@test.com");

        // 2. Preparamos el Token de Autenticación Específico
        // El controlador hace un cast a (OAuth2AuthenticationToken), así que necesitamos este tipo exacto.
        oauth2AuthToken = new OAuth2AuthenticationToken(
                oauth2UserMock,
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")),
                "google" // registrationId dummy
        );

        // 3. Inyectamos la seguridad en el contexto global
        SecurityContextHolder.getContext().setAuthentication(oauth2AuthToken);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- TEST GET /me ---

    @Test
    void getMe_DeberiaRetornarUsuario_CuandoExiste() throws Exception {
        // Preparamos un usuario de base de datos
        User userEntity = new User();
        userEntity.setId(1L);
        userEntity.setEmail("student@test.com");
        userEntity.setNombre("Juan Perez");
        userEntity.setRol(UserRole.STUDENT);

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(userEntity));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("student@test.com"))
                .andExpect(jsonPath("$.rol").value("STUDENT"));
    }

    @Test
    void getMe_DeberiaRetornarNotFound_CuandoNoExisteEnBD() throws Exception {
        // El usuario está logueado en Google, pero no existe en nuestra BD local
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getMe_DeberiaRetornarUnauthorized_CuandoNoHayPrincipal() throws Exception {
        // Borramos el contexto de seguridad para simular usuario no logueado
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // --- TEST PATCH /toggle-role ---

    @Test
    void toggleRole_DeberiaCambiarDeStudentAAdmin() throws Exception {
        // 1. Estado inicial: Usuario es STUDENT
        User userEntity = new User();
        userEntity.setId(1L);
        userEntity.setEmail("student@test.com");
        userEntity.setRol(UserRole.STUDENT);

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(userEntity));

        // 2. Simulamos el guardado: cuando guarden, devolvemos el usuario con el rol cambiado
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            return invocation.getArgument(0);
        });

        // 3. Ejecutar
        mockMvc.perform(patch("/api/v1/users/toggle-role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN")); // Verificamos que cambió en la respuesta

        // 4. Verificamos que se llamó al repositorio para guardar
        verify(userRepository).save(any(User.class));
    }

    @Test
    void toggleRole_DeberiaCambiarDeAdminAStudent() throws Exception {
        // 1. Estado inicial: Usuario es ADMIN
        User userEntity = new User();
        userEntity.setEmail("student@test.com");
        userEntity.setRol(UserRole.ADMIN); // Es ADMIN

        when(userRepository.findByEmail("student@test.com")).thenReturn(Optional.of(userEntity));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // 2. Ejecutar
        mockMvc.perform(patch("/api/v1/users/toggle-role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("STUDENT")); // Debe cambiar a STUDENT
    }

    @Test
void toggleRole_DeberiaFallar_CuandoUsuarioNoExisteEnBD() throws Exception {
    // 1. Arrange: El repositorio no encuentra al usuario
    when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

    // 2. Ejecutar y Verificar
    mockMvc.perform(patch("/api/v1/users/toggle-role"))
            
            // 3. CORRECCIÓN: Esperamos 400 Bad Request, según la regla del GlobalExceptionHandler para IllegalStateException
            .andExpect(status().isBadRequest()) 
            
            // 4. Verificamos que la CAUSA del 400 fue la IllegalStateException
            .andExpect(result -> {
                assertTrue(result.getResolvedException() instanceof IllegalStateException, 
                           "Se esperaba IllegalStateException al no encontrar el usuario.");
            });
}
}