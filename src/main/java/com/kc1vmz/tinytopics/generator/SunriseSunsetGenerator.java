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

import com.fasterxml.jackson.databind.JsonNode;
import com.kc1vmz.tinytopics.model.DataFormat;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SunriseSunsetGenerator implements TinyTopicGenerator {
    @Override public java.util.List<String> parameterNames() { return java.util.List.of("x", "y"); }

    @Override public com.kc1vmz.tinytopics.model.ContentSource contentSource() { return com.kc1vmz.tinytopics.model.ContentSource.Internet; }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final RestClient client;
    private final Clock clock;

    @Autowired
    public SunriseSunsetGenerator() { this(createClient(), Clock.systemUTC()); }

    SunriseSunsetGenerator(RestClient client, Clock clock) { this.client = client; this.clock = clock; }

    private static RestClient createClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl("https://aa.usno.navy.mil").requestFactory(factory)
                .defaultHeader("User-Agent", "TinyTopics/1.0.0").defaultHeader("Accept", "application/json").build();
    }

    @Override public String name() { return "sunrise-sunset"; }

    @Override
    public String generate(int length, DataFormat format, Map<String,String> parameters, Map<String,String> settings) {
        String longitude = coordinate("x", 180, parameters, settings);
        String latitude = coordinate("y", 90, parameters, settings);
        LocalDate date = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        JsonNode response;
        try {
            response = client.get().uri(uri -> uri.path("/api/rstt/oneday").queryParam("date", date)
                    .queryParam("coords", latitude + "," + longitude).queryParam("tz", 0)
                    .queryParam("dst", false).build()).retrieve().body(JsonNode.class);
        } catch (Exception ex) { throw error(HttpStatus.BAD_GATEWAY, "USNO service is unavailable or returned invalid data"); }
        if (response == null || response.has("error"))
            throw error(HttpStatus.BAD_GATEWAY, "USNO service returned an error");
        try {
            JsonNode data = response.path("properties").path("data");
            if (!data.path("tz").isNumber() || data.path("tz").asDouble() != 0 || data.path("isdst").asBoolean())
                throw new IllegalArgumentException("USNO response is not GMT");
            LocalDate returnedDate = LocalDate.of(data.path("year").asInt(), data.path("month").asInt(), data.path("day").asInt());
            if (!returnedDate.equals(date)) throw new IllegalArgumentException("USNO response date does not match");
            String text = "Sun rise " + event(data.path("sundata"), "Rise") + " set " + event(data.path("sundata"), "Set")
                    + " Moon rise " + event(data.path("moondata"), "Rise") + " set " + event(data.path("moondata"), "Set");
            if (text.codePointCount(0, text.length()) > length)
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Sun and Moon rise/set times cannot fit the requested length");
            // Output uses only ASCII digits, punctuation, and labels for both formats.
            return text;
        } catch (DateTimeException | IllegalArgumentException ex) {
            throw error(HttpStatus.BAD_GATEWAY, "USNO service returned invalid dates or times");
        }
    }

    private static String event(JsonNode events, String phenomenon) {
        if (!events.isArray()) throw new IllegalArgumentException("Missing event list");
        for (JsonNode event : events) {
            if (!phenomenon.equals(event.path("phen").asText())) continue;
            JsonNode value = event.get("time");
            if (value == null) throw new IllegalArgumentException("Missing event time");
            if (value.isNull() || "null".equals(value.asText())) return "none";
            return TIME.format(LocalTime.parse(value.asText())) + "Z";
        }
        return "none";
    }

    private static String coordinate(String key, int limit, Map<String,String> parameters, Map<String,String> settings) {
        String value = parameters.get(key);
        if (value == null || value.isBlank()) value = settings.get(key);
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Missing parameter or saved setting: " + key);
        try {
            var number = new BigDecimal(value.trim());
            if (number.abs().compareTo(BigDecimal.valueOf(limit)) > 0) throw new NumberFormatException();
            return number.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw error(HttpStatus.BAD_REQUEST, key + " must be a valid " + (key.equals("x") ? "longitude" : "latitude"));
        }
    }
    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}


