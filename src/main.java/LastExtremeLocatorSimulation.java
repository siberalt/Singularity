import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.PointSeriesProvider;
import com.siberalt.singularity.strategy.extreme.LastExtremeLocator;

import java.time.Instant;
import java.util.List;

public class LastExtremeLocatorSimulation {
    public static void main(String[] args) {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();

        CvsCandleRepository candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );
        List<Candle> candles = candleRepository.getPeriod("TMOS", startTime, endTime);
        LastExtremeLocator minExtremeLocator = LastExtremeLocator.ofMinimums(
            50, 1, Candle::getTypicalAsDouble
        );
        LastExtremeLocator maxExtremeLocator = LastExtremeLocator.ofMaximums(
            50, 1, Candle::getTypicalAsDouble
        );
        int chunkSize = 2000;

        PointSeriesProvider minPoints = new PointSeriesProvider("Minima");
        minPoints.setColor("#00FF00");
        PointSeriesProvider maxPoints = new PointSeriesProvider("Maxima");
        maxPoints.setColor("#FF0000");
        PointSeriesProvider chunkPoints = new PointSeriesProvider("Chunk border");
        chunkPoints.setColor("#FFFF00");

        for (int i = 0; i < candles.size(); i += chunkSize) {
            int toIndex = Math.min(i + chunkSize, candles.size());
            List<Candle> chunk = candles.subList(i, toIndex);
            Candle lastChunkCandle = chunk.get(chunk.size() - 1);
            chunkPoints.addPoint(lastChunkCandle.getIndex(), lastChunkCandle.getTypicalAsDouble());
            minExtremeLocator.locate(chunk)
                .forEach(
                    minPoint -> minPoints.addPoint(
                        minPoint.getIndex(),
                        minPoint.getTypicalAsDouble()
                    )
                );
            maxExtremeLocator.locate(chunk)
                .forEach(
                    maxPoint -> maxPoints.addPoint(
                        maxPoint.getIndex(),
                        maxPoint.getTypicalAsDouble()
                    )
                );
        }

        PriceChart priceChart = new PriceChart(
            candleRepository,
            "TMOS",
            Candle::getTypicalAsDouble
        );
        priceChart.addSeriesProvider(minPoints);
        priceChart.addSeriesProvider(maxPoints);
        priceChart.addSeriesProvider(chunkPoints);
        priceChart.setStepInterval(1);
        // Render the chart (hypothetical method)
        priceChart.render(candles);
    }
}
