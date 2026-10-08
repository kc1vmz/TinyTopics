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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PortAccessFilter extends OncePerRequestFilter {
    private final int readPort;
    private final int adminPort;
    private final ObjectMapper mapper;

    public PortAccessFilter(@Value("${server.port}") int readPort,
            @Value("${tt.admin-http-port}") int adminPort, ObjectMapper mapper) {
        this.readPort = readPort; this.adminPort = adminPort; this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Use the actual socket listener, never Host or forwarded-port headers.
        int port = request.getLocalPort();
        if (port == adminPort) {
            chain.doFilter(request, response);
            return;
        }
        if (port != readPort) {
            reject(response, 403, "Unrecognized HTTP listener");
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.startsWith("/api/v1/")) {
            reject(response, 404, "The administration UI is available on the administration port");
            return;
        }
        if (!Set.of("GET", "HEAD").contains(request.getMethod())) {
            response.setHeader("Allow", "GET, HEAD");
            reject(response, 405, "This API port is read-only; use the administration port for changes");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), Map.of("errorMessage", message));
    }
}
