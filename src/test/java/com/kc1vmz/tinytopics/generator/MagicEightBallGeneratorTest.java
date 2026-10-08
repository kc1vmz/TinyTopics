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
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MagicEightBallGeneratorTest {
    @Test void responsesAreRecognizedAndAprsCompatible() {
        var generator = new MagicEightBallGenerator();
        assertEquals(20, MagicEightBallGenerator.PHRASES.stream().distinct().count());
        assertTrue(MagicEightBallGenerator.PHRASES.stream().allMatch(text ->
                text.codePoints().allMatch(c -> c >= 32 && c <= 126 && c != '{') && text.length() <= 64));
        for (int i = 0; i < 100; i++)
            assertTrue(MagicEightBallGenerator.PHRASES.contains(generator.generate(64, DataFormat.APRS, Map.of(), Map.of())));
    }
}
