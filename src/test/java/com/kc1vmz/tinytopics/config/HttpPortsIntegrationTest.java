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

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT, properties={
        "spring.datasource.url=jdbc:h2:mem:port-tests;DB_CLOSE_DELAY=-1", "spring.jpa.hibernate.ddl-auto=create-drop"})
class HttpPortsIntegrationTest {
    private static final int[] PORTS = freePorts();
    private final HttpClient client = HttpClient.newHttpClient();
    @DynamicPropertySource static void ports(DynamicPropertyRegistry registry) {
        registry.add("server.port", () -> PORTS[0]);
        registry.add("tt.admin-http-port", () -> PORTS[1]);
    }
    private static int[] freePorts() {
        try (var first=new ServerSocket(0); var second=new ServerSocket(0)) {
            return new int[]{first.getLocalPort(),second.getLocalPort()};
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
    }
    private HttpResponse<String> request(int port, String method, String path, String body, String contentType) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .header("Content-Type",contentType).method(method,HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> get(int port,String path) throws Exception { return request(port,"GET",path,"","application/json"); }

    @Test void realListenersAllowReadsAndBlockPublicChanges() throws Exception {
        String path="/api/v1/tinyTopicDefinition";
        String definition="{\"id\":\"port-topic\",\"type\":\"text\",\"values\":[\"hello\"]}";
        assertEquals(201,request(PORTS[1],"POST",path,definition,"application/json").statusCode());
        assertEquals(200,get(PORTS[0],path).statusCode());
        assertTrue(get(PORTS[0],"/api/v1/tinyTopicResult/port-topic?length=64").body().contains("hello"));
        assertEquals(200,request(PORTS[0],"HEAD",path,"","application/json").statusCode());
        for (String method : new String[]{"POST","PUT","DELETE","PATCH","OPTIONS"}) {
            var response=request(PORTS[0],method,path+"/port-topic",definition,"application/json");
            assertEquals(405,response.statusCode(),method);
            assertEquals("GET, HEAD",response.headers().firstValue("Allow").orElseThrow());
        }
        var spoofed=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+PORTS[0]+path+"/port-topic"))
            .header("X-Forwarded-Port",Integer.toString(PORTS[1])).header("Forwarded","host=localhost:"+PORTS[1])
            .DELETE().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(405,spoofed.statusCode());
        assertEquals(200,get(PORTS[0],path+"/port-topic").statusCode());
        assertEquals(200,request(PORTS[1],"PUT",path+"/port-topic",definition,"application/json").statusCode());
        assertEquals(204,request(PORTS[1],"DELETE",path+"/port-topic","","application/json").statusCode());
        assertEquals(404,get(PORTS[0],path+"/port-topic").statusCode());
    }
    @Test void uiFormsAndAssetsStayOnAdminPort() throws Exception {
        for (String path : new String[]{"/","/topics/new","/style.css","/confirmations.js"}) {
            assertEquals(200,get(PORTS[1],path).statusCode(),path);
            assertEquals(404,get(PORTS[0],path).statusCode(),path);
        }
        assertEquals(404,request(PORTS[0],"POST","/topics","id=ui-port-topic&type=text&values=hello","application/x-www-form-urlencoded").statusCode());
        var saved=request(PORTS[1],"POST","/topics","id=ui-port-topic&type=text&values=hello","application/x-www-form-urlencoded");
        assertEquals(302,saved.statusCode());
        assertEquals(200,get(PORTS[1],"/topics/ui-port-topic").statusCode());
        assertEquals(302,request(PORTS[1],"POST","/topics/ui-port-topic/delete","","application/x-www-form-urlencoded").statusCode());
    }
    @Test void rejectsInvalidOrEqualPorts() {
        assertThrows(IllegalArgumentException.class,()->HttpPortConfiguration.validatePorts(8088,8088));
        assertThrows(IllegalArgumentException.class,()->HttpPortConfiguration.validatePorts(0,8089));
        assertThrows(IllegalArgumentException.class,()->HttpPortConfiguration.validatePorts(8088,65536));
    }
    @Test void documentationIsCompleteAndAdminOnly() throws Exception {
        assertEquals(404,get(PORTS[0],"/v3/api-docs").statusCode());
        assertEquals(404,get(PORTS[0],"/swagger-ui/index.html").statusCode());
        assertEquals(200,get(PORTS[1],"/swagger-ui/index.html").statusCode());
        assertEquals(200,get(PORTS[1],"/v3/api-docs/swagger-config").statusCode());
        var response=get(PORTS[1],"/v3/api-docs");
        assertEquals(200,response.statusCode());
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
        assertEquals("TinyTopics API",json.path("info").path("title").asText());
        assertEquals("/",json.path("servers").get(0).path("url").asText());
        assertEquals(4,json.path("paths").size());
        var operation=json.path("paths").path("/api/v1/tinyTopicResult/{id}").path("get");
        var parameters=operation.path("parameters");
        boolean lengthFound=false, formatFound=false;
        for (var parameter:parameters) {
            if (parameter.path("name").asText().equals("length")) {
                lengthFound=true; assertTrue(parameter.path("required").asBoolean());
                assertEquals(1,parameter.path("schema").path("minimum").asInt());
                assertEquals(4096,parameter.path("schema").path("maximum").asInt());
            }
            if (parameter.path("name").asText().equals("format")) {
                formatFound=true; assertEquals("UNICODE",parameter.path("schema").path("default").asText());
            }
            assertNotEquals("query",parameter.path("name").asText());
        }
        assertTrue(lengthFound); assertTrue(formatFound);
        for(String status:new String[]{"200","400","404","422","502"}) assertTrue(operation.path("responses").has(status));
        assertTrue(json.path("components").path("schemas").path("TinyTopicDefinition").path("properties").path("contentSource").path("readOnly").asBoolean());
        assertTrue(json.path("paths").path("/api/v1/tinyTopicDefinition").path("post").path("responses").has("201"));
    }
}
