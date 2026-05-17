import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;

import java.time.Instant;
import java.util.List;

public class ExtremesLocatorSimulator {
    public static void main(String[] args) {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();

        CvsCandleRepository candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );
        List<Candle> candles = candleRepository.getPeriod("TMOS", startTime, endTime);
        ExtremeLocator minExtremeLocator = PivotPointExtremeLocator.ofMinimums(60);
        ExtremeLocator maxExtremeLocator = PivotPointExtremeLocator.ofMaximums(60);
        PointSeriesProvider minPoints = new PointSeriesProvider("Minima");
        minPoints.setColor("#00FF00");
        minPoints.setSize(5);
        minExtremeLocator.locate(candles)
            .forEach(
                minPoint -> minPoints.addPoint(
                    minPoint.getIndex(),
                    minPoint.getClosePriceAsDouble()
                )
            );
        PointSeriesProvider maxPoints = new PointSeriesProvider("Maxima");
        maxPoints.setColor("#FF0000");
        maxPoints.setSize(5);
        maxExtremeLocator.locate(candles)
            .forEach(
                maxPoint -> maxPoints.addPoint(
                    maxPoint.getIndex(),
                    maxPoint.getClosePriceAsDouble()
                )
            );

        PriceChart priceChart = new PriceChart(
            candleRepository,
            "TMOS",
            Candle::getClosePriceAsDouble
        );
        priceChart.addSeriesProvider(minPoints);
        priceChart.addSeriesProvider(maxPoints);
        priceChart.setStepInterval(1);
        // Render the chart (hypothetical method)
        priceChart.render(candles);
    }
}
