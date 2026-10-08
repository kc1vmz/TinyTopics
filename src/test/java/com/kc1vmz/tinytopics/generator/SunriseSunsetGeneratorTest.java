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

class SunriseSunsetGeneratorTest {
    private MockRestServiceServer server;
    private SunriseSunsetGenerator generator;
    private final Map<String,String> location = Map.of("x","-72.97","y","43.61");
    private static final String URL = "https://api.sunrise-sunset.org/v2?lat=43.610000&lng=-72.970000&date=today";
    private static final String RESPONSE = """
        {"date":"2026-10-07","tzid":"America/New_York","sunrise":"2026-10-07T07:02:30-04:00","sunset":"2026-10-07T18:23:45-04:00"}
        """;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://api.sunrise-sunset.org");
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new SunriseSunsetGenerator(builder.build());
    }
    private void response(String json) {
        server.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    @Test void returnsLocalTimesAndOverridesSavedCoordinates() {
        response(RESPONSE);
        assertEquals("Sunrise 07:02; sunset 18:23",
                generator.generate(64,DataFormat.APRS,location,Map.of("x","0","y","0")));
        server.verify();
    }
    @Test void savedSettingsAndExactLength() {
        response(RESPONSE);
        String expected = "Sunrise 07:02; sunset 18:23";
        assertEquals(expected,generator.generate(expected.length(),DataFormat.UNICODE,Map.of(),location));
        server.verify();
    }
    @Test void tooShortReturnsErrorWithoutTruncation() {
        response(RESPONSE);
        assertEquals(422,assertThrows(ResponseStatusException.class,()->generator.generate(10,DataFormat.APRS,location,Map.of())).getStatusCode().value());
        server.verify();
    }
    @Test void polarAndIndependentMissingEventsAreMeaningfulResults() {
        response("{\"date\":\"2026-10-07\",\"sunrise\":null,\"sunset\":null}");
        assertEquals("Sunrise none; sunset none",generator.generate(64,DataFormat.UNICODE,location,Map.of()));
        server.verify();
    }
    @Test void rejectsInvalidCoordinatesWithoutNetwork() {
        for(var input:java.util.List.of(Map.<String,String>of(),Map.of("x","NaN","y","43"),Map.of("x","181","y","0"),Map.of("x","0","y","-91")))
            assertEquals(400,assertThrows(ResponseStatusException.class,()->generator.generate(64,DataFormat.UNICODE,input,Map.of())).getStatusCode().value());
        server.verify();
    }
    @Test void upstreamFailureIsAnError() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertEquals(502,assertThrows(ResponseStatusException.class,()->generator.generate(64,DataFormat.UNICODE,location,Map.of())).getStatusCode().value());
        server.verify();
    }
    @Test void missingFieldsAreNotNoContent() {
        response("{\"date\":\"2026-10-07\"}");
        assertEquals(502,assertThrows(ResponseStatusException.class,()->generator.generate(64,DataFormat.UNICODE,location,Map.of())).getStatusCode().value());
        server.verify();
    }
}
