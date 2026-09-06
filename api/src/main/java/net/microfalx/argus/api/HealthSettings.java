package net.microfalx.argus.api;

import lombok.*;
import net.microfalx.lang.annotation.SizeOf;

import java.time.Duration;

@Getter
@With
@NoArgsConstructor
@AllArgsConstructor
@ToString
@SizeOf
public class HealthSettings {

    /**
     * The default interval between metrics scraping (also the time when the health is updated, after
     * the metrics were calculated).
     * <p>
     * The default is 10 seconds.
     */
    private Duration scrapeInterval = Duration.ofSeconds(10);

    /**
     * The interval of data points to be used for health calculation.
     * <p>
     * The default is 5 minutes.
     */
    private Duration healthInterval = Duration.ofMinutes(5);


}
