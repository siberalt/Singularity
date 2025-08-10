import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.presenter.google.PriceChart;
import com.siberalt.singularity.strategy.level.LinearLevelCalculator;
import com.siberalt.singularity.strategy.level.Result;

import java.io.IOException;
import java.time.Instant;

public class LinearLevelCalculatorSimulator {
    public static void main(String[] args) throws IOException {
        CvsFileCandleRepositoryFactory factory = new CvsFileCandleRepositoryFactory();
        Instant start = Instant.parse("2021-03-05T00:00:00Z");
        Instant end = Instant.parse("2021-03-15T12:00:00Z");

        CvsCandleRepository candleRepository = factory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        );

        LinearLevelCalculator calculator = LinearLevelCalculator.createSupport(
            candleRepository,
            5,
            0.003,
            c -> c.getClosePrice().toBigDecimal().doubleValue(),
            -.001
        );

        var result = calculator.calculate("TMOS", start, end);

        PriceChart priceChart = new PriceChart(
            candleRepository,
            "TMOS",
            "src/main/resources/presenter/google/PriceChart.html",
            c -> c.getClosePrice().toBigDecimal().doubleValue()
        );

        priceChart.setStepInterval(1);

        for (Result.Level<Double> level : result.levels()) {
            priceChart.addLine(
                level.indexFrom(),
                level.indexTo(),
                level.function()
            );
        }

        priceChart.render(start, end);
    }
}
