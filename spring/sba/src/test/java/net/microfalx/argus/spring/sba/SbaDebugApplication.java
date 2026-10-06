package net.microfalx.argus.spring.sba;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Standalone launcher to manually exercise Spring Boot Admin (and the Argus Wallboard score
 * extension) in a browser. Lives under src/test so it never ships in the module's jar.
 * <p>
 * It registers itself as a monitored client so the Wallboard has at least one hexagon to show,
 * and fakes a "health" indicator producing a random Argus-style score (1-10, see
 * {@link net.microfalx.argus.api.Health#MIN}/{@link net.microfalx.argus.api.Health#MAX}) and a
 * matching one-line report (shown as the badge tooltip) on every poll - a real {@code HealthService} isn't available here (it lives in the Service
 * Locator repo), so this stands in for {@code argus-actuator}'s HealthHealthIndicator under the
 * same "health" key.
 * <p>
 * Run it, then open http://localhost:8080/wallboard. To see the avg/min/max badge on a hexagon
 * with more than one instance, run a second copy with {@code -Dserver.port=8081} alongside it;
 * both self-register under the same application name.
 */
@SpringBootApplication
@EnableAdminServer
public class SbaDebugApplication {

    public static void main(String[] args) {
        SpringApplication.run(SbaDebugApplication.class, args);
    }

    @Bean
    public HealthIndicator health() {
        return () -> {
            float score = 1f + ThreadLocalRandom.current().nextFloat() * 9f;
            String report = String.format("Debug\n   • Random\n      ▪ Score: %.1f", score);
            return Health.up().withDetail("score", score).withDetail("report", report).build();
        };
    }
}
