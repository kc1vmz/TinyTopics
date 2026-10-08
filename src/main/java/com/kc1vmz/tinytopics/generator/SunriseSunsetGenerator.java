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

    @Autowired
    public SunriseSunsetGenerator() { this(createClient()); }

    SunriseSunsetGenerator(RestClient client) { this.client = client; }

    private static RestClient createClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl("https://api.sunrise-sunset.org").requestFactory(factory)
                .defaultHeader("User-Agent", "TinyTopics/0.0.1").defaultHeader("Accept", "application/json").build();
    }

    @Override public String name() { return "sunrise-sunset"; }

    @Override
    public String generate(int length, DataFormat format, Map<String,String> parameters, Map<String,String> settings) {
        String longitude = coordinate("x", 180, parameters, settings);
        String latitude = coordinate("y", 90, parameters, settings);
        JsonNode response;
        try {
            response = client.get().uri(uri -> uri.path("/v2").queryParam("lat", latitude)
                    .queryParam("lng", longitude).queryParam("date", "today").build()).retrieve().body(JsonNode.class);
        } catch (Exception ex) { throw error(HttpStatus.BAD_GATEWAY, "Sunrise-Sunset service is unavailable or returned invalid data"); }
        if (response == null || response.has("error"))
            throw error(HttpStatus.BAD_GATEWAY, "Sunrise-Sunset service returned an error");
        try {
            LocalDate date = LocalDate.parse(response.path("date").asText());
            String sunrise = event(response, "sunrise", date);
            String sunset = event(response, "sunset", date);
            String text = "Sunrise " + sunrise + "; sunset " + sunset;
            if (text.codePointCount(0, text.length()) > length)
                throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Sunrise and sunset times cannot fit the requested length");
            // Output uses only ASCII digits, punctuation, and labels for both formats.
            return text;
        } catch (DateTimeException | IllegalArgumentException ex) {
            throw error(HttpStatus.BAD_GATEWAY, "Sunrise-Sunset service returned invalid dates or times");
        }
    }

    private static String event(JsonNode response, String field, LocalDate date) {
        JsonNode value = response.get(field);
        if (value == null) throw new IllegalArgumentException("Missing solar event");
        if (value.isNull()) return "none";
        OffsetDateTime time = OffsetDateTime.parse(value.asText());
        if (!time.toLocalDate().equals(date)) throw new IllegalArgumentException("Event date does not match requested day");
        return TIME.format(time);
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


