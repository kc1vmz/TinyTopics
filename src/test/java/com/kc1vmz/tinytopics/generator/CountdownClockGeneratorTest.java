package com.kc1vmz.tinytopics.generator;

import com.kc1vmz.tinytopics.model.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class CountdownClockGeneratorTest {
    private final CountdownClockGenerator generator = new CountdownClockGenerator(
            Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC));
    private String countdown(String target) {
        return generator.generate(256, DataFormat.UNICODE, Map.of("targetTime",target,"description","Christmas"), Map.of());
    }
    @Test void daysHoursAndMinutes() {
        assertEquals("2 days 3 hours 15 minutes until Christmas",countdown("2026-10-15T15:15:00Z"));
        assertEquals(ContentSource.Local,generator.contentSource());
        assertEquals(java.util.List.of("targetTime","description"),generator.parameterNames());
    }
    @Test void roundingCarriesAcrossHourAndDay() {
        assertEquals("0 days 0 hours 1 minute until Christmas",countdown("2026-10-13T12:00:00.000000001Z"));
        assertEquals("0 days 1 hour 0 minutes until Christmas",countdown("2026-10-13T12:59:01Z"));
        assertEquals("1 day 0 hours 0 minutes until Christmas",countdown("2026-10-14T11:59:59Z"));
        assertEquals("1 day 1 hour 1 minute until Christmas",countdown("2026-10-14T13:01:00Z"));
    }
    @Test void passedAndEqualTargets() {
        assertEquals("Time has passed for Christmas",countdown("2026-10-13T11:59:59Z"));
        assertEquals("Time has passed for Christmas",countdown("2026-10-13T12:00:00Z"));
    }
    @Test void offsetsAndSavedDefaults() {
        assertEquals("0 days 1 hour 0 minutes until Field Day",generator.generate(64,DataFormat.APRS,
                Map.of("targetTime","2026-10-13T09:00:00-04:00"),Map.of("targetTime","2026-12-25T00:00:00Z","description","Field Day")));
    }
    @Test void validatesInputs() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->countdown("2026-10-13T13:00:00")).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->countdown("not-a-time"));
        assertThrows(ResponseStatusException.class,()->generator.generate(64,DataFormat.UNICODE,Map.of(),Map.of()));
    }
    @Test void validatesLengthAndAprsWithoutTruncation() {
        var settings = Map.of("targetTime","2026-10-13T11:00:00Z","description","Christmas");
        assertEquals("Time has passed for Christmas",generator.generate(29,DataFormat.APRS,Map.of(),settings));
        assertEquals(422,assertThrows(ResponseStatusException.class,()->generator.generate(28,DataFormat.APRS,Map.of(),settings)).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->generator.generate(64,DataFormat.APRS,Map.of("description","Café"),settings));
        assertEquals("Time has passed for Café",generator.generate(64,DataFormat.UNICODE,Map.of("description","Café"),settings));
    }
}
