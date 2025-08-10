package com.siberalt.singularity.presenter.google;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.math.LinearFunction2D;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class PriceChart {
    private static final String DEFAULT_HTML_FILE = "src/main/resources/presenter/google/PriceChart.html";
    private static final String DEFAULT_OUTPUT_FILE_PATH = "src/main/resources/presenter/google/PriceChart.json";

    public record Line(long x1, long x2, LinearFunction2D<Double> function) {
        public double y1() {
            return function.calculate((double) x1);
        }

        public double y2() {
            return function.calculate((double) x2);
        }
    }

    private final String instrumentUid;
    private final ReadCandleRepository candleRepository;
    private Function<Candle, Double> priceExtractor = c -> c.getClosePrice().toBigDecimal().doubleValue();
    private String htmlFile = DEFAULT_HTML_FILE;
    private String outputFilePath = DEFAULT_OUTPUT_FILE_PATH;
    private final List<Line> lines = new ArrayList<>();
    private int stepInterval = 30; // Default step interval for rendering

    public PriceChart(ReadCandleRepository candleRepository, String instrumentUid) {
        this.candleRepository = candleRepository;
        this.instrumentUid = instrumentUid;
    }

    public PriceChart(ReadCandleRepository candleRepository, String instrumentUid, String htmlFile) {
        this.candleRepository = candleRepository;
        this.instrumentUid = instrumentUid;
        this.htmlFile = htmlFile;
    }

    public PriceChart(
        ReadCandleRepository candleRepository,
        String instrumentUid,
        String htmlFile,
        Function<Candle, Double> priceExtractor
    ) {
        this.candleRepository = candleRepository;
        this.instrumentUid = instrumentUid;
        this.htmlFile = htmlFile;
        this.priceExtractor = priceExtractor;
    }

    public PriceChart setStepInterval(int stepInterval) {
        this.stepInterval = stepInterval;
        return this;
    }

    public void addLine(long x1, long x2, LinearFunction2D<Double> function) {
        long remainderX1 = x1 % stepInterval;
        long remainderX2 = x2 % stepInterval;

        x1 = remainderX1 < (stepInterval / 2) ? x1 - remainderX1 : x1 - remainderX1 + stepInterval;
        x2 = remainderX2 < (stepInterval / 2) ? x2 - remainderX2 : x2 - remainderX2 + stepInterval;
        Line line = new Line(x1, x2, function);
        lines.add(line);
    }

    public void render(Instant startTime, Instant endTime) throws IOException {
        List<Candle> candles = candleRepository.getPeriod(instrumentUid, startTime, endTime);

        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode jsonArray = objectMapper.createArrayNode();
        long index = 0;
        Iterator<Line> linesIterator = lines.iterator();
        Line currentLine = linesIterator.hasNext() ? linesIterator.next() : null;

        for (Candle candle : candles) {

            if (index % stepInterval == 0) {
                ArrayNode arrayNode = objectMapper.createArrayNode();
                arrayNode.add(candle.getTime().toString());
                arrayNode.add(priceExtractor.apply(candle));

                // Add lines to the JSON array
                if (currentLine != null && currentLine.x1() <= index && currentLine.x2() >= index) {
                    arrayNode.add(currentLine.function.calculate((double) index));

                    if (linesIterator.hasNext() && index >= currentLine.x2()) {
                        currentLine = linesIterator.next();
                    }
                }

                jsonArray.add(arrayNode);
            }

            index++;
        }

        objectMapper.writeValue(new File(outputFilePath), jsonArray);
        System.out.println("Serve the JSON file using an HTTP server, e.g., Python's `http.server` or Node.js.");

        // Open the HTML file by field htmlFile in the default browser
        if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
            java.awt.Desktop.getDesktop().browse(new File(htmlFile).toURI());
        } else {
            System.out.println("Desktop is not supported or browse action is unavailable. Please open the HTML file manually: " + htmlFile);
        }
    }
}
