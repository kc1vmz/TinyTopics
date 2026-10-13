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
package com.kc1vmz.tinytopics.controller.rest;
import com.kc1vmz.tinytopics.accessor.*;
import com.kc1vmz.tinytopics.generator.GeneratorRegistry;
import com.kc1vmz.tinytopics.model.*;
import java.util.List;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name="Topics", description="Topic configuration and event-time content generation")
public class TopicRestController {
    private final TopicAccessor topics;
    private final ResultAccessor results;
    private final GeneratorRegistry generators;
    public TopicRestController(TopicAccessor topics,ResultAccessor results,GeneratorRegistry generators) { this.topics=topics; this.results=results; this.generators=generators; }
    @Operation(summary="List topic definitions", description="Includes the derived Internet/Local content source and saved-location attributions. Secret settings are masked.")
    @GetMapping("/tinyTopicDefinition") public List<TinyTopicDefinition> list() { return topics.list(); }
    @Operation(summary="Get a topic definition")
    @ApiResponse(responseCode="404", description="Unknown topic", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @GetMapping("/tinyTopicDefinition/{id}") public TinyTopicDefinition get(@PathVariable String id) { return topics.get(id); }
    @Operation(summary="Create a topic", description="Administration port only. Text topics require values; generated topics require a registered generator name.")
    @ApiResponse(responseCode="201", description="Created topic", content=@Content(schema=@Schema(implementation=TinyTopicDefinition.class)))
    @ApiResponse(responseCode="400", description="Invalid definition or JSON", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="409", description="ID already exists", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="405", description="Writes are blocked on the read-only port", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @PostMapping("/tinyTopicDefinition") @ResponseStatus(HttpStatus.CREATED)
    public TinyTopicDefinition create(@RequestBody TinyTopicDefinition topic) { return topics.save(topic,true); }
    @Operation(summary="Replace a topic definition", description="Administration port only. Body ID must match the path; IDs cannot be renamed.")
    @ApiResponse(responseCode="200", description="Updated topic", content=@Content(schema=@Schema(implementation=TinyTopicDefinition.class)))
    @ApiResponse(responseCode="400", description="Invalid definition, JSON, or ID mismatch", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="404", description="Unknown topic", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="405", description="Writes are blocked on the read-only port", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="409", description="Persistence conflict", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @PutMapping("/tinyTopicDefinition/{id}") public TinyTopicDefinition update(@PathVariable String id,@RequestBody TinyTopicDefinition topic) {
        if(!id.equals(topic.id())) throw TopicAccessor.error(HttpStatus.BAD_REQUEST,"Topic IDs cannot be renamed");
        return topics.save(topic,false);
    }
    @Operation(summary="Delete a topic", description="Administration port only.")
    @ApiResponse(responseCode="204", description="Deleted", content=@Content)
    @ApiResponse(responseCode="404", description="Unknown topic", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @ApiResponse(responseCode="405", description="Writes are blocked on the read-only port", content=@Content(schema=@Schema(implementation=ApiError.class)))
    @DeleteMapping("/tinyTopicDefinition/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id) { topics.delete(id); }
    @Operation(summary="List available generator names", description="Choose one of these names when creating a generated topic.")
    @GetMapping("/generators") public List<String> generators() { return generators.names(); }
    @Operation(summary="Request a topic result", description="Create the topic first. length is required and counts Unicode code points. "
            + "format defaults to UNICODE; APRS requires printable ASCII excluding {. "
            + "Only declared topic parameter names are accepted; all are optional and must occur once. "
            + "For NWS/solar/AQI generators use x=longitude and y=latitude; NWS forecast also accepts wfo and dayOffset. "
            + "AQI uses dayOffset=1 by default and maxAgeHours=2; its token must be saved in generatorSettings. "
            + "dayOffset is an optional nonnegative integer, default 0: 0=today, 1=tomorrow, etc., in the location's time zone. "
            + "Generated topic definitions automatically list the selected generator's supported parameterNames. "
            + "Supply generator-specific parameters as the exploded parameters object, e.g. {\"x\":\"-72.97\",\"y\":\"43.61\"}. "
            + "Returned snapshots are not persisted and have Cache-Control: no-store. Output is never truncated.", parameters={
        @Parameter(name="length", in=ParameterIn.QUERY, required=true, description="Maximum output length", example="64", schema=@Schema(type="integer", minimum="1", maximum="4096")),
        @Parameter(name="format", in=ParameterIn.QUERY, description="Output format", schema=@Schema(type="string", allowableValues={"APRS","UNICODE"}, defaultValue="UNICODE")),
        @Parameter(name="parameters", in=ParameterIn.QUERY, style=ParameterStyle.FORM, explode=io.swagger.v3.oas.annotations.enums.Explode.TRUE,
            description="Optional declared generator parameters, serialized as individual query keys. Leave empty for topics without parameters.",
            schema=@Schema(type="object", additionalProperties=Schema.AdditionalPropertiesValue.TRUE))
    })
    @ApiResponse(responseCode="200", description="CONTENT or intentional NO_CONTENT", content=@Content(schema=@Schema(implementation=TinyTopicResult.class)))
    @ApiResponse(responseCode="400", description="Invalid length/format/parameters or generator inputs", content=@Content(schema=@Schema(implementation=TinyTopicResult.class)))
    @ApiResponse(responseCode="404", description="Unknown topic", content=@Content(schema=@Schema(implementation=TinyTopicResult.class)))
    @ApiResponse(responseCode="422", description="Content cannot meet length or format constraints", content=@Content(schema=@Schema(implementation=TinyTopicResult.class)))
    @ApiResponse(responseCode="502", description="Generator or upstream service failure", content=@Content(schema=@Schema(implementation=TinyTopicResult.class)))
    @GetMapping("/tinyTopicResult/{id}") public ResponseEntity<TinyTopicResult> result(@PathVariable String id,
            @Parameter(hidden=true) @RequestParam MultiValueMap<String,String> query) { return results.request(id,query); }
}
