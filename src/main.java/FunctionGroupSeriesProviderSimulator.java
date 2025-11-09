import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.presenter.google.series.FunctionGroupSeriesProvider;

import java.time.Instant;
import java.util.List;

public class FunctionGroupSeriesProviderSimulator {
    public static void main(String[] args) {
        Instant startTime = Instant.parse("2021-01-01T00:00:00Z");
        Instant endTime = Instant.parse("2021-02-02T00:00:00Z");
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();

        CvsCandleRepository candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );
        List<Candle> candles = candleRepository.getPeriod("TMOS", startTime, endTime);
        FunctionGroupSeriesProvider blueLinesGroup = new FunctionGroupSeriesProvider("Blue lines group");
        blueLinesGroup.setColor("#0000FF");
        blueLinesGroup.addFunction(0, 200, x -> 5.8);
        blueLinesGroup.addFunction(0, 2000, x -> 6.0);
        blueLinesGroup.addFunction(1000, 2000, x -> 6.2);
        blueLinesGroup.addFunction(2000, 6000, x -> (x - 2000) / 10000 + 5.4);
        FunctionGroupSeriesProvider redLinesGroup = new FunctionGroupSeriesProvider("Red lines group");
        redLinesGroup.setColor("#FF0000");
        redLinesGroup.addFunction(0, 3000, x -> 6.4);
        redLinesGroup.addFunction(3000, 6000, x -> (6000 - x) / 10000 + 5.6);
        PriceChart priceChart = new PriceChart(
            candleRepository,
            "TMOS",
            Candle::getTypicalPriceAsDouble
        );
        priceChart.setStepInterval(10);
        priceChart.addSeriesProvider(blueLinesGroup);
        priceChart.addSeriesProvider(redLinesGroup);
        // Render the chart (hypothetical method)
        priceChart.render(candles);
    }
}
