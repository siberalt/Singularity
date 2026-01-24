import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.FunctionGroupSeriesProvider;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.strategy.extreme.BaseExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ConcurrentFrameExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.linear.ClusterLevelDetector;

import java.time.Instant;
import java.util.List;

public class ClusterLevelDetectorSimulation {
    public static void main(String[] args) {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();

        CvsCandleRepository candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );
        List<Candle> candles = candleRepository.getPeriod("TMOS", startTime, endTime);
        ConcurrentFrameExtremeLocator minExtremeLocator = new ConcurrentFrameExtremeLocator(
            120,
            BaseExtremeLocator.createMinLocator(Candle::getTypicalPriceAsDouble)
        );
        ConcurrentFrameExtremeLocator maxExtremeLocator = new ConcurrentFrameExtremeLocator(
            120,
            BaseExtremeLocator.createMaxLocator(Candle::getTypicalPriceAsDouble)
        );
        PointSeriesProvider minPoints = new PointSeriesProvider("Minima");
        minPoints.setColor("#00FF00");
        minPoints.setSize(5);
        minExtremeLocator.locate(candles)
            .forEach(
                minPoint -> minPoints.addPoint(
                    minPoint.getIndex(),
                    minPoint.getTypicalPriceAsDouble()
                )
            );
        PointSeriesProvider maxPoints = new PointSeriesProvider("Maxima");
        maxPoints.setColor("#FF0000");
        maxPoints.setSize(5);
        maxExtremeLocator.locate(candles)
            .forEach(
                maxPoint -> maxPoints.addPoint(
                    maxPoint.getIndex(),
                    maxPoint.getTypicalPriceAsDouble()
                )
            );

        PriceChart priceChart = new PriceChart(
            candleRepository,
            "TMOS",
            Candle::getTypicalPriceAsDouble
        );
        ClusterLevelDetector supportDetector = new ClusterLevelDetector(0.005, minExtremeLocator);
        ClusterLevelDetector resistanceDetector = new ClusterLevelDetector(0.005, maxExtremeLocator);
        var supportLevels = supportDetector.detect(candles);
        var resistanceLevels = resistanceDetector.detect(candles);
        addLevelsToChart(priceChart, "Support Levels", supportLevels, "#00FFFA");
        addLevelsToChart(priceChart, "Resistance Levels", resistanceLevels, "#FFBB00");
        priceChart.addSeriesProvider(minPoints);
        priceChart.addSeriesProvider(maxPoints);
        priceChart.setStepInterval(1);
        // Render the chart (hypothetical method)
        priceChart.render(candles);
    }

    private static void addLevelsToChart(PriceChart chart, String name, List<Level<Double>> levels, String color) {
        var functionsProvider = new FunctionGroupSeriesProvider(name);
        levels.forEach(level -> functionsProvider.addFunction(
            FunctionGroupSeriesProvider.createFunctionDetails(level.indexFrom(), level.indexTo(), level.function())
        ));
        functionsProvider.setColor(color);
        chart.addSeriesProvider(functionsProvider);
    }
}
