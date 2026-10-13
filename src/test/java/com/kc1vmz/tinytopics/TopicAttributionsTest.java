package com.kc1vmz.tinytopics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kc1vmz.tinytopics.accessor.*;
import com.kc1vmz.tinytopics.generator.*;
import com.kc1vmz.tinytopics.model.*;
import com.kc1vmz.tinytopics.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TopicAttributionsTest {
    @Test void creditsPersistSecretsStayMaskedAndOverridesDoNotRefreshCredits() throws Exception {
        var rows=new HashMap<String,TopicEntity>();
        var repo=mock(TopicRepository.class);
        when(repo.existsById(anyString())).thenAnswer(call -> rows.containsKey(call.getArgument(0)));
        when(repo.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.getArgument(0))));
        when(repo.findAll()).thenAnswer(call -> new ArrayList<>(rows.values()));
        when(repo.saveAndFlush(any())).thenAnswer(call -> { TopicEntity entity=call.getArgument(0); rows.put(entity.id,entity); return entity; });
        var requests=new ArrayList<Map<String,String>>();
        TinyTopicGenerator generator=new TinyTopicGenerator() {
            public String name(){return "test-aqi";}
            public ContentSource contentSource(){return ContentSource.Internet;}
            public List<String> parameterNames(){return List.of("x","y");}
            public Set<String> secretSettingNames(){return Set.of("token");}
            public List<Attribution> attributions(Map<String,String> settings){requests.add(settings);return List.of(new Attribution("Agency "+settings.get("x"),"https://example.org/"));}
            public String generate(int length,DataFormat format,Map<String,String> parameters,Map<String,String> settings){assertEquals("secret",settings.get("token"));return "AQI 13";}
        };
        var registry=new GeneratorRegistry(List.of(generator));
        var mapper=new ObjectMapper().findAndRegisterModules();
        var topics=new TopicAccessor(repo,mapper,registry);
        var input=new TinyTopicDefinition("aqi","Test",List.of(),TinyTopicDefinition.TopicType.generated,List.of(),"test-aqi",Map.of("x","0","y","0","token","secret"));
        var saved=topics.save(input,true);
        assertEquals("***",saved.generatorSettings().get("token"));
        assertEquals("Agency 0",saved.attributions().getFirst().name());
        assertEquals(saved.attributions(),topics.get("aqi").attributions());
        assertFalse(mapper.writeValueAsString(topics.list()).contains("secret"));
        // Editing the masked response preserves the stored key and refreshes saved-location credits.
        topics.save(new TinyTopicDefinition("aqi","Edited",List.of(),input.type(),List.of(),input.generatorName(),Map.of("x","1","y","0","token","***")),false);
        assertEquals("secret",requests.get(1).get("token"));
        var query=new LinkedMultiValueMap<String,String>();query.add("length","63");query.add("x","2");
        var results=new ResultAccessor(topics,registry);
        assertEquals("AQI 13",results.request("aqi",query).getBody().text());
        assertEquals(2,requests.size());assertEquals("Agency 1",topics.get("aqi").attributions().getFirst().name());
        assertFalse(mapper.writeValueAsString(results.request("aqi",query).getBody()).contains("attributions"));
        query.add("token","secret");
        assertFalse(mapper.writeValueAsString(results.request("aqi",query).getBody()).contains("secret"));
    }
}
