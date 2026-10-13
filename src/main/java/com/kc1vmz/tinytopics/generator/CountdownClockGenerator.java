package com.kc1vmz.tinytopics.generator;

import com.kc1vmz.tinytopics.model.ContentSource;
import com.kc1vmz.tinytopics.model.DataFormat;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CountdownClockGenerator implements TinyTopicGenerator {
    private final Clock clock;
    @Autowired public CountdownClockGenerator() { this(Clock.systemUTC()); }
    CountdownClockGenerator(Clock clock) { this.clock = clock; }

    @Override public String name() { return "countdown-clock"; }
    @Override public ContentSource contentSource() { return ContentSource.Local; }
    @Override public List<String> parameterNames() { return List.of("targetTime", "description"); }

    @Override
    public String generate(int length, DataFormat format, Map<String,String> parameters, Map<String,String> settings) {
        String description = value("description", parameters, settings).replaceAll("\\s+", " ").trim();
        Instant target;
        try { target = OffsetDateTime.parse(value("targetTime", parameters, settings)).toInstant(); }
        catch (DateTimeException ex) {
            throw error(HttpStatus.BAD_REQUEST, "targetTime must be an ISO 8601 date/time with Z or an explicit UTC offset");
        }
        Instant now = clock.instant();
        String text;
        if (!target.isAfter(now)) {
            text = "Time has passed for " + description;
        } else {
            Duration remaining = Duration.between(now, target);
            long totalMinutes = remaining.getSeconds() / 60;
            if (remaining.getSeconds() % 60 != 0 || remaining.getNano() != 0) totalMinutes++;
            long days = totalMinutes / 1440;
            long hours = totalMinutes % 1440 / 60;
            long minutes = totalMinutes % 60;
            text = quantity(days, "day") + " " + quantity(hours, "hour") + " "
                    + quantity(minutes, "minute") + " until " + description;
        }
        if (text.codePointCount(0, text.length()) > length)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Countdown message cannot fit the requested length");
        if (format == DataFormat.APRS && text.codePoints().anyMatch(c -> c < 32 || c > 126 || c == '{'))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "Countdown description is not compatible with APRS format");
        return text;
    }

    private static String quantity(long count, String unit) { return count + " " + unit + (count == 1 ? "" : "s"); }
    private static String value(String name, Map<String,String> parameters, Map<String,String> settings) {
        String value = parameters.get(name);
        if (value == null || value.isBlank()) value = settings.get(name);
        if (value == null || value.isBlank()) throw error(HttpStatus.BAD_REQUEST, "Missing parameter or saved setting: " + name);
        return value.trim();
    }
    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
