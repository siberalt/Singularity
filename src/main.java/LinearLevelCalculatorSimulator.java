import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.LineSeriesProvider;
import com.siberalt.singularity.strategy.level.linear.LinearLevel;
import com.siberalt.singularity.strategy.level.linear.LinearLevelDetector;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public class LinearLevelCalculatorSimulator {
    public static void main(String[] args) {
        var factory = new CvsFileCandleRepositoryFactory();
        var candleRepository = factory.create("TMOS", "src/test/resources/entity.candle.cvs/TMOS");
        var priceExtractor = (Function<Candle, Double>) c -> c.getClosePrice().toBigDecimal().doubleValue();
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");

        List<Candle> candles = candleRepository.getPeriod(
            "TMOS", Instant.parse("2021-01-01T00:00:00Z"), Instant.parse("2021-02-02T00:00:00Z")
        );
        int frameSize = 800;

        var supportDetector = LinearLevelDetector.createSupport(
            frameSize, 0.003, priceExtractor, -0.002
        );
        var resistanceDetector = LinearLevelDetector.createResistance(
            frameSize, 0.003, priceExtractor, 0.002
        );

        var resultSupport = supportDetector.detect(candles);
        var resultResistance = resistanceDetector.detect(candles);

        var renderer = new FasterXmlRenderer(
            "src/main/resources/presenter/google/PriceChart.html",
            "src/main/resources/presenter/google/PriceChart.json"
        );
        var priceChart = new PriceChart(candleRepository, "TMOS", priceExtractor);
        priceChart.setDataRenderer(renderer);
        priceChart.setStepInterval(1);

        addLevelsToChart(priceChart, "Support", resultSupport, "#00FF00");
        addLevelsToChart(priceChart, "Resistance", resultResistance, "#FF0000");

        priceChart.render(startTime, endTime);
    }

    private static void addLevelsToChart(PriceChart chart, String name, List<LinearLevel<Double>> levels, String color) {
        var linesProvider = new LineSeriesProvider(name);
        levels.forEach(level -> linesProvider.addLine(level.getIndexFrom(), level.getIndexTo(), level.getFunction()));
        linesProvider.setColor(color);
        chart.addSeriesProvider(linesProvider);
    }
}
