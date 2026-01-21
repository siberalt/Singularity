import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.strategy.extremum.BaseExtremumLocator;
import com.siberalt.singularity.strategy.extremum.ConcurrentFrameExtremumLocator;

import java.time.Instant;
import java.util.List;

public class ConcurrentFrameExtremumLocatorSimulator {
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
        PointSeriesProvider minPoints = new PointSeriesProvider("Minima");
        minPoints.setColor("#00FF00");
        minExtremumLocator.locate(candles)
            .forEach(
                minPoint -> minPoints.addPoint(
                    minPoint.getIndex(),
                    minPoint.getTypicalPriceAsDouble()
                )
            );
        PointSeriesProvider maxPoints = new PointSeriesProvider("Maxima");
        maxPoints.setColor("#FF0000");
        maxExtremumLocator.locate(candles)
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
        priceChart.addSeriesProvider(minPoints);
        priceChart.addSeriesProvider(maxPoints);
        priceChart.setStepInterval(1);
        // Render the chart (hypothetical method)
        priceChart.render(candles);
    }
}
