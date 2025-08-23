package bookfronterab.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
                "app", "BookFrontera",
                "apiIndex", "/api/v1",
                "docs", "/swagger-ui/index.html"
        );
    }

}
