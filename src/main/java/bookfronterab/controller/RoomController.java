package bookfronterab.controller;

import bookfronterab.dto.RoomDto;
import bookfronterab.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
// Capa de seguridad extra: Asegura que todos los métodos de esta clase requieran ADMIN
@PreAuthorize("hasRole('ADMIN')")
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public List<RoomDto> getAllRooms() {
        return roomService.getAllRooms();
    }

    @PostMapping
    public ResponseEntity<RoomDto> createRoom(@Valid @RequestBody RoomDto roomDto) {
        RoomDto newRoom = roomService.createRoom(roomDto);
        return new ResponseEntity<>(newRoom, HttpStatus.CREATED);
    }

    @DeleteMapping("{id}")
    public ResponseEntity<HttpStatus> deleteRoom(@PathVariable Long id) {
        roomService.delateRoom(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PatchMapping("{id}")
    public ResponseEntity<RoomDto> patchRoom(@PathVariable Long id, @Valid @RequestBody RoomDto roomDto) {
        RoomDto patchedRoom = roomService.patchRoom(id, roomDto);
        return ResponseEntity.ok(patchedRoom);
    }

    @PutMapping("{id}")
    public ResponseEntity<RoomDto> updateRoom(@PathVariable Long id, @Valid @RequestBody RoomDto roomDto) {
        return ResponseEntity.ok(roomService.putRoom(id, roomDto));
    }
}