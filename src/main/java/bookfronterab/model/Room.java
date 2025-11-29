package bookfronterab.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Entidad JPA que representa una sala (Room) física disponible para reservas.
 * Esta clase se mapea a la tabla "rooms" en la base de datos.
 *
 * Utiliza anotaciones de Lombok (@Getter, @Setter, @NoArgsConstructor,
 * @AllArgsConstructor, @Builder) para la generación automática de
 * constructores, getters, setters y el patrón Builder.
 */
@Entity
@Table(name = "\"rooms\"") // Mapea a la tabla "rooms". Las comillas dobles aseguran compatibilidad con PostgreSQL.
@Getter // Lombok: Genera métodos getter para todos los campos.
@Setter // Lombok: Genera métodos setter para todos los campos.
@NoArgsConstructor // Lombok: Genera un constructor vacío (requerido por JPA).
@AllArgsConstructor // Lombok: Genera un constructor que acepta todos los campos.
@Builder // Lombok: Implementa el patrón de diseño Builder para la clase.
public class Room {

    /**
     * Identificador único (Clave Primaria) de la sala.
     *
     * @Id Define este campo como la clave primaria de la tabla.
     * @GeneratedValue(strategy = GenerationType.SEQUENCE) Configura la estrategia de
     * generación de ID para usar una secuencia de base de datos.
     * @SequenceGenerator(name = "room_seq", ...) Define los detalles de la secuencia
     * de base de datos "room_id_seq" que se utilizará.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "room_seq")
    @SequenceGenerator(name = "room_seq", sequenceName = "room_id_seq", allocationSize = 1, initialValue = 1)
    private Long id;

    /**
     * El nombre descriptivo o de visualización de la sala (ej. "Sala de Conferencias 1").
     */
    private String name;

    /**
     * La capacidad máxima de personas permitida en la sala.
     */
    private int capacity;

    /**
     * Una colección de cadenas que describe el equipamiento disponible en la sala
     * (ej. "TV", "Pizarra", "Proyector").
     *
     * @ElementCollection Indica que esta es una colección de elementos básicos
     * (no de entidades) que será gestionada por JPA en una tabla separada.
     *
     * fetch = FetchType.EAGER: Especifica la estrategia de carga "Ansiosa".
     * Esto instruye a JPA a cargar esta colección (equipment) al mismo tiempo
     * que se carga la entidad Room principal.
     *
     * Nota: La estrategia por defecto es LAZY (perezosa). EAGER se usa aquí para
     * asegurar que la colección esté disponible inmediatamente después de cargar
     * la entidad, previniendo {@link org.hibernate.LazyInitializationException}
     * si se accede a la colección fuera de una sesión transaccional activa.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> equipment;

    /**
     * El número del piso (planta) en el que se ubica la sala.
     */
    private int floor;

    @Column(name = "image_url")
    private String imageUrl;

}