package net.microfalx.argus.spring.actuator;

import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures Argus actuator integrations.
 */
@AutoConfiguration
public class ActuatorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(HealthHealthIndicator.class)
    @ConditionalOnEnabledHealthIndicator("health")
    public HealthHealthIndicator argusHealthIndicator() {
        return new HealthHealthIndicator();
    }

    @Bean
    @ConditionalOnMissingBean(ResourceEndpoint.class)
    public ResourceEndpoint argusResourceEndpoint() {
        return new ResourceEndpoint();
    }
}

