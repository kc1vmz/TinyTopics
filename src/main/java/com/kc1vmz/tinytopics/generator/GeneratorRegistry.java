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
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class GeneratorRegistry {
    private final Map<String,TinyTopicGenerator> generators = new TreeMap<>();
    public GeneratorRegistry(List<TinyTopicGenerator> implementations) {
        for (var generator : implementations)
            if (generators.putIfAbsent(generator.name(), generator) != null)
                throw new IllegalStateException("Duplicate generator: " + generator.name());
    }
    public List<String> names() { return List.copyOf(generators.keySet()); }
    public Map<String,List<String>> parameterNames() {
        var names = new TreeMap<String,List<String>>();
        generators.forEach((name,generator) -> names.put(name,List.copyOf(generator.parameterNames())));
        return names;
    }
    public TinyTopicGenerator get(String name) { return name==null ? null : generators.get(name); }
}
