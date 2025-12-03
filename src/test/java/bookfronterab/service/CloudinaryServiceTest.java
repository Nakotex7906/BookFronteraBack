package bookfronterab.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para CloudinaryService.
 * Mockea la dependencia externa (Cloudinary) para asegurar que el servicio
 * interactúa correctamente con la API sin realizar llamadas reales a Internet.
 */
@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    // Inyecta los mocks en la clase a probar
    @InjectMocks
    private CloudinaryService cloudinaryService;

    // Mockea la dependencia externa
    @Mock
    private Cloudinary cloudinary;

    // Se necesita un mock para el Uploader, ya que se llama en cadena (cloudinary.uploader()...)
    @Mock
    private Uploader uploader;

    private static final String FAKE_URL = "https://res.cloudinary.com/test/image/upload/v12345/test_id.jpg";
    private static final String PUBLIC_ID = "v12345/test_id";
    private MultipartFile mockFile;
    private Map<String,Object> mockUploadResult;

    @BeforeEach
    void setUp() {
        // Configuramos la cadena de llamadas antes de cada test: cloudinary.uploader() -> uploader
        when(cloudinary.uploader()).thenReturn(uploader);

        // Creamos un archivo simulado (MockMultipartFile)
        mockFile = new MockMultipartFile(
                "image", 
                "test.jpg", 
                "image/jpeg", 
                "some-image-data".getBytes()
        );

        // Creamos el resultado simulado que Cloudinary devolvería
        mockUploadResult = Map.of(
                "secure_url", FAKE_URL,
                "public_id", PUBLIC_ID
        );
    }

    @Test
    @DisplayName("uploadFile debe llamar a la API y retornar la URL segura")
    void uploadFile_ShouldCallApiAndReturnUrl() throws IOException {
        
        // 1. Configurar el comportamiento del Mock:
        // Cuando se llame a uploader.upload() con cualquier byte[] y cualquier Map de opciones,
        // debe devolver nuestro resultado simulado (mockUploadResult).
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(mockUploadResult);

        // 2. Ejecutar el método del servicio:
        String actualUrl = cloudinaryService.uploadFile(mockFile);

        // 3. Verificar el resultado:
        assertEquals(FAKE_URL, actualUrl, "La URL devuelta debe ser la URL simulada.");
        
        // 4. Verificar la interacción (Opcional, pero bueno):
        // Verificamos que uploader.upload fue llamado una vez con los argumentos correctos.
        verify(uploader).upload(mockFile.getBytes(), ObjectUtils.emptyMap());
    }

    @Test
    @DisplayName("deleteFile debe llamar a la API con el publicId y no lanzar excepción")
    void deleteFile_ShouldCallApi() throws IOException {
        
        // 1. Configurar el comportamiento del Mock:
        // No necesitamos que devuelva nada, solo asegurarnos de que el método no lance error.
        // La configuración por defecto es que no hace nada (void method).

        // 2. Ejecutar el método del servicio:
        cloudinaryService.deleteFile(PUBLIC_ID);

        // 3. Verificar la interacción:
        // Verificamos que el método destroy() fue llamado con el publicId correcto y el mapa de opciones vacío.
        verify(uploader).destroy(PUBLIC_ID, ObjectUtils.emptyMap());
    }
}