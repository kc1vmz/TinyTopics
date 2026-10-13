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
package com.kc1vmz.tinytopics.generator;

import java.util.Map;
import com.kc1vmz.tinytopics.model.DataFormat;

public interface TinyTopicGenerator {
    com.kc1vmz.tinytopics.model.ContentSource contentSource();
    String name();
    java.util.List<String> parameterNames();
    default java.util.List<com.kc1vmz.tinytopics.model.Attribution> attributions(Map<String,String> settings) { return java.util.List.of(); }
    default java.util.Set<String> secretSettingNames() { return java.util.Set.of(); }
    // Null denotes intentional no-content; exceptions denote generation failure.
    String generate(int length, DataFormat format, Map<String, String> parameters, Map<String, String> settings);
}
