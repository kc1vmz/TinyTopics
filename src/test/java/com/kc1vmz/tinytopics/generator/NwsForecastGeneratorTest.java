/*
 *
 * TinyTopics
 * Copyright (c) 2026 John Rokicki KC1VMZ
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * https://www.gnu.org/licenses/.
 *
 * http://www.kc1vmz.com
 */
package com.kc1vmz.tinytopics.generator;

import com.kc1vmz.tinytopics.model.DataFormat;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NwsForecastGeneratorTest {
    private MockRestServiceServer server;
    private NwsForecastGenerator generator;
    private final Map<String,String> location = Map.of("wfo","BTV","x","-72.97","y","43.61");
    private static final String POINT = """
        {"properties":{"gridId":"BTV","gridX":42,"gridY":33,"timeZone":"America/New_York"}}
        """;
    private static final String FORECAST = """
        {"properties":{"periods":[
        {"name":"Today","startTime":"2026-10-07T06:00:00-04:00","endTime":"2026-10-07T18:00:00-04:00","detailedForecast":"Sunny all afternoon with clear skies and light winds.","shortForecast":"Sunny","temperature":65,"temperatureUnit":"F","windDirection":"NW","windSpeed":"5 mph"},
        {"name":"Tonight","startTime":"2026-10-07T18:00:00-04:00","endTime":"2026-10-08T06:00:00-04:00","detailedForecast":"Clear skies throughout the night with light northwest winds.","shortForecast":"Clear","temperature":42,"temperatureUnit":"F","windDirection":"NW","windSpeed":"5 mph"},
        {"name":"Thursday","startTime":"2026-10-08T06:00:00-04:00","endTime":"2026-10-08T18:00:00-04:00","detailedForecast":"Rain tomorrow.","shortForecast":"Rain"}]}}
        """;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api.weather.gov");
        server=MockRestServiceServer.bindTo(builder).build();
        generator=new NwsForecastGenerator(builder.build(),Clock.fixed(Instant.parse("2026-10-08T01:00:00Z"),ZoneOffset.UTC));
    }
    private void point() {
        server.expect(requestTo("https://api.weather.gov/points/43.6100,-72.9700")).andRespond(withSuccess(POINT,MediaType.APPLICATION_JSON));
    }
    private void forecast() {
        point();
        server.expect(requestTo("https://api.weather.gov/gridpoints/BTV/42,33/forecast")).andRespond(withSuccess(FORECAST,MediaType.APPLICATION_JSON));
    }
    @Test void selectsFullLocalDayAndSkipsTomorrow() {
        forecast();
        String result=generator.generate(1000,DataFormat.UNICODE,location,Map.of());
        assertTrue(result.startsWith("Today: Sunny"));
        assertFalse(result.contains("Thursday"));
        assertTrue(result.contains("Tonight:"));
        server.verify();
    }
    @Test void compactForecastAndSettingsFallback() {
        forecast();
        assertEquals("Today: Sunny 65F wind NW 5 mph",generator.generate(40,DataFormat.APRS,Map.of(),location));
        server.verify();
    }
    @Test void includesBothRemainingDayAndNightPeriods() {
        var builder=RestClient.builder().baseUrl("https://api.weather.gov");
        server=MockRestServiceServer.bindTo(builder).build();
        generator=new NwsForecastGenerator(builder.build(),Clock.fixed(Instant.parse("2026-10-07T16:00:00Z"),ZoneOffset.UTC));
        forecast();
        String result=generator.generate(4096,DataFormat.UNICODE,location,Map.of());
        assertTrue(result.contains("Today:")); assertTrue(result.contains("Tonight:")); assertFalse(result.contains("Thursday:"));
        server.verify();
    }
    @Test void doesNotTruncate() {
        forecast();
        var ex=assertThrows(ResponseStatusException.class,()->generator.generate(1,DataFormat.APRS,location,Map.of()));
        assertEquals(422,ex.getStatusCode().value()); server.verify();
    }
    @Test void rejectsWrongOfficeAndInvalidCoordinates() {
        assertEquals(400,assertThrows(ResponseStatusException.class,()->generator.generate(100,DataFormat.UNICODE,Map.of(),Map.of())).getStatusCode().value());
        assertThrows(ResponseStatusException.class,()->generator.generate(100,DataFormat.UNICODE,Map.of("wfo","BTV","x","181","y","43"),Map.of()));
        point();
        assertEquals(400,assertThrows(ResponseStatusException.class,()->generator.generate(100,DataFormat.UNICODE,Map.of("wfo","BOX","x","-72.97","y","43.61"),Map.of())).getStatusCode().value());
        server.verify();
    }
    @Test void upstreamFailuresAreSeparateErrors() {
        server.expect(requestTo("https://api.weather.gov/points/43.6100,-72.9700")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(502,assertThrows(ResponseStatusException.class,()->generator.generate(100,DataFormat.UNICODE,location,Map.of())).getStatusCode().value());
        server.verify();
    }
    @Test void noCurrentDayForecastReturnsNoContent() {
        point();
        server.expect(requestTo("https://api.weather.gov/gridpoints/BTV/42,33/forecast")).andRespond(withSuccess("{\"properties\":{\"periods\":[]}}",MediaType.APPLICATION_JSON));
        assertNull(generator.generate(100,DataFormat.UNICODE,location,Map.of())); server.verify();
    }
    @Test void tomorrowUsesLocalCalendarAndIncludesOverlappingNight() {
        forecast();
        var parameters = new java.util.HashMap<>(location);
        parameters.put("dayOffset", "1");
        // UTC date is October 8, but local today is still October 7: tomorrow is October 8.
        String result = generator.generate(4096, DataFormat.UNICODE, parameters, Map.of());
        assertTrue(result.contains("Tonight:"));
        assertTrue(result.contains("Thursday: Rain tomorrow."));
        assertFalse(result.contains("Today:"));
        server.verify();
    }
    @Test void savedOffsetDefaultsAndRequestOverridesIt() {
        forecast();
        assertTrue(generator.generate(4096, DataFormat.UNICODE, location, Map.of("dayOffset", "1")).contains("Thursday: Rain tomorrow."));
        server.verify();
        setup(); forecast();
        var parameters = new java.util.HashMap<>(location);
        parameters.put("dayOffset", "0");
        assertTrue(generator.generate(4096, DataFormat.UNICODE, parameters, Map.of("dayOffset", "1")).startsWith("Today:"));
        server.verify();
    }
    @Test void unavailableFutureDayReturnsNoContent() {
        forecast();
        assertNull(generator.generate(4096, DataFormat.UNICODE, location, Map.of("dayOffset", "2")));
        server.verify();
    }
    @Test void rejectsInvalidOffsetBeforeHttpRequests() {
        for (String offset : new String[]{"-1", "1.5", "tomorrow", "2147483648"}) {
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> generator.generate(64,
                DataFormat.UNICODE, location, Map.of("dayOffset", offset))).getStatusCode().value());
        }
        server.verify();
    }
}
