package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.IncrementalLinearRegression;
import com.siberalt.singularity.math.Point2D;
import com.siberalt.singularity.strategy.extremum.BaseExtremumLocator;
import com.siberalt.singularity.strategy.extremum.ExtremumLocator;
import com.siberalt.singularity.strategy.extremum.FrameExtremumLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class LinearLevelDetector implements LevelDetector<Double> {
    private long startLevelIndex;
    private Instant startLevelTime = null;
    private IncrementalLinearRegression linearModel;
    private final double neighbourhoodRatio; // Default neighborhood percentage for support level calculation
    private StrengthCalculator strengthCalculator = new BasicStrengthCalculator();
    private final ExtremumLocator extremumLocator;

    public LinearLevelDetector(
        double neighbourhoodRatio,
        ExtremumLocator extremumLocator
    ) {
        this.neighbourhoodRatio = neighbourhoodRatio;
        this.extremumLocator = extremumLocator;
    }

    public List<Level<Double>> detect(List<Candle> candles, CandleIndexProvider candleIndexProvider) {
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

        List<Candle> extremums = extremumLocator.locate(candles);

        long lastIndex = -1;

        for (Candle extremum : extremums) {
            long extremumIndex = candleIndexProvider.provideIndex(extremum);
            Point2D<Double> point = new Point2D<>((double) extremumIndex, extremum.getTypicalPrice().toDouble());

            if (linearModel.addPoint(point)) {
                lastPoint = point;
                lastTime = extremum.getTime();
                lastIndex = extremumIndex;
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

                startLevelTime = extremum.getTime();
                startLevelIndex = (int) extremumIndex;
                linearModel.reset();
                linearModel.addPoint(point);
            }
        }

        if (linearModel.getInliers().size() > 1) {
            lastIndex = candleIndexProvider.provideIndex(extremums.get(extremums.size() - 1));
            lastTime = extremums.get(extremums.size() - 1).getTime();
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
            new FrameExtremumLocator(
                frameSize, BaseExtremumLocator.createMinLocator(candle -> candle.getTypicalPrice().toDouble())
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
            new FrameExtremumLocator(
                frameSize, BaseExtremumLocator.createMinLocator(priceExtractor)
            )
        );
    }

    public static LinearLevelDetector createResistance(
        long frameSize,
        double neighbourhoodRatio
    ) {
        return new LinearLevelDetector(
            neighbourhoodRatio,
            new FrameExtremumLocator(
                frameSize, BaseExtremumLocator.createMaxLocator(candle -> candle.getTypicalPrice().toDouble())
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
            new FrameExtremumLocator(
                frameSize, BaseExtremumLocator.createMaxLocator(priceExtractor)
            )
        );
    }
}
