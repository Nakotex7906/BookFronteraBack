package bookfronterab.controller;

import bookfronterab.dto.RoomDto;
import bookfronterab.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ResponseEntity<RoomDto> createFood(@Valid @RequestBody RoomDto roomDto) {
        RoomDto newRoom = roomService.createRoom(roomDto);
        return new ResponseEntity<>(newRoom, HttpStatus.CREATED); // 201 Created
    }



}