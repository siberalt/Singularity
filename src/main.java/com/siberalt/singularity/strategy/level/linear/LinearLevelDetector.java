package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.math.IncrementalLinearRegression;
import com.siberalt.singularity.math.Point2D;
import com.siberalt.singularity.strategy.extreme.BaseExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.FrameExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.strength.BasicStrengthCalculator;
import com.siberalt.singularity.strategy.level.strength.StrengthCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class LinearLevelDetector implements LevelDetector {
    private long startLevelIndex;
    private Instant startLevelTime = null;
    private IncrementalLinearRegression linearModel;
    private final double neighbourhoodRatio; // Default neighborhood percentage for support level calculation
    private StrengthCalculator strengthCalculator = new BasicStrengthCalculator();
    private final ExtremeLocator extremeLocator;
    private VolatilityCalculator volatilityCalculator;
    private double volatilities = 1;

    public LinearLevelDetector(
        double neighbourhoodRatio,
        ExtremeLocator extremeLocator
    ) {
        this.neighbourhoodRatio = neighbourhoodRatio;
        this.extremeLocator = extremeLocator;
    }

    /**
     * Насколько далеко от прямой может лежать экстремум, чтобы уровень продолжался - в волатильностях
     * вместо доли цены.
     * <p>
     * Доля цены - не то, чем измеряется промах. Бумага, которая ходит на процент в час, и бумага,
     * которая ходит на десятую процента, при одном и том же {@code neighbourhoodRatio} получают
     * несравнимые уровни: у первой в допуск попадает что угодно и прямая тянется через шум, у второй
     * не попадает ничего и уровень рвётся на каждом экстремуме. Волатильность приводит обе к одной
     * мерке, и допуск пересчитывается на каждом вызове - рынок меняется, прямая остаётся.
     *
     * @param volatilityCalculator чем мерить волатильность окна; {@code null} возвращает к доле цены
     * @param volatilities         сколько волатильностей составляют допуск
     */
    public LinearLevelDetector setVolatilityTolerance(VolatilityCalculator volatilityCalculator, double volatilities) {
        if (volatilities <= 0) {
            throw new IllegalArgumentException("Допуск должен быть положительным, получено " + volatilities);
        }

        this.volatilityCalculator = volatilityCalculator;
        this.volatilities = volatilities;
        return this;
    }

    public List<Level<Double>> detect(List<Candle> candles) {
        if (linearModel == null) {
            // Check if there are enough candles to calculate support levels
            if (candles.size() < 2) {
                throw new IllegalArgumentException("Not enough data to calculate support levels");
            }

            linearModel = new IncrementalLinearRegression(neighbourhoodRatio);
            startLevelIndex = 0;
        }

        linearModel.setThreshold(toleranceOf(candles));

        // Initialize variables to track the lowest price and its timestamp
        ArrayList<Level<Double>> levels = new ArrayList<>();
        Point2D<Double> lastPoint = null;
        Instant lastTime = null;

        if (startLevelTime == null) {
            startLevelTime = candles.getFirst().getTime();
        }

        List<Candle> extremes = extremeLocator.locate(candles);

        long lastIndex = -1;

        for (Candle extreme : extremes) {
            long extremeIndex = extreme.getIndex();
            Point2D<Double> point = new Point2D<>((double) extremeIndex, extreme.getTypicalAsDouble());

            if (linearModel.addPoint(point)) {
                lastPoint = point;
                lastTime = extreme.getTime();
                lastIndex = extremeIndex;
            } else {
                if (lastPoint != null) {
                    levels.add(
                        createLinearLevel(
                            startLevelTime,
                            lastTime,
                            startLevelIndex,
                            lastIndex,
                            linearModel.getInliers().size(),
                            candles
                        )
                    );
                }

                startLevelTime = extreme.getTime();
                startLevelIndex = (int) extremeIndex;
                linearModel.reset();
                linearModel.addPoint(point);
            }
        }

        if (linearModel.getInliers().size() > 1) {
            Candle lastExtreme = extremes.get(extremes.size() - 1);
            lastIndex = lastExtreme.getIndex();
            lastTime = lastExtreme.getTime();

            levels.add(
                createLinearLevel(
                    startLevelTime,
                    lastTime,
                    startLevelIndex,
                    lastIndex,
                    linearModel.getInliers().size(),
                    candles
                )
            );
        } else if (linearModel.getInliers().isEmpty()) {
            startLevelIndex = (int) (lastIndex + 1);
            startLevelTime = lastTime;
        }

        return Collections.unmodifiableList(levels);
    }

    private Level<Double> createLinearLevel(
        Instant startLevelTime,
        Instant endLevelTime,
        long startLevelIndex,
        long endLevelIndex,
        int touchesCount,
        List<Candle> candles
    ) {
        TimePoint startPoint = new TimePoint(startLevelIndex, startLevelTime);
        TimePoint endPoint = new TimePoint(endLevelIndex, endLevelTime);

        Level<Double> level = new Level<>(
            startPoint,
            endPoint,
            linearModel.getLinearFunction(),
            0,
            touchesCount
        );
        double strength = strengthCalculator.calculate(level, candles);

        return level.withStrength(strength);
    }

    /**
     * Допуск для этого окна: столько волатильностей, сколько задано, выраженные долей от цены -
     * {@link IncrementalLinearRegression} принимает точку по относительной ошибке. Без калькулятора
     * волатильности остаётся заданная доля цены.
     */
    protected double toleranceOf(List<Candle> candles) {
        if (volatilityCalculator == null || candles.isEmpty()) {
            return neighbourhoodRatio;
        }

        double volatility = volatilityCalculator.calculate(candles);
        double price = candles.getLast().getTypicalAsDouble();

        return price <= 0 || volatility <= 0 ? neighbourhoodRatio : volatilities * volatility / price;
    }

    public LinearLevelDetector setStrengthCalculator(StrengthCalculator strengthCalculator) {
        this.strengthCalculator = strengthCalculator;
        return this;
    }

    public static LinearLevelDetector createSupport(
        long frameSize,
        double neighbourhoodRatio
    ) {
        return new LinearLevelDetector(
            neighbourhoodRatio,
            new FrameExtremeLocator(
                frameSize, BaseExtremeLocator.createMinLocator(Candle::getTypicalAsDouble)
            )
        );
    }

    public static LinearLevelDetector createSupport(
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor
    ) {
        return new LinearLevelDetector(
            neighbourhoodRatio,
            new FrameExtremeLocator(
                frameSize, BaseExtremeLocator.createMinLocator(priceExtractor)
            )
        );
    }

    public static LinearLevelDetector createResistance(
        long frameSize,
        double neighbourhoodRatio
    ) {
        return new LinearLevelDetector(
            neighbourhoodRatio,
            new FrameExtremeLocator(
                frameSize, BaseExtremeLocator.createMaxLocator(Candle::getTypicalAsDouble)
            )
        );
    }

    public static LinearLevelDetector createResistance(
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor
    ) {
        return new LinearLevelDetector(
            neighbourhoodRatio,
            new FrameExtremeLocator(
                frameSize, BaseExtremeLocator.createMaxLocator(priceExtractor)
            )
        );
    }
}
