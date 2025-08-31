import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.render.FasterXmlRenderer;
import com.siberalt.singularity.presenter.google.series.LineSeriesProvider;
import com.siberalt.singularity.strategy.level.LinearLevelCalculator;
import com.siberalt.singularity.strategy.level.Result;

import java.time.Instant;
import java.util.function.Function;

public class LinearLevelCalculatorSimulator {
    public static void main(String[] args) {
        var factory = new CvsFileCandleRepositoryFactory();
        var candleRepository = factory.create("TMOS", "src/test/resources/entity.candle.cvs/TMOS");
        var priceExtractor = (Function<Candle, Double>) c -> c.getClosePrice().toBigDecimal().doubleValue();

        var supportCalculator = LinearLevelCalculator.createSupport(
            candleRepository, 5, 0.003, priceExtractor, -0.001
        );
        var resistanceCalculator = LinearLevelCalculator.createResistance(
            candleRepository, 5, 0.003, priceExtractor, 0.001
        );

        var resultSupport = supportCalculator.calculate(
            "TMOS", Instant.parse("2021-03-12T00:00:00Z"), Instant.parse("2021-03-15T12:00:00Z")
        );
        var resultResistance = resistanceCalculator.calculate(
            "TMOS", Instant.parse("2021-03-12T00:00:00Z"), Instant.parse("2021-03-15T12:00:00Z")
        );

        var renderer = new FasterXmlRenderer(
            "src/main/resources/presenter/google/PriceChart.html",
            "src/main/resources/presenter/google/PriceChart.json"
        );
        var priceChart = new PriceChart(candleRepository, "TMOS", priceExtractor);
        priceChart.setDataRenderer(renderer);
        priceChart.setStepInterval(1);

        addLevelsToChart(priceChart, "Support", resultSupport, "#000000");
        addLevelsToChart(priceChart, "Resistance", resultResistance, "#DB4437");

        priceChart.render(Instant.parse("2021-03-12T00:00:00Z"), Instant.parse("2021-03-15T12:00:00Z"));
    }

    private static void addLevelsToChart(PriceChart chart, String name, Result<Double> result, String color) {
        var linesProvider = new LineSeriesProvider(name);
        result.levels().forEach(level -> linesProvider.addLine(level.indexFrom(), level.indexTo(), level.function()));
        linesProvider.setColor(color);
        chart.addSeriesProvider(linesProvider);
    }
}
