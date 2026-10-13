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
import java.time.*;
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
    private static final String URL = "https://aa.usno.navy.mil/api/rstt/oneday?date=2025-10-13&coords=43.610000,-72.970000&tz=0&dst=false";
    private static final String RESPONSE = """
        {"properties":{"data":{"year":2025,"month":10,"day":13,"tz":0.0,"isdst":false,
        "sundata":[{"phen":"Begin Civil Twilight","time":"04:52"},{"phen":"Rise","time":"05:20"},{"phen":"Set","time":"16:32"}],
        "moondata":[{"phen":"Upper Transit","time":"04:40"},{"phen":"Set","time":"12:46"},{"phen":"Rise","time":"21:38"}]}}}
        """;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://aa.usno.navy.mil");
        server = MockRestServiceServer.bindTo(builder).build();
        generator = new SunriseSunsetGenerator(builder.build(), Clock.fixed(Instant.parse("2025-10-13T00:30:00Z"), ZoneOffset.ofHours(-4)));
    }
    private void response(String json) {
        server.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    @Test void returnsGmtSunAndMoonTimesAndOverridesSavedCoordinates() {
        response(RESPONSE);
        assertEquals("Sun rise 05:20Z set 16:32Z Moon rise 21:38Z set 12:46Z",
                generator.generate(64,DataFormat.APRS,location,Map.of("x","0","y","0")));
        server.verify();
    }
    @Test void savedSettingsAndExactLength() {
        response(RESPONSE);
        String expected = "Sun rise 05:20Z set 16:32Z Moon rise 21:38Z set 12:46Z";
        assertEquals(expected,generator.generate(expected.length(),DataFormat.UNICODE,Map.of(),location));
        server.verify();
    }
    @Test void tooShortReturnsErrorWithoutTruncation() {
        response(RESPONSE);
        assertEquals(422,assertThrows(ResponseStatusException.class,()->generator.generate(10,DataFormat.APRS,location,Map.of())).getStatusCode().value());
        server.verify();
    }
    @Test void polarAndIndependentMissingEventsAreMeaningfulResults() {
        response("{\"properties\":{\"data\":{\"year\":2025,\"month\":10,\"day\":13,\"tz\":0,\"sundata\":[{\"phen\":\"Rise\",\"time\":null}],\"moondata\":[{\"phen\":\"Set\",\"time\":\"12:46\"}]}}}");
        assertEquals("Sun rise none set none Moon rise none set 12:46Z",generator.generate(64,DataFormat.UNICODE,location,Map.of()));
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
