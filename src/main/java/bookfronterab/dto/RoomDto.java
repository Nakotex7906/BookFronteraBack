package bookfronterab.dto;


import java.util.Set;

public record RoomDto(Long id, String name, int capacity, Set<String> equipment) {}