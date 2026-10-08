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

import java.time.Instant;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="Transient result snapshot. Errors never appear in the text field.")
public record TinyTopicResult(
        @Schema(example="welcome") String topicId,
        @Schema(description="UTC request timestamp") Instant requestedAt,
        @Schema(description="Supplied topic parameters, excluding length and format") Map<String, String> parameters,
        Status status,
        @Schema(description="Selected/generated text for CONTENT; null for NO_CONTENT or ERROR", nullable=true, example="Welcome!") String text,
        @Schema(description="Failure explanation for ERROR; otherwise null", nullable=true) String errorMessage) {
    public enum Status { CONTENT, NO_CONTENT, ERROR }
}
