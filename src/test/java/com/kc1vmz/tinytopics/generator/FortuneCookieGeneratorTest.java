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

class FortuneCookieGeneratorTest {
    private MockRestServiceServer server;
    private RestClient client;
    private FortuneCookieGenerator generator;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl(FortuneCookieGenerator.BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build(); generator = new FortuneCookieGenerator(client);
    }
    private void fortune(String text) {
        server.expect(requestTo(FortuneCookieGenerator.BASE_URL + "/"))
                .andExpect(headerDoesNotExist("X-RapidAPI-Key"))
                .andExpect(headerDoesNotExist("X-RapidAPI-Host"))
                .andRespond(withSuccess("{\"data\":{\"message\":\"" + text + "\"},\"meta\":{\"status\":200}}", MediaType.APPLICATION_JSON));
    }
    private String generate(int length, DataFormat format) { return generator.generate(length, format, Map.of(), Map.of()); }
    @Test void firstFortuneFitsExactly() {
        fortune("Good luck"); assertEquals("Good luck", generate(9, DataFormat.APRS)); server.verify();
    }
    @Test void retriesUntilSixthAttemptFits() {
        for (int i=0; i<5; i++) fortune("This is too long");
        fortune("Luck"); assertEquals("Luck", generate(4, DataFormat.UNICODE)); server.verify();
    }
    @Test void exhaustionReturnsErrorWithoutTruncation() {
        for (int i=0; i<6; i++) fortune("Too long");
        assertEquals(422, assertThrows(ResponseStatusException.class, () -> generate(1, DataFormat.APRS)).getStatusCode().value());
        server.verify();
    }
    @Test void retriesIncompatibleAprsOutput() {
        fortune("Café"); fortune("Luck"); assertEquals("Luck", generate(4, DataFormat.APRS)); server.verify();
    }
    @Test void unicodeUsesCodePoints() {
        fortune("😀"); assertEquals("😀", generate(1, DataFormat.UNICODE)); server.verify();
    }
    @Test void metadataFailureDoesNotRetry() {
        server.expect(requestTo(FortuneCookieGenerator.BASE_URL + "/")).andRespond(withSuccess("{\"data\":{\"message\":\"Service error\"},\"meta\":{\"status\":500}}", MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> generate(64, DataFormat.UNICODE)).getStatusCode().value());
        server.verify();
    }
    @Test void serviceFailureDoesNotRetry() {
        server.expect(requestTo(FortuneCookieGenerator.BASE_URL + "/")).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> generate(64, DataFormat.UNICODE)).getStatusCode().value()); server.verify();
    }
    @Test void malformedResponseDoesNotRetry() {
        server.expect(requestTo(FortuneCookieGenerator.BASE_URL + "/")).andRespond(withSuccess("{\"message\":\"Service error\"}", MediaType.APPLICATION_JSON));
        assertEquals(502, assertThrows(ResponseStatusException.class, () -> generate(64, DataFormat.UNICODE)).getStatusCode().value()); server.verify();
    }
}
