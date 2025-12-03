package bookfronterab.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HomeController.class)
@AutoConfigureMockMvc(addFilters = false) // Desactiva login para que sea público
class HomeControllerUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void home_DeberiaRetornarInformacionBasica() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk()) // Esperamos un 200 OK
                .andExpect(jsonPath("$.app").value("BookFrontera")) // Verificamos que diga el nombre
                .andExpect(jsonPath("$.apiIndex").exists()) // Verificamos que existan las otras claves
                .andExpect(jsonPath("$.docs").exists());
    }
}