package com.siberalt.singularity.presenter.google.render;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.siberalt.singularity.presenter.google.series.Column;
import com.siberalt.singularity.presenter.google.series.ColumnRole;
import com.siberalt.singularity.presenter.google.series.SeriesChunk;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FasterXmlRenderer implements DataRenderer {
    private static final String DEFAULT_HTML_FILE = "src/main/resources/presenter/google/PriceChart.html";
    private static final String DEFAULT_OUTPUT_FILE_PATH = "src/main/resources/presenter/google/PriceChart.json";

    private String htmlFile = DEFAULT_HTML_FILE;
    private String outputFilePath = DEFAULT_OUTPUT_FILE_PATH;

    public FasterXmlRenderer(String htmlFile, String outputFilePath) {
        this.htmlFile = htmlFile;
        this.outputFilePath = outputFilePath;
    }

    public FasterXmlRenderer() {
    }

    @Override
    public void render(SeriesChunk seriesChunk) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode rootNode = objectMapper.createObjectNode();

        rootNode.set("columns", createColumnsNode(objectMapper, seriesChunk));
        rootNode.set("options", createOptionsNode(objectMapper, seriesChunk));
        rootNode.set("data", createDataNode(objectMapper, seriesChunk));

        writeJsonToFile(objectMapper, rootNode);
        openHtmlFileInBrowser();
    }

    private ArrayNode createColumnsNode(ObjectMapper objectMapper, SeriesChunk seriesChunk) {
        ArrayNode columns = objectMapper.createArrayNode();
        for (Column column : seriesChunk.columns()) {
            ObjectNode columnNode = objectMapper.createObjectNode();
            columnNode.put("label", column.label());
            columnNode.put("type", column.type().getType());
            columnNode.put("role", column.role().getName());
            columns.add(columnNode);
        }
        return columns;
    }

    private ObjectNode createOptionsNode(ObjectMapper objectMapper, SeriesChunk seriesChunk) {
        ObjectNode options = objectMapper.createObjectNode();
        ObjectNode seriesOptions = objectMapper.createObjectNode();
        int seriesIndex = 0;
        Iterator<Column> columns = seriesChunk.columns().listIterator();

        for (Map<String, Object> option : seriesChunk.options()) {
            Column column = columns.next();

            if (column.role().equals(ColumnRole.DOMAIN)) {
                continue; // Skip domain columns
            }

            seriesOptions.set(String.valueOf(seriesIndex++), optionsToObjectNode(objectMapper, option));
        }

        options.set("series", seriesOptions);
        return options;
    }

    private ArrayNode createDataNode(ObjectMapper objectMapper, SeriesChunk seriesChunk) {
        ArrayNode seriesData = objectMapper.createArrayNode();
        for (Object[] row : seriesChunk.data()) {
            ArrayNode rowNode = objectMapper.createArrayNode();
            for (Object value : row) {
                addValue(rowNode, value);
            }
            seriesData.add(rowNode);
        }
        return seriesData;
    }

    private void writeJsonToFile(ObjectMapper objectMapper, ObjectNode rootNode) {
        try {
            objectMapper.writeValue(new File(outputFilePath), rootNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON file", e);
        }
    }

    private void openHtmlFileInBrowser() {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new File(htmlFile).toURI());
            } else {
                System.out.println("Desktop is not supported or browse action is unavailable. Please open the HTML file manually: " + htmlFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to open HTML file in browser", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ObjectNode optionsToObjectNode(ObjectMapper objectMapper, Map<String, Object> options) {
        ObjectNode optionsNode = objectMapper.createObjectNode();
        options.forEach((key, value) -> {
            if (value instanceof String) {
                optionsNode.put(key, (String) value);
            } else if (value instanceof Number) {
                optionsNode.put(key, ((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                optionsNode.put(key, (Boolean) value);
            } else if (value instanceof Map<?, ?> mapValue) {
                // Recursive call for nested maps
                optionsNode.set(key, optionsToObjectNode(objectMapper, (Map<String, Object>) mapValue));
            } else if (value instanceof List<?>) {
                ArrayNode arrayNode = objectMapper.createArrayNode();
                for (Object item : (List<?>) value) {
                    addValue(arrayNode, item);
                }
                optionsNode.set(key, arrayNode);
            } else {
                throw new IllegalArgumentException("Unsupported option type: " + value.getClass().getName());
            }
        });
        return optionsNode;
    }

    private void addValue(ArrayNode data, Object value) {
        if (value instanceof Instant instant) {
            data.add(instant.toString());
        } else if (value instanceof Double doubleValue) {
            data.add(doubleValue);
        } else if (value instanceof String stringValue) {
            data.add(stringValue);
        } else if (value == null) {
            data.addNull();
        } else {
            throw new IllegalArgumentException("Unsupported data type: " + value.getClass().getName());
        }
    }
}
