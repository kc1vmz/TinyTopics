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

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpPortConfiguration {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> administrationConnector(
            @Value("${server.port}") int readPort, @Value("${tt.admin-http-port}") int adminPort) {
        validatePorts(readPort, adminPort);
        return factory -> {
            var connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(adminPort);
            factory.addAdditionalTomcatConnectors(connector);
        };
    }

    static void validatePorts(int readPort, int adminPort) {
        if (readPort < 1 || readPort > 65535 || adminPort < 1 || adminPort > 65535 || readPort == adminPort)
            throw new IllegalArgumentException("Read and administration ports must be distinct integers from 1 to 65535");
    }
}
