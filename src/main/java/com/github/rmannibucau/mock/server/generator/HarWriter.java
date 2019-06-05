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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;
import javax.json.bind.config.PropertyOrderStrategy;

import com.github.rmannibucau.mock.server.generator.model.Har;

public class HarWriter {
    public String toString(final Har har) {
        try (final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig()
                .withFormatting(true)
                .withPropertyOrderStrategy(PropertyOrderStrategy.LEXICOGRAPHICAL))) {
            return jsonb.toJson(har);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public void write(final Path output, final Har har) {
        if (!Files.exists(output.getParent())) {
            try {
                Files.createDirectories(output.getParent());
            } catch (IOException e) {
                throw new IllegalStateException("Can't create '" + output + "'");
            }
        }
        try (final Writer writer = Files.newBufferedWriter(output)) {
            writer.write(toString(har));
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
