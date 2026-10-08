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
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class NwsAlertsGeneratorTest {
    private MockRestServiceServer server;
    private NwsAlertsGenerator generator;
    private static final String ALERTS = """
        {"features":[{"properties":{"event":"Flood Warning","areaDesc":"Rutland","severity":"Severe","certainty":"Observed","headline":"Flood Warning issued for Rutland until tonight"}},
        {"properties":{"event":"Wind Advisory","areaDesc":"Windsor","severity":"Moderate","certainty":"Likely","headline":"Wind Advisory issued for Windsor until this evening"}}]}
        """;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api.weather.gov");
        server=MockRestServiceServer.bindTo(builder).build();
        generator=new NwsAlertsGenerator(builder.build());
    }
    private void alerts(String body) {
        server.expect(requestTo("https://api.weather.gov/alerts/active?point=43.6100,-72.9700")).andRespond(withSuccess(body,MediaType.APPLICATION_JSON));
    }
    @Test void returnsAllHeadlinesAndOverridesSavedCoordinates() {
        alerts(ALERTS);
        String text=generator.generate(1000,DataFormat.UNICODE,Map.of("x"," -72.97 ","y","43.61"),Map.of("x","0","y","0"));
        assertTrue(text.startsWith("Alerts (2):"));
        assertTrue(text.contains("Flood Warning issued")); assertTrue(text.contains("Wind Advisory issued"));
        server.verify();
    }
    @Test void compactSummaryAndSavedCoordinates() {
        alerts(ALERTS);
        assertEquals("Alerts (2): Flood Warning/Severe/Observed Wind Advisory/Moderate/Likely",
            generator.generate(100,DataFormat.APRS,Map.of(),Map.of("x","-72.97","y","43.61")));
        server.verify();
    }
    @Test void noAlertsMeansNoContent() {
        alerts("{\"features\":[]}");
        assertNull(generator.generate(100,DataFormat.UNICODE,Map.of("x","-72.97","y","43.61"),Map.of())); server.verify();
    }
    @Test void rejectsLengthTooSmallForAnyAlert() {
        alerts(ALERTS);
        assertEquals(422,assertThrows(ResponseStatusException.class,()->generator.generate(1,DataFormat.APRS,Map.of("x","-72.97","y","43.61"),Map.of())).getStatusCode().value());
        server.verify();
    }
    @Test void rejectsMissingAndInvalidCoordinates() {
        for (Map<String,String> input : java.util.List.of(Map.<String,String>of(), Map.of("x","181","y","43"), Map.of("x","-72","y","91"), Map.of("x","NaN","y","43"), Map.of("x","-72","y","43&area=VT"))) {
            assertEquals(400,assertThrows(ResponseStatusException.class,()->generator.generate(100,DataFormat.UNICODE,input,Map.of())).getStatusCode().value());
        }
        server.verify();
    }    @Test void upstreamFailure() {
        server.expect(requestTo("https://api.weather.gov/alerts/active?point=43.6100,-72.9700")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertEquals(502,assertThrows(ResponseStatusException.class,()->generator.generate(100,DataFormat.UNICODE,Map.of("x","-72.97","y","43.61"),Map.of())).getStatusCode().value()); server.verify();
    }
    @Test void invalidResponseIsNotNoContent() {
        alerts("{}");
        assertEquals(502,assertThrows(ResponseStatusException.class,()->generator.generate(100,DataFormat.UNICODE,Map.of("x","-72.97","y","43.61"),Map.of())).getStatusCode().value()); server.verify();
    }
}

