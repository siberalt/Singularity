package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.IncrementalLinearRegression;
import com.siberalt.singularity.math.Point2D;
import com.siberalt.singularity.strategy.extreme.BaseExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.FrameExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;

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

    public LinearLevelDetector(
        double neighbourhoodRatio,
        ExtremeLocator extremeLocator
    ) {
        this.neighbourhoodRatio = neighbourhoodRatio;
        this.extremeLocator = extremeLocator;
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

        // Initialize variables to track the lowest price and its timestamp
        ArrayList<Level<Double>> levels = new ArrayList<>();
        Point2D<Double> lastPoint = null;
        Instant lastTime = null;

        if (startLevelTime == null) {
            startLevelTime = candles.get(0).getTime();
        }

        List<Candle> extremes = extremeLocator.locate(candles);

        long lastIndex = -1;

        for (Candle extreme : extremes) {
            long extremeIndex = extreme.getIndex();
            Point2D<Double> point = new Point2D<>((double) extremeIndex, extreme.getTypicalPrice().toDouble());

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
                            linearModel.getInliers().size()
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
                    linearModel.getInliers().size()
                )
            );
        } else if (linearModel.getInliers().size() == 0) {
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
        int touchesCount
    ) {
        StrengthCalculator.LevelContext context = new StrengthCalculator.LevelContext(
            startLevelTime,
            endLevelTime,
            startLevelIndex,
            endLevelIndex,
            linearModel.getLinearFunction(),
            0,
            touchesCount
        );

        double strength = strengthCalculator.calculate(context);

        return new Level<>(
            startLevelTime,
            endLevelTime,
            startLevelIndex,
            endLevelIndex,
            linearModel.getLinearFunction(),
            strength
        );
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
                frameSize, BaseExtremeLocator.createMinLocator(candle -> candle.getTypicalPrice().toDouble())
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
                frameSize, BaseExtremeLocator.createMaxLocator(candle -> candle.getTypicalPrice().toDouble())
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
