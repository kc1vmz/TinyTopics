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

import com.kc1vmz.tinytopics.generator.GeneratorRegistry;
import com.kc1vmz.tinytopics.model.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResultAccessor {
    private final TopicAccessor topics;
    private final GeneratorRegistry generators;
    public ResultAccessor(TopicAccessor topics,GeneratorRegistry generators) { this.topics=topics; this.generators=generators; }
    public ResponseEntity<TinyTopicResult> request(String id, MultiValueMap<String,String> query) {
        var time=Instant.now();
        var parameters=new LinkedHashMap<String,String>();
        query.forEach((key,values) -> { if(!Set.of("length","format").contains(key)) parameters.put(key,values.getFirst()); });
        try {
            if(query.values().stream().anyMatch(values -> values.size()!=1)) throw TopicAccessor.error(HttpStatus.BAD_REQUEST,"Each query parameter must occur once");
            int length;
            try { length=Integer.parseInt(query.getFirst("length")); }
            catch(Exception ex) { throw TopicAccessor.error(HttpStatus.BAD_REQUEST,"length is required and must be an integer from 1 to 4096"); }
            if(length<1 || length>4096) throw TopicAccessor.error(HttpStatus.BAD_REQUEST,"length must be between 1 and 4096");
            DataFormat format;
            try { format=DataFormat.valueOf(query.getFirst("format")==null ? "UNICODE" : query.getFirst("format")); }
            catch(IllegalArgumentException ex) { throw TopicAccessor.error(HttpStatus.BAD_REQUEST,"format must be APRS or UNICODE"); }
            var topic=topics.get(id);
            if(!topic.parameterNames().containsAll(parameters.keySet())) throw TopicAccessor.error(HttpStatus.BAD_REQUEST,"Unknown topic parameter");
            String text;
            try {
                text=topic.type()==TinyTopicDefinition.TopicType.text
                    ? topic.values().get(ThreadLocalRandom.current().nextInt(topic.values().size()))
                    : generators.get(topic.generatorName()).generate(length,format,Map.copyOf(parameters),topic.generatorSettings());
            } catch(ResponseStatusException ex) { throw ex; }
            catch(Exception ex) { throw TopicAccessor.error(HttpStatus.BAD_GATEWAY,"Generator failed to produce content"); }
            if(text==null || text.isBlank()) return response(HttpStatus.OK,new TinyTopicResult(id,time,parameters,TinyTopicResult.Status.NO_CONTENT,null,null));
            if(text.codePointCount(0,text.length())>length) throw TopicAccessor.error(HttpStatus.UNPROCESSABLE_ENTITY,"Text cannot fit within the requested length");
            if(format==DataFormat.APRS && text.codePoints().anyMatch(c -> c<32 || c>126 || c=='{'))
                throw TopicAccessor.error(HttpStatus.UNPROCESSABLE_ENTITY,"Text is not compatible with APRS format");
            return response(HttpStatus.OK,new TinyTopicResult(id,time,parameters,TinyTopicResult.Status.CONTENT,text,null));
        } catch(ResponseStatusException ex) {
            return response(HttpStatus.valueOf(ex.getStatusCode().value()),new TinyTopicResult(id,time,parameters,TinyTopicResult.Status.ERROR,null,ex.getReason()));
        }
    }
    private ResponseEntity<TinyTopicResult> response(HttpStatus status,TinyTopicResult result) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(result);
    }
}
