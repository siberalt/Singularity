import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.FunctionGroupSeriesProvider;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.linear.ConsensusLineLevelDetector;
import com.siberalt.singularity.strategy.level.linear.LinearLevelDetector;
import com.siberalt.singularity.strategy.level.linear.StatelessClusterLevelDetector;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

public class LevelDetectorSimulation {
    // Our id for TMOS - what its candles are kept under.
    private static final long TMOS = 1;

    public static void main(String[] args) throws IOException {
        Instant startTime = Instant.parse("2023-04-01T00:00:00Z");
        Instant endTime = Instant.parse("2023-05-01T00:00:00Z");
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepositoryFactory sqliteCandleRepositoryFactory = new SqliteCandleRepositoryFactory();
        SqliteCandleRepository candleRepository = sqliteCandleRepositoryFactory.create(dbPath);

        List<Candle> candles = candleRepository.getPeriod(TMOS, startTime, endTime);
        ExtremeLocator minExtremeLocator = PivotPointExtremeLocator.ofMinimums(50);
        //ExtremeLocator maxExtremeLocator = PivotPointExtremeLocator.ofMaximums(50);
        VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator();
        LevelDetector supportDetector = new ConsensusLineLevelDetector(minExtremeLocator).setVolatilityTolerance(volatilityCalculator, 2);
        //LevelDetector resistanceDetector = StatelessClusterLevelDetector.createDefault(1.4, maxExtremeLocator);
        var supportLevels = supportDetector.detect(candles);
        //var resistanceLevels = resistanceDetector.detect(candles);

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
//        maxExtremeLocator.locate(candles)
//            .forEach(
//                maxPoint -> maxPoints.addPoint(
//                    maxPoint.getIndex(),
//                    maxPoint.getCloseAsDouble()
//                )
//            );

        PriceChart priceChart = new PriceChart(
            candleRepository,
            TMOS,
            Candle::getCloseAsDouble
        );
        addLevelsToChart(priceChart, "Support Levels", supportLevels, "#00FFFA");
        //addLevelsToChart(priceChart, "Resistance Levels", resistanceLevels, "#FFBB00");
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
