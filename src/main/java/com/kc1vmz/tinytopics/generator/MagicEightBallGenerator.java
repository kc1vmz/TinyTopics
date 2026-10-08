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

import com.kc1vmz.tinytopics.model.DataFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class MagicEightBallGenerator implements TinyTopicGenerator {
    @Override public java.util.List<String> parameterNames() { return java.util.List.of(); }

    @Override public com.kc1vmz.tinytopics.model.ContentSource contentSource() { return com.kc1vmz.tinytopics.model.ContentSource.Local; }

    static final List<String> PHRASES = List.of(
            "It is certain", "It is decidedly so", "Without a doubt", "Yes definitely",
            "You may rely on it", "As I see it, yes", "Most likely", "Outlook good", "Yes",
            "Signs point to yes", "Reply hazy, try again", "Ask again later",
            "Better not tell you now", "Cannot predict now", "Concentrate and ask again",
            "Don't count on it", "My reply is no", "My sources say no",
            "Outlook not so good", "Very doubtful");

    @Override public String name() { return "magic-8-ball"; }

    @Override
    public String generate(int length, DataFormat format, Map<String,String> parameters, Map<String,String> settings) {
        return PHRASES.get(ThreadLocalRandom.current().nextInt(PHRASES.size()));
    }
}


