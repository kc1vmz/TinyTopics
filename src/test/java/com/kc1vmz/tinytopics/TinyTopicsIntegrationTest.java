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
package com.kc1vmz.tinytopics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kc1vmz.tinytopics.generator.TinyTopicGenerator;
import com.kc1vmz.tinytopics.model.DataFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:tests;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Import(TinyTopicsIntegrationTest.Generators.class)
class TinyTopicsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired com.kc1vmz.tinytopics.persistence.TopicRepository repository;
    void create(String id,String type,String values,String generator,String settings) throws Exception {
        mvc.perform(post("/api/v1/tinyTopicDefinition").contentType(MediaType.APPLICATION_JSON).content(
            "{\"id\":\""+id+"\",\"description\":\"Test\",\"parameterNames\":[\"optional\"],\"type\":\""+type+"\",\"values\":"+values+",\"generatorName\":"+generator+",\"generatorSettings\":"+settings+"}"))
            .andExpect(status().isCreated());
    }
    @Test void crudAndPages() throws Exception {
        create("crud","text","[\"hello\"]","null","{}");
        mvc.perform(get("/api/v1/tinyTopicDefinition/crud")).andExpect(jsonPath("$.values[0]").value("hello"));
        mvc.perform(post("/api/v1/tinyTopicDefinition").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"crud\",\"type\":\"text\",\"values\":[\"duplicate\"]}")).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/tinyTopicDefinition/crud").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"crud\",\"type\":\"text\",\"values\":[\"changed\"]}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/tinyTopicResult/crud").param("length","7")).andExpect(jsonPath("$.text").value("changed"));
        for(String path:new String[]{"/","/topics/new","/topics/crud","/topics/crud/preview"}) mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/topics/crud/preview").param("length","7")).andExpect(status().isOk());
        mvc.perform(delete("/api/v1/tinyTopicDefinition/crud")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/tinyTopicDefinition/crud")).andExpect(status().isNotFound());
    }
    @Test void validatesRequestAndOutput() throws Exception {
        create("limits","text","[\"abc{\"]","null","{}");
        mvc.perform(get("/api/v1/tinyTopicResult/limits")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("ERROR")).andExpect(jsonPath("$.topicId").value("limits"));
        for(String length:new String[]{"0","4097","bad"}) mvc.perform(get("/api/v1/tinyTopicResult/limits").param("length",length)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tinyTopicResult/limits").param("length","3")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.text").isEmpty());
        mvc.perform(get("/api/v1/tinyTopicResult/limits").param("length","4").param("format","APRS")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/tinyTopicResult/limits").param("length","4")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/tinyTopicResult/limits").param("length","4").param("format","INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tinyTopicResult/limits").param("length","4").param("unknown","value")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tinyTopicResult/limits").param("length","4","5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/tinyTopicResult/missing").param("length","4")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorMessage").exists());
    }
    @Test void generatorsAndContext() throws Exception {
        create("random","generated","[]","\"magic-8-ball\"","{}");
        var result=mvc.perform(get("/api/v1/tinyTopicResult/random").param("length","64").param("format","APRS")).andExpect(status().isOk()).andReturn();
        String text=mapper.readTree(result.getResponse().getContentAsString()).get("text").asText();
        org.junit.jupiter.api.Assertions.assertFalse(text.isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(text.length() <= 64);
        create("context","generated","[]","\"context\"","{\"prefix\":\"saved\"}");
        mvc.perform(get("/api/v1/tinyTopicResult/context").param("length","100").param("format","APRS").param("optional","provided"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("100:APRS:provided:saved")).andExpect(jsonPath("$.parameters.optional").value("provided"));
        create("none","generated","[]","\"none\"","{}");
        mvc.perform(get("/api/v1/tinyTopicResult/none").param("length","1")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_CONTENT"));
        create("fails","generated","[]","\"fails\"","{}");
        mvc.perform(get("/api/v1/tinyTopicResult/fails").param("length","100")).andExpect(status().isBadGateway()).andExpect(jsonPath("$.text").isEmpty());
        create("emoji","text","[\"😀\"]","null","{}");
        mvc.perform(get("/api/v1/tinyTopicResult/emoji").param("length","1")).andExpect(status().isOk());
    }
    @Test void uiSavesAndValidation() throws Exception {
        mvc.perform(post("/topics").param("id","ui-created").param("type","generated").param("generatorName","magic-8-ball").param("settings","test=value"))
            .andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/tinyTopicDefinition/ui-created")).andExpect(jsonPath("$.generatorSettings.test").value("value"));
        mvc.perform(post("/topics").param("id","INVALID").param("values","hello")).andExpect(status().isOk()).andExpect(model().attributeExists("error"));
        mvc.perform(post("/api/v1/tinyTopicDefinition").contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"bad-generator\",\"type\":\"generated\"}"))
            .andExpect(status().isBadRequest());
    }
    @TestConfiguration static class Generators {
        @Bean org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer adminRequests(
                @org.springframework.beans.factory.annotation.Value("${tt.admin-http-port}") int port) {
            return builder -> builder.defaultRequest(get("/").with(request -> { request.setLocalPort(port); return request; }));
        }
        @Bean TinyTopicGenerator context() { return generator("context",(length,format,parameters,settings)->length+":"+format+":"+parameters.get("optional")+":"+settings.get("prefix")); }
        @Bean TinyTopicGenerator none() { return generator("none",(l,f,p,s)->null); }
        @Bean TinyTopicGenerator fails() { return generator("fails",(l,f,p,s)->{throw new IllegalStateException("internal detail");}); }
        interface Generation { String run(int l,DataFormat f,Map<String,String> p,Map<String,String> s); }
        static TinyTopicGenerator generator(String name,Generation operation) { return new TinyTopicGenerator() {
            public java.util.List<String> parameterNames(){return name.equals("context") ? java.util.List.of("optional") : java.util.List.of();}
            public com.kc1vmz.tinytopics.model.ContentSource contentSource(){return com.kc1vmz.tinytopics.model.ContentSource.Local;}
            public String name(){return name;}
            public String generate(int l,DataFormat f,Map<String,String> p,Map<String,String> s){return operation.run(l,f,p,s);}
        }; }
    }
    @Test void contentSourcesAreDerivedAndListed() throws Exception {
        String text="{\"id\":\"source-text\",\"type\":\"text\",\"values\":[\"Hello\"],\"contentSource\":\"Internet\"}";
        mvc.perform(post("/api/v1/tinyTopicDefinition").contentType(MediaType.APPLICATION_JSON).content(text))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.contentSource").value("Local"));
        create("source-web","generated","[]","\"fortune-cookie\"","{}");
        mvc.perform(get("/api/v1/tinyTopicDefinition/source-web")).andExpect(jsonPath("$.contentSource").value("Internet"));
        mvc.perform(put("/api/v1/tinyTopicDefinition/source-web").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"source-web\",\"type\":\"generated\",\"generatorName\":\"magic-8-ball\",\"contentSource\":\"Internet\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.contentSource").value("Local"));
        repository.saveAndFlush(new com.kc1vmz.tinytopics.persistence.TopicEntity("legacy-source",
            "{\"id\":\"legacy-source\",\"type\":\"generated\",\"generatorName\":\"nws-alerts\",\"parameterNames\":[],\"values\":[],\"generatorSettings\":{}}"));
        mvc.perform(get("/api/v1/tinyTopicDefinition/legacy-source")).andExpect(status().isOk()).andExpect(jsonPath("$.contentSource").value("Internet"));
        mvc.perform(get("/api/v1/tinyTopicDefinition"))
            .andExpect(jsonPath("$[?(@.id == 'source-text')].contentSource").value(org.hamcrest.Matchers.contains("Local")))
            .andExpect(jsonPath("$[?(@.id == 'legacy-source')].contentSource").value(org.hamcrest.Matchers.contains("Internet")));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Content source")));
    }
    @Test void generatorParametersAreAuthoritativeInResponsesAndRequests() throws Exception {
        mvc.perform(post("/api/v1/tinyTopicDefinition").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"auto-forecast\",\"type\":\"generated\",\"generatorName\":\"nws-forecast\",\"parameterNames\":[\"obsolete\"]}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.parameterNames").value(org.hamcrest.Matchers.contains("wfo","x","y","dayOffset")));
        mvc.perform(get("/api/v1/tinyTopicDefinition/auto-forecast"))
            .andExpect(jsonPath("$.parameterNames").value(org.hamcrest.Matchers.contains("wfo","x","y","dayOffset")));
        mvc.perform(get("/api/v1/tinyTopicDefinition"))
            .andExpect(jsonPath("$[?(@.id == 'auto-forecast')].parameterNames[0]").value(org.hamcrest.Matchers.contains("wfo")));
        // Reaching generator-specific validation proves dayOffset is accepted even when absent from the submitted definition.
        mvc.perform(get("/api/v1/tinyTopicResult/auto-forecast").param("length","64").param("dayOffset","-1"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorMessage").value(org.hamcrest.Matchers.containsString("dayOffset")));
        mvc.perform(put("/api/v1/tinyTopicDefinition/auto-forecast").contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"auto-forecast\",\"type\":\"generated\",\"generatorName\":\"magic-8-ball\",\"parameterNames\":[\"wfo\",\"x\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.parameterNames").isEmpty());
        repository.saveAndFlush(new com.kc1vmz.tinytopics.persistence.TopicEntity("legacy-parameters",
            "{\"id\":\"legacy-parameters\",\"type\":\"generated\",\"generatorName\":\"nws-alerts\",\"parameterNames\":[\"state\"],\"values\":[],\"generatorSettings\":{}}"));
        mvc.perform(get("/api/v1/tinyTopicDefinition/legacy-parameters"))
            .andExpect(jsonPath("$.parameterNames").value(org.hamcrest.Matchers.contains("x","y")));
        mvc.perform(get("/topics/new")).andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Parameter Names")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("data-parameter-names=\"wfo,x,y,dayOffset\"")));
    }
}
