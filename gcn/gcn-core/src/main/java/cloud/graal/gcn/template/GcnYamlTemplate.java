/*
 * Copyright 2023 Oracle and/or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cloud.graal.gcn.template;

import io.micronaut.starter.template.DefaultTemplate;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static cloud.graal.gcn.GcnUtils.LIB_MODULE;

/**
 * Replaces io.micronaut.starter.template.YamlTemplate to avoid using SnakeYAML
 * which bulks up the size of the generated Web Image files significantly.
 *
 * @since 1.0.0
 */
public class GcnYamlTemplate extends DefaultTemplate {

    private static final Pattern DOT_PATTERN = Pattern.compile("\\.");
    private static final String INDENT = "  ";
    private static final char[] INDICATORS = ":[]{},\"'|*&".toCharArray();

    private final Map<String, Object> config;
    private final Map<String, Object> originalConfig;

    public GcnYamlTemplate(String path, Map<String, Object> config) {
        this(LIB_MODULE, path, config);
    }

    public GcnYamlTemplate(String module, String path, Map<String, Object> config) {
        super(module, path);
        this.originalConfig = config;
        this.config = transform(config);
    }

    /**
     * @return the transformed config
     */
    public Map<String, Object> getConfig() {
        return Collections.unmodifiableMap(config);
    }

    /**
     * @return the config passed to the constructor, not the transformed config
     */
    public Map<String, Object> getOriginalConfig() {
        return Collections.unmodifiableMap(originalConfig);
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        if (config.isEmpty()) {
            outputStream.write("# Place application configuration here".getBytes());
            return;
        }

        StringBuilder sb = new StringBuilder();
        render(config, sb, "");
        String yaml = sb.toString().trim() + '\n';

        outputStream.write(yaml.getBytes());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> transform(Map<String, Object> config) {
        Map<String, Object> transformed = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            Map<String, Object> finalMap = transformed;
            String key = entry.getKey();
            Object value = entry.getValue();
            int index = key.indexOf('.');
            if (index != -1) {
                String[] keys = DOT_PATTERN.split(key);
                if (!keys[0].equals("micronaut") && config.keySet().stream().filter(k -> k.startsWith(keys[0] + ".")).count() == 1) {
                    finalMap.put(key, value);
                } else {
                    for (int i = 0; i < keys.length - 1; i++) {
                        String subKey = keys[i];

                        if (!finalMap.containsKey(subKey)) {
                            finalMap.put(subKey, new LinkedHashMap<>());
                        }
                        Object next = finalMap.get(subKey);
                        if (next instanceof Map) {
                            finalMap = ((Map<String, Object>) next);
                        }
                    }
                    finalMap.put(keys[keys.length - 1], value);
                }
            } else {
                finalMap.put(key, value);
            }
        }
        return transformed;
    }

    private void render(Object o, StringBuilder yaml, String indent) {
        if (o == null) {
            yaml.append(" ~\n");
            return;
        }

        if (o instanceof Map) {
            render((Map<String, Object>) o, yaml, indent);
        } else if (o instanceof Collection) {
            render((Collection<?>) o, yaml, indent);
        } else {
            render(o, yaml);
        }
    }

    private void render(Map<String, Object> m, StringBuilder yaml, String indent) {

        if (m.isEmpty()) {
            yaml.append(" {").append('\n').append(indent).append('}');
        }

        yaml.append('\n');

        for (Map.Entry<String, Object> entry : m.entrySet()) {
            yaml.append(indent).append(entry.getKey()).append(':');
            render(entry.getValue(), yaml, indent(indent));
        }
    }

    private void render(Collection<?> c, StringBuilder yaml, String indent) {

        if (c.isEmpty()) {
            yaml.append(" [").append('\n').append(indent).append(']');
        }

        yaml.append('\n');

        for (Object o : c) {
            yaml.append(unindent(indent)).append('-');
            render(o, yaml, indent(indent));
        }
    }

    private void render(Object o, StringBuilder yaml) {
        yaml.append(' ');
        if (o instanceof CharSequence || o instanceof Character) {
            yaml.append(escapeAndQuote(o.toString()));
        } else {
            yaml.append(o);
        }
        yaml.append('\n');
    }

    private String escapeAndQuote(String s) {

        if (s.length() == 0) {
            return "''";
        }

        boolean quote = false;

        if (s.trim().length() != s.length()) {
            quote = true;
        } else {
            for (char c : INDICATORS) {
                if (s.indexOf(c) != -1) {
                    quote = true;
                    break;
                }
            }
        }

        if (quote) {
            return "'" + s
                    .replace("\\", "\\\\")
                    .replace("\b", "\\b")
                    .replace("\0", "\\0")
                    .replace("\t", "\\t")
                    .replace("'", "''") +
                    "'";
        }

        return s;
    }

    private String indent(String s) {
        return INDENT + s;
    }

    private String unindent(String s) {
        return s.substring(INDENT.length());
    }

    @Override
    public String toString() {
        return "GcnYamlTemplate{" +
                "config=" + getConfig() +
                "originalConfig=" + originalConfig +
                ", path='" + path + '\'' +
                ", module='" + module + '\'' +
                ", useModule=" + useModule +
                '}';
    }
}