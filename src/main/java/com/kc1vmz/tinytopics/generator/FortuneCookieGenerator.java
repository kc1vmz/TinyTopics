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
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class FortuneCookieGenerator implements TinyTopicGenerator {
    @Override public java.util.List<String> parameterNames() { return java.util.List.of(); }

    @Override public com.kc1vmz.tinytopics.model.ContentSource contentSource() { return com.kc1vmz.tinytopics.model.ContentSource.Internet; }

    static final String BASE_URL = "https://aphorismcookie.herokuapp.com";
    static final int MAX_ATTEMPTS = 6; // Initial request plus five retries.
    private final RestClient client;

    @Autowired
    public FortuneCookieGenerator() {
        this(createClient());
    }

    FortuneCookieGenerator(RestClient client) {
        this.client = client;
    }

    private static RestClient createClient() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(BASE_URL).requestFactory(factory)
                .defaultHeader("Accept", "application/json").build();
    }

    @Override public String name() { return "fortune-cookie"; }

    @Override
    public String generate(int length, DataFormat format, Map<String,String> parameters, Map<String,String> settings) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            JsonNode response;
            try {
                response = client.get().uri("/").retrieve().body(JsonNode.class);
            } catch (RestClientResponseException ex) {
                String reason = switch (ex.getStatusCode().value()) {
                    case 401, 403 -> "Fortune Cookie API denied access";
                    case 429 -> "Fortune Cookie API rate limit exceeded";
                    default -> "Fortune Cookie API returned an HTTP error";
                };
                throw error(HttpStatus.BAD_GATEWAY, reason);
            } catch (Exception ex) {
                throw error(HttpStatus.BAD_GATEWAY, "Fortune Cookie API is unavailable or returned invalid JSON");
            }
            if (response == null || response.has("error") || !response.path("meta").path("status").isIntegralNumber()
                    || response.path("meta").path("status").asInt() != 200
                    || !response.path("data").path("message").isTextual())
                throw error(HttpStatus.BAD_GATEWAY, "Fortune Cookie API returned an invalid fortune payload");
            String text = response.path("data").path("message").asText().replaceAll("\\s+", " ").trim();
            if (text.isBlank()) throw error(HttpStatus.BAD_GATEWAY, "Fortune Cookie API returned an empty fortune");
            if (text.codePointCount(0, text.length()) <= length
                    && (format != DataFormat.APRS || text.codePoints().allMatch(c -> c >= 32 && c <= 126 && c != '{')))
                return text;
        }
        throw error(HttpStatus.UNPROCESSABLE_ENTITY, "No fortune fits the requested length and format after six attempts (five retries)");
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}


