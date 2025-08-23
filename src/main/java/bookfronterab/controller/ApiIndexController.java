package bookfronterab.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApiIndexController {

    @GetMapping({"", "/"})
    public Map<String, Object> index() {
        return Map.of(
                "name", "BookFrontera API",
                "version", "v1",
                "endpoints", List.of(
                        "/api/v1/rooms"
                )
        );
    }

}
