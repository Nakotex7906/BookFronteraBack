package bookfronterab.controller;

import bookfronterab.dto.RoomDto;
import bookfronterab.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping("/rooms")
    public List<RoomDto> getAllRooms() {
        return roomService.getAllRooms();
    }

}