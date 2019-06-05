/**
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rmannibucau.mock.server.generator;

import static javax.ws.rs.client.Entity.entity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import com.github.rmannibucau.mock.server.generator.endpoint.SimpleEndpoints;
import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit5.MeecrowaveConfig;
import org.junit.jupiter.api.Test;

@MeecrowaveConfig
class HarClientFeatureTest {
    @Inject
    private Meecrowave.Builder config;

    @Test
    void capture() {
        final HarClientFeature feature = new HarClientFeature();
        final Client client = ClientBuilder.newClient().register(feature);
        try {
            final WebTarget test = client.target("http://localhost:" + config.getHttpPort()).path("test");
            test.path("string").request(MediaType.APPLICATION_JSON_TYPE).get(String.class);
            test.path("object").request(MediaType.APPLICATION_JSON_TYPE).get(String.class);
            test.request(MediaType.APPLICATION_JSON_TYPE).post(entity(new SimpleEndpoints.Text("post"), MediaType.APPLICATION_JSON_TYPE), String.class);
            final String har = new HarWriter().toString(feature.getHar());
            assertEquals(expected("capture.json"),
                    har.replaceAll(" {14}\"name\":\"Date\",\n {14}\"value\":\"[^\"]+\"",
                            "              \"name\":\"Date\",\n              \"value\":\"@date@\""));
        } finally {
            client.close();
        }
    }

    private String expected(final String resource) {
        try {
            return new String(Files.readAllBytes(Paths.get("target/test-classes/expected/" + resource)), StandardCharsets.UTF_8)
                    .replace("@port@", Integer.toString(config.getHttpPort()));
        } catch (final IOException e) {
            fail(e.getMessage());
            throw new IllegalStateException(e);
        }
    }
}
