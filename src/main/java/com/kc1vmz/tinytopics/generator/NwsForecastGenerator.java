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
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** x is geographic longitude and y is geographic latitude, not NWS grid coordinates. */
@Component
public class NwsForecastGenerator implements TinyTopicGenerator {
    @Override public java.util.List<String> parameterNames() { return java.util.List.of("wfo", "x", "y", "dayOffset"); }

    @Override public com.kc1vmz.tinytopics.model.ContentSource contentSource() { return com.kc1vmz.tinytopics.model.ContentSource.Internet; }

    private final RestClient client;
    private final Clock clock;

    @Autowired
    public NwsForecastGenerator(@Value("${tt.nws.user-agent}") String userAgent) {
        this(createClient(userAgent), Clock.systemUTC());
    }

    NwsForecastGenerator(RestClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    static RestClient createClient(String userAgent) {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl("https://api.weather.gov")
                .requestFactory(factory).defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept", "application/geo+json").build();
    }

    @Override
    public String name() { return "nws-forecast"; }

    @Override
    public String generate(int length, DataFormat format, Map<String, String> parameters,
            Map<String, String> settings) {
        int dayOffset = dayOffset(parameters, settings);
        String wfo = value("wfo", parameters, settings).toUpperCase(Locale.ROOT);
        if (!wfo.matches("[A-Z]{3}")) throw error(HttpStatus.BAD_REQUEST, "wfo must be a three-letter NWS office code");
        String longitude = coordinate("x", 180, parameters, settings);
        String latitude = coordinate("y", 90, parameters, settings);
        JsonNode point = fetch("/points/" + latitude + "," + longitude).path("properties");
        String office = point.path("gridId").asText();
        if (!wfo.equalsIgnoreCase(office))
            throw error(HttpStatus.BAD_REQUEST, "wfo does not match the location; expected " + office);
        if (!point.path("gridX").canConvertToInt() || !point.path("gridY").canConvertToInt())
            throw error(HttpStatus.BAD_GATEWAY, "NWS returned invalid grid coordinates");
        ZoneId zone;
        try { zone = ZoneId.of(point.path("timeZone").asText()); }
        catch (DateTimeException ex) { throw error(HttpStatus.BAD_GATEWAY, "NWS returned an invalid location time zone"); }
        Instant now = clock.instant();
        LocalDate forecastDate = now.atZone(zone).toLocalDate().plusDays(dayOffset);
        Instant dayStart = forecastDate.atStartOfDay(zone).toInstant();
        Instant dayEnd = forecastDate.plusDays(1).atStartOfDay(zone).toInstant();
        String path = "/gridpoints/" + wfo + "/" + point.path("gridX").asInt() + ","
                + point.path("gridY").asInt() + "/forecast";
        JsonNode periods = fetch(path).path("properties").path("periods");
        if (!periods.isArray()) throw error(HttpStatus.BAD_GATEWAY, "NWS returned invalid forecast periods");
        var detailed = new ArrayList<String>();
        var compact = new ArrayList<String>();
        int detailedCount = 0;
        int compactCount = 0;
        for (JsonNode period : periods) {
            Instant start, end;
            try {
                start = OffsetDateTime.parse(period.path("startTime").asText()).toInstant();
                end = OffsetDateTime.parse(period.path("endTime").asText()).toInstant();
            } catch (DateTimeException ex) { throw error(HttpStatus.BAD_GATEWAY, "NWS returned invalid forecast times"); }
            // Today includes the ongoing overnight period. Future days start with that day's periods,
            // rather than the previous evening's forecast; include the selected day's night as well.
            if ((end.isBefore(dayStart)) || start.isAfter(dayEnd)) continue;
            String label = period.path("name").asText();
            if (label.startsWith("This"))  {
                label = "";
            }
            String forecast = period.path("detailedForecast").asText();
            String shortForecast = period.path("shortForecast").asText();
             if (forecast.isBlank() || shortForecast.isBlank())
                throw error(HttpStatus.BAD_GATEWAY, "NWS returned incomplete forecast text");
            String detailedValue = label + ": " + forecast;
            detailedCount += detailedValue.length();
            detailed.add(detailedValue);
            String temperature = period.path("temperature").asText();
            String unit = period.path("temperatureUnit").asText();
            String wind = (period.path("windDirection").asText() + " " + period.path("windSpeed").asText()).trim();
            String compactValue = label + (label.isBlank() ? "" : (": ")) + shortForecast
                    + (temperature.isBlank() ? "" : " " + temperature + unit)
                    + (wind.isBlank() ? "" : " wind " + wind);
            compact.add(compactValue);
            compactCount += compactValue.length();
        }
        if (detailed.isEmpty()) return null;
        // return everything
        if ((detailedCount + detailed.size()) <= length) {
            String candidate = String.join(" ", detailed);
            if (format != DataFormat.APRS || candidate.codePoints().allMatch(c -> c >= 32 && c <= 126 && c != '{')) {
                // return max if possible
                return candidate;
            }
        }
        // something smaller
        if ((compactCount + (compact.size()-1)) <= length) {
            String candidate = String.join(" ", compact);
            if (format != DataFormat.APRS || candidate.codePoints().allMatch(c -> c >= 32 && c <= 126 && c != '{')) {
                // return max if possible
                return candidate;
            }
        }
        // find smallest
        int smallestCount = 0;
        String candidate = "";
        for (String compactValue : compact) {
            if (smallestCount + compactValue.length() + 1 < length) {
                if (candidate.length() > 0) {
                    candidate += " ";
                }
                candidate += compactValue;
                smallestCount = candidate.length();
            }
        }

        if (candidate.length() == 0) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Forecast for " + forecastDate + " cannot fit the requested length and format");
        } 
        return candidate;
    }

    private JsonNode fetch(String path) {
        try {
            JsonNode body = client.get().uri(path).retrieve().body(JsonNode.class);
            if (body == null) throw error(HttpStatus.BAD_GATEWAY, "NWS returned an empty response");
            return body;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw error(HttpStatus.BAD_GATEWAY, "NWS forecast service is unavailable or returned invalid data"); }
    }

    private String coordinate(String key, int limit, Map<String, String> parameters, Map<String, String> settings) {
        String input = value(key, parameters, settings);
        try {
            var number = new BigDecimal(input);
            if (number.abs().compareTo(BigDecimal.valueOf(limit)) > 0) throw new NumberFormatException();
            // NWS point coordinates accept up to four decimal places.
            return number.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString();
        } catch (NumberFormatException | ArithmeticException ex) {
            throw error(HttpStatus.BAD_REQUEST, key + " must be a valid " + (key.equals("x") ? "longitude" : "latitude"));
        }
    }

    private String value(String key, Map<String, String> parameters, Map<String, String> settings) {
        String value = parameters.get(key);
        if (value == null || value.isBlank()) value = settings.get(key);
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Missing NWS parameter or saved setting: " + key);
        return value.trim();
    }

    private int dayOffset(Map<String,String> parameters, Map<String,String> settings) {
        String input = parameters.get("dayOffset");
        if (input == null || input.isBlank()) input = settings.get("dayOffset");
        if (input == null || input.isBlank()) return 0;
        try {
            int offset = Integer.parseInt(input.trim());
            if (offset < 0) throw new NumberFormatException();
            return offset;
        } catch (NumberFormatException ex) {
            throw error(HttpStatus.BAD_REQUEST, "dayOffset must be a nonnegative integer (0=today, 1=tomorrow)");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}

