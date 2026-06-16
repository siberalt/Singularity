import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.VolumeChart;
import com.siberalt.singularity.presenter.google.series.FunctionGroupSeriesProvider;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.StatelessClusterLevelDetector;
import com.siberalt.singularity.strategy.level.selector.LevelPair;
import com.siberalt.singularity.strategy.level.selector.StrongestLevelPairSelector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class LevelPairSelectorSimulation {
    public static void main(String[] args) {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();

        CvsCandleRepository candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );
        List<Candle> candles = candleRepository.getPeriod("TMOS", startTime, endTime);
        ExtremeLocator minExtremeLocator = PivotPointExtremeLocator.ofMinimums(50);
        ExtremeLocator maxExtremeLocator = PivotPointExtremeLocator.ofMaximums(50);
        LevelDetector supportDetector = StatelessClusterLevelDetector.createDefault(2.5, minExtremeLocator);
        LevelDetector resistanceDetector = StatelessClusterLevelDetector.createDefault(2.5, maxExtremeLocator);
        ArrayList<Level<Double>> supportLevels = new ArrayList<>(supportDetector.detect(candles));
        ArrayList<Level<Double>> resistanceLevels = new ArrayList<>(resistanceDetector.detect(candles));

        var levelSelector = new StrongestLevelPairSelector(1);
        List<LevelPair> levelPairs = levelSelector.select(resistanceLevels, supportLevels, candles);

        PointSeriesProvider minPoints = new PointSeriesProvider("Minima");
        minPoints.setColor("#00FF00");
        minPoints.setSize(5);
        minExtremeLocator.locate(candles)
            .forEach(
                minPoint -> minPoints.addPoint(
                    minPoint.getIndex(),
                    minPoint.getCloseAsDouble()
                )
            );
        PointSeriesProvider maxPoints = new PointSeriesProvider("Maxima");
        maxPoints.setColor("#FF0000");
        maxPoints.setSize(5);
        maxExtremeLocator.locate(candles)
            .forEach(
                maxPoint -> maxPoints.addPoint(
                    maxPoint.getIndex(),
                    maxPoint.getCloseAsDouble()
                )
            );

        PriceChart priceChart = new PriceChart(
            candleRepository,
            "TMOS",
            Candle::getCloseAsDouble
        );
        List<Level<Double>> selectedSupportLevels = new ArrayList<>();
        List<Level<Double>> selectedResistanceLevels = new ArrayList<>();
        List<Level<Double>> unselectedSupportLevels = new ArrayList<>();
        List<Level<Double>> unselectedResistanceLevels = new ArrayList<>();

        for (var pair : levelPairs) {
            for (var supportLevel : supportLevels) {
                if (supportLevel.equals(pair.support())) {
                    selectedSupportLevels.add(supportLevel);
                } else {
                    unselectedSupportLevels.add(supportLevel);
                }
            }

            for (var restanceLevel : resistanceLevels) {
                if (restanceLevel.equals(pair.resistance())) {
                    selectedResistanceLevels.add(restanceLevel);
                } else {
                    unselectedResistanceLevels.add(restanceLevel);
                }
            }
        }
        addLevelsToChart(priceChart, "Support Levels", unselectedSupportLevels, "#00FFFA");
        addLevelsToChart(priceChart, "Resistance Levels", unselectedResistanceLevels, "#FFBB00");
        addLevelsToChart(priceChart, "Selected resistance Levels", selectedResistanceLevels, "#CC8400");
        addLevelsToChart(priceChart, "Selected support Levels", selectedSupportLevels, "#008B8B");
        priceChart.addSeriesProvider(minPoints);
        priceChart.addSeriesProvider(maxPoints);
        priceChart.setStepInterval(1);
        // Render the chart (hypothetical method)
        priceChart.render(candles);

        VolumeChart volumeChart = new VolumeChart(1);
        volumeChart.render(candles);
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
