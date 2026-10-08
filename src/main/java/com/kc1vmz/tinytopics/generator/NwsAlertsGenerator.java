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
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class NwsAlertsGenerator implements TinyTopicGenerator {
    @Override public java.util.List<String> parameterNames() { return java.util.List.of("x", "y"); }

    @Override public com.kc1vmz.tinytopics.model.ContentSource contentSource() { return com.kc1vmz.tinytopics.model.ContentSource.Internet; }

    private final RestClient client;

    @Autowired
    public NwsAlertsGenerator(@Value("${tt.nws.user-agent}") String userAgent) {
        this(NwsForecastGenerator.createClient(userAgent));
    }

    NwsAlertsGenerator(RestClient client) { this.client = client; }

    @Override
    public String name() { return "nws-alerts"; }

    @Override
    public String generate(int length, DataFormat format, Map<String,String> parameters, Map<String,String> settings) {
        String longitude = coordinate("x", 180, parameters, settings);
        String latitude = coordinate("y", 90, parameters, settings);
        JsonNode response;
        try {
            response = client.get().uri("/alerts/active?point=" + latitude + "," + longitude).retrieve().body(JsonNode.class);
        } catch (Exception ex) {
            throw error(HttpStatus.BAD_GATEWAY, "NWS alert service is unavailable or returned invalid data");
        }
        if (response == null || !response.path("features").isArray())
            throw error(HttpStatus.BAD_GATEWAY, "NWS returned invalid alerts");
        if (!response.path("pagination").path("next").asText().isBlank())
            throw error(HttpStatus.BAD_GATEWAY, "NWS returned a paginated alert list; cannot return a complete summary");
        var headlines = new ArrayList<String>();
        var summaries = new ArrayList<String>();
        var events = new ArrayList<String>();
        int headlinesCount = 0;
        int summariesCount = 0;
        for (JsonNode feature : response.path("features")) {
            JsonNode alert = feature.path("properties");
            String event = clean(alert.path("event").asText());
            String area = clean(alert.path("areaDesc").asText());
            String severity = clean(alert.path("severity").asText());
            String certainty = clean(alert.path("certainty").asText());
            if (event.isBlank() || area.isBlank())
                throw error(HttpStatus.BAD_GATEWAY, "NWS returned an incomplete alert");
            String summary = event + "/" + severity+"/"+certainty;
            String headline = clean(alert.path("headline").asText());
            headline = headline.isBlank() ? summary : headline + " [" + area + "]";
            headlines.add(headline);
            headlinesCount += headline.length();
            summaries.add(summary);
            summariesCount += summary.length();
            events.add(event);
        }
        if (summaries.isEmpty()) return null;

        String prefix = "Alerts (" + summaries.size() + "): ";
        if ((headlinesCount + headlines.size() + prefix.length()) <= length) {
            // do it all
            String candidate = prefix+ String.join(" ", headlines);
            if (format != DataFormat.APRS || candidate.codePoints().allMatch(c -> c >= 32 && c <= 126 && c != '{')) {
                return candidate;
            }
        }

        if ((summariesCount + (summaries.size()-1) + prefix.length()) <= length) {
            String candidate = prefix + String.join(" ", summaries);
            if (format != DataFormat.APRS || candidate.codePoints().allMatch(c -> c >= 32 && c <= 126 && c != '{')) {
                return candidate;
            }
        }

        // find smallest
        int smallestCount = 0;
        String candidate = "";
        for (String compactValue : events) {
            if (smallestCount + compactValue.length() + 1 < length) {
                if (candidate.length() > 0) {
                    candidate += " ";
                }
                candidate += compactValue;
                smallestCount = candidate.length();
            }
        }

        if (candidate.length() == 0) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Today's forecast cannot fit the requested length and format");
        } 
        return candidate;
    }

    private static String coordinate(String key, int limit, Map<String,String> parameters, Map<String,String> settings) {
        String value = parameters.get(key);
        if (value == null || value.isBlank()) value = settings.get(key);
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Missing NWS parameter or saved setting: " + key);
        try {
            var number = new java.math.BigDecimal(value.trim());
            if (number.abs().compareTo(java.math.BigDecimal.valueOf(limit)) > 0) throw new NumberFormatException();
            return number.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw error(HttpStatus.BAD_REQUEST, key + " must be a valid " + (key.equals("x") ? "longitude" : "latitude"));
        }
    }
    private static String clean(String value) { return value.replaceAll("\\s+", " ").trim(); }
    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}



