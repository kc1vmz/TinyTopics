package com.kc1vmz.tinytopics.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.kc1vmz.tinytopics.model.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AqiForecastGenerator implements TinyTopicGenerator {
    private final RestClient client;
    private final Clock clock;
    @Autowired public AqiForecastGenerator() { this(createClient(),Clock.systemUTC()); }
    AqiForecastGenerator(RestClient client,Clock clock) { this.client=client; this.clock=clock; }
    private static RestClient createClient() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl("https://api.waqi.info").requestFactory(factory)
                .defaultHeader("Accept","application/json").build();
    }
    @Override public String name() { return "aqi-forecast"; }
    @Override public ContentSource contentSource() { return ContentSource.Internet; }
    @Override public List<String> parameterNames() { return List.of("x","y","dayOffset","maxAgeHours"); }
    @Override public Set<String> secretSettingNames() { return Set.of("token"); }

    @Override public List<Attribution> attributions(Map<String,String> settings) {
        JsonNode data=feed(Map.of(),settings); // Always saved location, never request overrides.
        JsonNode credits=data.path("attributions");
        if(!credits.isArray()) throw error(HttpStatus.BAD_GATEWAY,"AQI feed has no attribution list");
        var result=new LinkedHashSet<Attribution>();
        for(var credit:credits) {
            String name=credit.path("name").asText().trim(), url=credit.path("url").asText().trim();
            try {
                URI uri=URI.create(url);
                if(name.isBlank() || uri.getHost()==null || !Set.of("http","https").contains(uri.getScheme())) throw new IllegalArgumentException();
            } catch(IllegalArgumentException ex) { throw error(HttpStatus.BAD_GATEWAY,"AQI feed returned invalid attribution"); }
            result.add(new Attribution(name,url));
        }
        if(result.stream().noneMatch(a -> URI.create(a.url()).getHost().equals("waqi.info")))
            result.add(new Attribution("World Air Quality Index Project","https://waqi.info/"));
        return List.copyOf(result);
    }

    @Override public String generate(int length,DataFormat format,Map<String,String> parameters,Map<String,String> settings) {
        int offset=integer("dayOffset",1,0,parameters,settings);
        int maxAge=integer("maxAgeHours",2,1,parameters,settings);
        JsonNode data=feed(parameters,settings);
        String aqi=number(data.path("aqi"));
        if(aqi==null) throw error(HttpStatus.BAD_GATEWAY,"AQI feed has no valid current AQI");
        String currentAqi=aqi+" ("+level(new BigDecimal(aqi))+")";
        OffsetDateTime measured;
        try { measured=OffsetDateTime.parse(data.path("time").path("iso").asText()); }
        catch(DateTimeException ex) { throw error(HttpStatus.BAD_GATEWAY,"AQI feed has no valid observation timestamp"); }
        Instant now=clock.instant();
        if(measured.toInstant().isBefore(now.minus(Duration.ofHours(maxAge))))
            throw error(HttpStatus.BAD_GATEWAY,"Current AQI reading is older than the configured maximum age");
        if(measured.toInstant().isAfter(now.plusSeconds(300))) throw error(HttpStatus.BAD_GATEWAY,"AQI observation timestamp is in the future");
        LocalDate date=now.atOffset(measured.getOffset()).toLocalDate().plusDays(offset);
        JsonNode daily=data.path("forecast").path("daily");
        String pm25=forecast(daily,"pm25",date), ozone=forecast(daily,"o3",date), uvi=forecast(daily,"uvi",date);
        String verbose="Current AQI: "+currentAqi+". Forecast for "+date.format(DateTimeFormatter.ofPattern("MMM d",Locale.ENGLISH))+": "
            +forecastPhrase("PM2.5 AQI","PM2.5",pm25)+"; "
            +forecastPhrase("ozone AQI","ozone",ozone)+"; "
            +forecastPhrase("UV index","UV index",uvi)+".";
        if(length>63 && verbose.length()<=length) return verbose;
        String text="AQI "+currentAqi+" now; "+date.format(DateTimeFormatter.ofPattern("MM/dd"))+" max PM2.5 "
            +pm25+" O3 "+ozone+" UVI "+uvi;
        if(text.length()>Math.min(length,63)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"AQI summary cannot fit the requested length");
        return text;
    }

    private static String forecastPhrase(String label,String unavailableLabel,String value) {
        return value.equals("N/A") ? unavailableLabel+" forecast unavailable" : "maximum "+label+" "+value;
    }
    static String level(BigDecimal aqi) {
        if(aqi.compareTo(BigDecimal.valueOf(50))<=0) return "Good";
        if(aqi.compareTo(BigDecimal.valueOf(100))<=0) return "Moderate";
        if(aqi.compareTo(BigDecimal.valueOf(150))<=0) return "Unhealthy/Sen";
        if(aqi.compareTo(BigDecimal.valueOf(200))<=0) return "Unhealthy";
        if(aqi.compareTo(BigDecimal.valueOf(300))<=0) return "V Unhealthy";
        return "Hazardous";
    }

    private String forecast(JsonNode daily,String pollutant,LocalDate date) {
        JsonNode entries=daily.path(pollutant);
        if(entries.isMissingNode() || entries.isNull()) return "N/A";
        if(!entries.isArray()) throw error(HttpStatus.BAD_GATEWAY,"AQI feed returned an invalid forecast list");
        for(var entry:entries) if(date.toString().equals(entry.path("day").asText())) {
            String value=number(entry.path("max")); return value==null ? "N/A" : value;
        }
        return "N/A";
    }
    private static String number(JsonNode node) {
        if(!node.isNumber()) return null;
        BigDecimal value=node.decimalValue();
        return value.signum()<0 ? null : value.stripTrailingZeros().toPlainString();
    }
    private JsonNode feed(Map<String,String> parameters,Map<String,String> settings) {
        String x=coordinate("x",180,parameters,settings), y=coordinate("y",90,parameters,settings);
        String token=settings.get("token");
        if(token==null || token.isBlank() || token.equals("***")) throw error(HttpStatus.BAD_REQUEST,"A saved AQI token is required");
        try {
            JsonNode response=client.get().uri(uri -> uri.path("/feed/geo:"+y+";"+x+"/").queryParam("token","{token}").build(token)).retrieve().body(JsonNode.class);
            if(response==null || !"ok".equals(response.path("status").asText()) || !response.path("data").isObject())
                throw error(HttpStatus.BAD_GATEWAY,"AQI service rejected the request or returned invalid data");
            return response.path("data");
        } catch(ResponseStatusException ex) { throw ex; }
        catch(Exception ex) { throw error(HttpStatus.BAD_GATEWAY,"AQI service is unavailable or rejected the request"); }
    }
    private static String coordinate(String name,int bound,Map<String,String> parameters,Map<String,String> settings) {
        String raw=value(name,parameters,settings);
        try {
            BigDecimal value=new BigDecimal(raw==null ? "" : raw.trim());
            if(value.abs().compareTo(BigDecimal.valueOf(bound))>0) throw new NumberFormatException();
            return value.setScale(6,java.math.RoundingMode.HALF_UP).toPlainString();
        } catch(NumberFormatException|ArithmeticException ex) { throw error(HttpStatus.BAD_REQUEST,name+" must be a valid "+(name.equals("x") ? "longitude" : "latitude")); }
    }
    private static int integer(String key,int fallback,int minimum,Map<String,String> parameters,Map<String,String> settings) {
        String value=value(key,parameters,settings);
        if(value==null) return fallback;
        try { int number=Integer.parseInt(value.trim()); if(number<minimum) throw new NumberFormatException(); return number; }
        catch(NumberFormatException ex) { throw error(HttpStatus.BAD_REQUEST,key+" must be an integer of at least "+minimum); }
    }
    private static String value(String key,Map<String,String> parameters,Map<String,String> settings) {
        String value=parameters.get(key); if(value==null || value.isBlank()) value=settings.get(key);
        return value==null || value.isBlank() ? null : value;
    }
    private static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
