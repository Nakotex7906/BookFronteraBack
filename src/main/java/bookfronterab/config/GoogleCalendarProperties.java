package bookfronterab.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "google.calendar")
@Getter
@Setter
public class GoogleCalendarProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
}
