import com.siberalt.singularity.broker.contract.service.market.request.CandleInterval;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.*;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

public class ExtremesLocatorSimulator {
    private static final long INSTRUMENT_ID = 7;

    public static void main(String[] args) throws IOException {
        Instant startTime = Instant.parse("2021-03-01T00:00:00Z");
        Instant endTime = Instant.parse("2026-12-11T00:00:00Z");

        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );
        String dbPath = ConfigFacade.of(configuration).getAsString("dbPath");
        SqliteCandleRepositoryFactory sqliteCandleRepositoryFactory = new SqliteCandleRepositoryFactory();

        ReadCandleRepository candleRepository = new AggregatingCandleRepository(
            sqliteCandleRepositoryFactory.create(dbPath),
            CandleInterval.HOUR
        );
        List<Candle> candles = candleRepository.getPeriod(INSTRUMENT_ID, startTime, endTime);
        ExtremeLocator minExtremeLocator = PivotPointExtremeLocator.ofMinimums(50);
        ExtremeLocator maxExtremeLocator = PivotPointExtremeLocator.ofMaximums(50);
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
            INSTRUMENT_ID,
            Candle::getCloseAsDouble
        );
        priceChart.addSeriesProvider(minPoints);
        priceChart.addSeriesProvider(maxPoints);
        priceChart.setStepInterval(1);
        // Render the chart (hypothetical method)
        priceChart.render(candles);
    }
}
