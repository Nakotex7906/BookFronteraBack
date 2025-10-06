package bookfronterab.controller;

import bookfronterab.dto.TimeSlotDto;
import bookfronterab.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SlotController {

    private final AvailabilityService availabilityService;

    @GetMapping("/slots")
    public List<TimeSlotDto> getSlots() {
        return availabilityService.generateTimeSlots(9, 21, 60);
    }
}
