package com.kc1vmz.tinytopics.generator;

import com.kc1vmz.tinytopics.model.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AqiForecastGeneratorTest {
    @Test void levelLabelsCoverEveryBoundary() {
        int[] values={0,50,51,100,101,150,151,200,201,300,301};
        String[] levels={"Good","Good","Moderate","Moderate","Unhealthy/Sen","Unhealthy/Sen","Unhealthy","Unhealthy","V Unhealthy","V Unhealthy","Hazardous"};
        for(int i=0;i<values.length;i++) assertEquals(levels[i],AqiForecastGenerator.level(java.math.BigDecimal.valueOf(values[i])));
    }
    private MockRestServiceServer server;
    private AqiForecastGenerator generator;
    private static final Map<String,String> SETTINGS=Map.of("x","-72.97","y","43.61","token","test-token");
    private static final String URL="https://api.waqi.info/feed/geo:43.610000;-72.970000/?token=test-token";
    private static final String FEED="""
        {"status":"ok","data":{"aqi":13,"time":{"iso":"2026-10-13T15:00:00-05:00"},
        "iaqi":{"o3":{"v":12.8}},"attributions":[
        {"name":"State of Vermont - Agency of Natural Resources","url":"http://anr.vermont.gov/"},
        {"name":"Air Now - US EPA","url":"http://www.airnow.gov/"},
        {"name":"World Air Quality Index Project","url":"https://waqi.info/"}],
        "forecast":{"daily":{"pm25":[{"day":"2026-10-13","max":63},{"day":"2026-10-14","max":70}]}}}}
        """;
    @BeforeEach void setup() {
        var builder=RestClient.builder().baseUrl("https://api.waqi.info");
        server=MockRestServiceServer.bindTo(builder).build();
        generator=new AqiForecastGenerator(builder.build(),Clock.fixed(Instant.parse("2026-10-13T20:30:00Z"),ZoneOffset.UTC));
    }
    private void feed(String json) { server.expect(requestTo(URL)).andRespond(withSuccess(json,MediaType.APPLICATION_JSON)); }
    @Test void usesTomorrowMaximaAndMissingForecastMarkers() {
        feed(FEED);
        assertEquals("AQI 13 (Good) now; 10/14 max PM2.5 70 O3 N/A UVI N/A",generator.generate(63,DataFormat.APRS,Map.of(),SETTINGS));
        server.verify();
    }
    @Test void offsetOverrideAndAvailableOzoneAndUvi() {
        feed(FEED.replace("\"daily\":{","\"daily\":{\"o3\":[{\"day\":\"2026-10-13\",\"max\":20}],\"uvi\":[{\"day\":\"2026-10-13\",\"max\":3.5}],"));
        assertEquals("AQI 13 (Good) now; 10/13 max PM2.5 63 O3 20 UVI 3.5",generator.generate(63,DataFormat.UNICODE,Map.of("dayOffset","0"),SETTINGS));
        server.verify();
    }
    @Test void creditsComeFromFeedAndNeedNotBeFresh() {
        feed(FEED.replace("2026-10-13T15:00:00-05:00","2026-10-01T15:00:00-05:00"));
        var credits=generator.attributions(SETTINGS);
        assertEquals(3,credits.size()); assertEquals("http://anr.vermont.gov/",credits.getFirst().url()); server.verify();
    }
    @Test void staleReadingFailsButConfigurableAgeAllowsIt() {
        String stale=FEED.replace("15:00:00-05:00","12:00:00-05:00");
        feed(stale); feed(stale);
        assertEquals(502,assertThrows(ResponseStatusException.class,()->generator.generate(63,DataFormat.APRS,Map.of(),SETTINGS)).getStatusCode().value());
        assertTrue(generator.generate(63,DataFormat.APRS,Map.of("maxAgeHours","4"),SETTINGS).startsWith("AQI 13")); server.verify();
    }
    @Test void lengthIsEnforcedWithoutTruncation() {
        feed(FEED);
        assertEquals(422,assertThrows(ResponseStatusException.class,()->generator.generate(10,DataFormat.APRS,Map.of(),SETTINGS)).getStatusCode().value()); server.verify();
    }
    @Test void validatesInputsBeforeNetwork() {
        assertThrows(ResponseStatusException.class,()->generator.generate(63,DataFormat.APRS,Map.of("dayOffset","-1"),SETTINGS));
        assertThrows(ResponseStatusException.class,()->generator.generate(63,DataFormat.APRS,Map.of("maxAgeHours","0"),SETTINGS));
        assertThrows(ResponseStatusException.class,()->generator.generate(63,DataFormat.APRS,Map.of("y","91"),SETTINGS));
        assertThrows(ResponseStatusException.class,()->generator.attributions(Map.of("x","0","y","0"))); server.verify();
    }
    @Test void providerErrorsAreSanitized() {
        feed("{\"status\":\"error\",\"data\":\"Invalid token test-token\"}");
        var ex=assertThrows(ResponseStatusException.class,()->generator.generate(63,DataFormat.APRS,Map.of(),SETTINGS));
        assertEquals(502,ex.getStatusCode().value()); assertFalse(ex.getReason().contains("test-token")); server.verify();
    }
    @Test void locationOverrideOnlyAffectsResultFeed() {
        server.expect(requestTo("https://api.waqi.info/feed/geo:40.000000;-70.000000/?token=test-token"))
            .andRespond(withSuccess(FEED,MediaType.APPLICATION_JSON));
        assertTrue(generator.generate(63,DataFormat.APRS,Map.of("x","-70","y","40"),SETTINGS).startsWith("AQI 13")); server.verify();
    }
    @Test void verboseOutputFitsExactRequestedLength() {
        feed(FEED);
        String expected="Current AQI: 13 (Good). Forecast for Oct 14: maximum PM2.5 AQI 70; ozone forecast unavailable; UV index forecast unavailable.";
        assertEquals(expected,generator.generate(expected.length(),DataFormat.APRS,Map.of(),SETTINGS)); server.verify();
    }
    @Test void fallsBackWhenVerboseExceedsLength() {
        feed(FEED); feed(FEED);
        String verbose="Current AQI: 13 (Good). Forecast for Oct 14: maximum PM2.5 AQI 70; ozone forecast unavailable; UV index forecast unavailable.";
        String compact="AQI 13 (Good) now; 10/14 max PM2.5 70 O3 N/A UVI N/A";
        assertEquals(compact,generator.generate(verbose.length()-1,DataFormat.UNICODE,Map.of(),SETTINGS));
        assertEquals(compact,generator.generate(64,DataFormat.APRS,Map.of(),SETTINGS)); server.verify();
    }
    @Test void verboseOutputShowsAllAvailableForecastValues() {
        feed(FEED.replace("\"daily\":{","\"daily\":{\"o3\":[{\"day\":\"2026-10-14\",\"max\":20}],\"uvi\":[{\"day\":\"2026-10-14\",\"max\":3.5}],"));
        assertEquals("Current AQI: 13 (Good). Forecast for Oct 14: maximum PM2.5 AQI 70; maximum ozone AQI 20; maximum UV index 3.5.",
            generator.generate(256,DataFormat.UNICODE,Map.of(),SETTINGS)); server.verify();
    }
}

