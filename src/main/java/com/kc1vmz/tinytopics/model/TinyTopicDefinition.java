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
package com.kc1vmz.tinytopics.model;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="A topic's configuration. Text topics require nonblank values; generated topics require an available generator. IDs cannot be renamed.")
public record TinyTopicDefinition(
        @Schema(description="Immutable topic ID", pattern="[a-z0-9-]{1,40}", minLength=1, maxLength=40, requiredMode=Schema.RequiredMode.REQUIRED, example="welcome") String id,
        @Schema(description="Topic description", example="Welcome messages") String description,
        @Schema(description="Accepted query parameter names. Automatically supplied by the selected generator for generated topics; text topics may configure names. Generator decides how missing values are handled. length and format are reserved.", example="[]") List<String> parameterNames,
        @Schema(requiredMode=Schema.RequiredMode.REQUIRED) TopicType type,
        @Schema(description="Text choices. One is selected randomly before length/format validation.", example="[\"Welcome!\",\"Hello there!\"]") List<String> values,
        @Schema(description="Name returned by GET /api/v1/generators; required for generated topics", example="magic-8-ball") String generatorName,
        @Schema(description="Saved generator configuration/defaults. AQI requires saved x, y, token. Secret token values appear as ***; send *** back unchanged to preserve them on edit. AQI defaults dayOffset=1 and maxAgeHours=2.", example="{}") Map<String, String> generatorSettings,
        @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        @Schema(description="Derived from topic type and generator; cannot be overridden by callers.", accessMode=Schema.AccessMode.READ_ONLY) ContentSource contentSource,
        @Schema(description="Generator credits resolved from saved settings on save; request overrides do not change them.", accessMode=Schema.AccessMode.READ_ONLY) List<Attribution> attributions) {
    public TinyTopicDefinition {
        attributions = attributions == null ? List.of() : List.copyOf(attributions);
    }
    public TinyTopicDefinition(String id, String description, List<String> parameterNames,
            TopicType type, List<String> values, String generatorName, Map<String,String> generatorSettings, ContentSource contentSource) {
        this(id, description, parameterNames, type, values, generatorName, generatorSettings, contentSource, List.of());
    }
    public TinyTopicDefinition(String id, String description, List<String> parameterNames,
            TopicType type, List<String> values, String generatorName, Map<String,String> generatorSettings) {
        this(id, description, parameterNames, type, values, generatorName, generatorSettings, null);
    }
    public enum TopicType { text, generated }
}
