package bookfronterab.dto;

import java.util.Set;

public record RoomDto(Long id, String nombre, String ubicacion, int capacidad, Set<String> equipos, boolean activa) {}