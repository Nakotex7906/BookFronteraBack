package bookfronterab.exception;

/**
 * Excepción lanzada cuando ocurre un fallo irrecuperable
 * durante el proceso de subida o manipulación de imágenes (ej. Cloudinary).
 * * Es una excepción no chequeada (RuntimeException) para evitar contaminar
 * las firmas de los métodos de servicio.
 */
public class ImageUploadException extends RuntimeException {

    public ImageUploadException(String message) {
        super(message);
    }

    public ImageUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}