package bookfronterab.controller;

import bookfronterab.dto.RoomDto;
import bookfronterab.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
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