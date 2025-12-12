package bookfronterab.controller;

import bookfronterab.dto.RoomDto;
import bookfronterab.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RoomDto> createRoom(
            @RequestPart("room") @Valid RoomDto roomDto,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        RoomDto newRoom = roomService.createRoom(roomDto, image);
        return new ResponseEntity<>(newRoom, HttpStatus.CREATED);
    }

    @DeleteMapping("{id}")
    public ResponseEntity<HttpStatus> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PatchMapping(value = "{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RoomDto> patchRoom(
            @PathVariable Long id,
            @RequestPart("room") @Valid RoomDto roomDto,
            @RequestPart(value = "image", required = false) MultipartFile image) {

        RoomDto patchedRoom = roomService.patchRoom(id, roomDto, image);
        return ResponseEntity.ok(patchedRoom);
    }

    @PutMapping("{id}")
    public ResponseEntity<RoomDto> updateRoom(@PathVariable Long id, @Valid @RequestBody RoomDto roomDto) {
        return ResponseEntity.ok(roomService.putRoom(id, roomDto));
    }
}