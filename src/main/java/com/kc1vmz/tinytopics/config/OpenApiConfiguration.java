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
package com.kc1vmz.tinytopics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI tinyTopicsApi(@Value("${server.port}") int readPort, @Value("${tt.admin-http-port}") int adminPort) {
        return new OpenAPI().info(new Info().title("TinyTopics API").version("v1")
                .description("Manage topic definitions and request text or generated content. "
                        + "The read-only listener (port " + readPort + ") allows GET/HEAD under /api/v1/. "
                        + "The administration listener (port " + adminPort + ") provides all operations and this documentation. "
                        + "Try it out uses this administration listener. No authentication is configured. "
                        + "Create a topic before requesting its result; a generator name is not a topic ID."))
                .servers(List.of(new Server().url("/").description("Current administration listener")));
    }
}
