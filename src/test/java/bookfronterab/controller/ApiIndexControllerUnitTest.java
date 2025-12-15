package bookfronterab.controller;

import bookfronterab.service.RateLimitingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiIndexController.class)
@AutoConfigureMockMvc(addFilters = false) // Desactiva seguridad para ir directo al endpoint
class ApiIndexControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    // Mock necesario por el filtro global RateLimitFilter
    @MockitoBean
    private RateLimitingService rateLimitingService;

    @Test
    @DisplayName("Debe retornar información de la API y estado 200")
    void index_ShouldReturnApiInfo() throws Exception {
        mockMvc.perform(get("/api/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("BookFrontera API"))
                .andExpect(jsonPath("$.version").value("v1"))
                .andExpect(jsonPath("$.endpoints").isArray());
    }

    @Test
    @DisplayName("Debe funcionar también con la barra final /")
    void index_ShouldWorkWithTrailingSlash() throws Exception {
        mockMvc.perform(get("/api/v1/"))
                .andExpect(status().isOk());
    }
}