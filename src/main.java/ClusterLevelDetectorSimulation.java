import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.FunctionGroupSeriesProvider;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.strategy.extremum.BaseExtremumLocator;
import com.siberalt.singularity.strategy.extremum.ConcurrentFrameExtremumLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.linear.ClusterLevelDetector;
import com.siberalt.singularity.strategy.market.DefaultCandleIndexProvider;

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
        ConcurrentFrameExtremumLocator minExtremumLocator = new ConcurrentFrameExtremumLocator(
            120,
            BaseExtremumLocator.createMinLocator(Candle::getTypicalPriceAsDouble)
        );
        ConcurrentFrameExtremumLocator maxExtremumLocator = new ConcurrentFrameExtremumLocator(
            120,
            BaseExtremumLocator.createMaxLocator(Candle::getTypicalPriceAsDouble)
        );
        DefaultCandleIndexProvider candleIndexProvider = new DefaultCandleIndexProvider();
        candleIndexProvider.accumulate(candles);
        PointSeriesProvider minPoints = new PointSeriesProvider("Minima");
        minPoints.setColor("#00FF00");
        minPoints.setSize(5);
        minExtremumLocator.locate(candles)
            .forEach(
                minPoint -> minPoints.addPoint(
                    candleIndexProvider.provideIndex(minPoint),
                    minPoint.getTypicalPriceAsDouble()
                )
            );
        PointSeriesProvider maxPoints = new PointSeriesProvider("Maxima");
        maxPoints.setColor("#FF0000");
        maxPoints.setSize(5);
        maxExtremumLocator.locate(candles)
            .forEach(
                maxPoint -> maxPoints.addPoint(
                    candleIndexProvider.provideIndex(maxPoint),
                    maxPoint.getTypicalPriceAsDouble()
                )
            );

        PriceChart priceChart = new PriceChart(
            candleRepository,
            "TMOS",
            Candle::getTypicalPriceAsDouble
        );
        ClusterLevelDetector supportDetector = new ClusterLevelDetector(0.005, minExtremumLocator);
        ClusterLevelDetector resistanceDetector = new ClusterLevelDetector(0.005, maxExtremumLocator);
        var supportLevels = supportDetector.detect(candles, candleIndexProvider);
        var resistanceLevels = resistanceDetector.detect(candles, candleIndexProvider);
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
