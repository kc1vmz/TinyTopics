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
package com.kc1vmz.tinytopics.accessor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kc1vmz.tinytopics.generator.GeneratorRegistry;
import com.kc1vmz.tinytopics.model.TinyTopicDefinition;
import com.kc1vmz.tinytopics.model.TinyTopicDefinition.TopicType;
import com.kc1vmz.tinytopics.persistence.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class TopicAccessor {
    private final TopicRepository repository;
    private final ObjectMapper mapper;
    private final GeneratorRegistry generators;
    public TopicAccessor(TopicRepository repository, ObjectMapper mapper, GeneratorRegistry generators) {
        this.repository=repository; this.mapper=mapper; this.generators=generators;
    }
    public List<TinyTopicDefinition> list() {
        return repository.findAll().stream().map(this::decode).sorted(Comparator.comparing(TinyTopicDefinition::id)).toList();
    }
    public TinyTopicDefinition get(String id) {
        return decode(repository.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND,"Topic not found")));
    }
    public TinyTopicDefinition save(TinyTopicDefinition input, boolean create) {
        var topic=withSource(validate(input));
        if (create && repository.existsById(topic.id())) throw error(HttpStatus.CONFLICT,"Topic already exists");
        if (!create && !repository.existsById(topic.id())) throw error(HttpStatus.NOT_FOUND,"Topic not found");
        try { repository.saveAndFlush(new TopicEntity(topic.id(),mapper.writeValueAsString(topic))); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
        return topic;
    }
    public void delete(String id) { get(id); repository.deleteById(id); }
    private TinyTopicDefinition validate(TinyTopicDefinition t) {
        if (t==null || t.id()==null || !t.id().matches("[a-z0-9-]{1,40}")) throw error(HttpStatus.BAD_REQUEST,"ID must contain 1–40 lowercase letters, digits, or dashes");
        if(t.type()==null) throw error(HttpStatus.BAD_REQUEST,"Topic type is required");
        var generator=t.type()==TopicType.generated ? generators.get(t.generatorName()) : null;
        if(t.type()==TopicType.generated && generator==null) throw error(HttpStatus.BAD_REQUEST,"Choose an available generator");
        var parameters=generator!=null ? generator.parameterNames() : t.parameterNames()==null ? List.<String>of() : t.parameterNames();
        if(parameters.stream().anyMatch(p -> p==null || !p.matches("[A-Za-z][A-Za-z0-9_-]*") || Set.of("length","format").contains(p)) || new HashSet<>(parameters).size()!=parameters.size())
            throw error(HttpStatus.BAD_REQUEST,"Parameter names must be unique identifiers; length and format are reserved");
        var values=t.values()==null ? List.<String>of() : t.values();
        if(t.type()==TopicType.text && (values.isEmpty() || values.stream().anyMatch(v -> v==null || v.isBlank()))) throw error(HttpStatus.BAD_REQUEST,"Text topics require at least one nonblank value");
        if(t.type()==TopicType.generated && generators.get(t.generatorName())==null) throw error(HttpStatus.BAD_REQUEST,"Choose an available generator");
        var settings=t.generatorSettings()==null ? Map.<String,String>of() : t.generatorSettings();
        if(settings.entrySet().stream().anyMatch(e -> e.getKey()==null || e.getKey().isBlank() || e.getValue()==null)) throw error(HttpStatus.BAD_REQUEST,"Generator settings require nonblank keys and string values");
        return new TinyTopicDefinition(t.id(), t.description()==null ? "" : t.description(),List.copyOf(parameters),t.type(),
            t.type()==TopicType.text ? List.copyOf(values) : List.of(),t.type()==TopicType.generated ? t.generatorName() : null,
            t.type()==TopicType.generated ? Map.copyOf(settings) : Map.of());
    }
    private TinyTopicDefinition decode(TopicEntity entity) {
        try { return withSource(mapper.readValue(entity.definitionJson,TinyTopicDefinition.class)); }
        catch(JsonProcessingException ex) { throw new IllegalStateException("Cannot read saved topic",ex); }
    }
    private TinyTopicDefinition withSource(TinyTopicDefinition topic) {
        var generator = topic.type()==TopicType.generated ? generators.get(topic.generatorName()) : null;
        var source = topic.type()==TopicType.text ? com.kc1vmz.tinytopics.model.ContentSource.Local
                : generator==null ? null : generator.contentSource();
        var parameters = generator==null ? topic.parameterNames() : List.copyOf(generator.parameterNames());
        return new TinyTopicDefinition(topic.id(), topic.description(), parameters, topic.type(),
                topic.values(), topic.generatorName(), topic.generatorSettings(), source);
    }
    public static ResponseStatusException error(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
