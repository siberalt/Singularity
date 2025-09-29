package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.IncrementalLinearRegression;
import com.siberalt.singularity.math.Point2D;
import com.siberalt.singularity.strategy.level.LevelDetector;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class LinearLevelDetector implements LevelDetector<Double> {
    record Frame(
        Instant from,
        Instant to,
        long indexFrom,
        long indexTo,
        Point2D<Double> keyPoint
    ) {
    }

    private long currentCandleIndex = 0;
    private long startLevelIndex;
    private Instant startLevelTime = null;
    private IncrementalLinearRegression linearModel;
    private final long frameSize;
    private final double neighbourhoodRatio; // Default neighborhood percentage for support level calculation
    private final Function<List<Candle>, Point2D<Double>> frameAggregator;
    private StrengthCalculator<Double> strengthCalculator = new BasicStrengthCalculator();

    public LinearLevelDetector(
        long frameSize,
        double neighbourhoodRatio,
        Function<List<Candle>, Point2D<Double>> frameAggregator
    ) {
        this.frameSize = frameSize;
        this.neighbourhoodRatio = neighbourhoodRatio;
        this.frameAggregator = frameAggregator;
    }

    public List<LinearLevel<Double>> detect(List<Candle> candles) {
        if (linearModel == null) {
            // Check if there are enough candles to calculate support levels
            if (candles.size() < 2) {
                throw new IllegalArgumentException("Not enough data to calculate support levels");
            }

            linearModel = new IncrementalLinearRegression(neighbourhoodRatio);
            startLevelIndex = 0;
        }

        // Initialize variables to track the lowest price and its timestamp
        ArrayList<LinearLevel<Double>> levels = new ArrayList<>();
        Point2D<Double> lastPoint = null;
        Instant lastTime = null;
        startLevelTime = null == startLevelTime
            ? candles.get(0).getTime()
            : startLevelTime;

        List<Frame> frames = extractFrames(startLevelTime, candles, frameSize, frameAggregator);

        long lastIndex = -1;

        for (Frame frame : frames) {
            Point2D<Double> point = frame.keyPoint;

            if (linearModel.addPoint(point)) {
                lastPoint = point;
                lastIndex = frame.indexTo;
                lastTime = frame.to;
            } else {
                if (lastPoint != null) {
                    levels.add(
                        createLinearLevel(
                            startLevelTime,
                            lastTime,
                            startLevelIndex,
                            lastIndex,
                            frameSize
                        )
                    );
                }

                startLevelTime = frame.from;
                startLevelIndex = frame.indexFrom;
                linearModel.reset();
                linearModel.addPoint(point);
            }
        }

        if (linearModel.getInliers().size() > 1) {
            lastIndex = frames.get(frames.size() - 1).indexTo;
            levels.add(
                createLinearLevel(
                    startLevelTime,
                    frames.get(frames.size() - 1).to,
                    startLevelIndex,
                    lastIndex,
                    frameSize
                )
            );
        } else if (linearModel.getInliers().size() == 0) {
            startLevelIndex = lastIndex + 1;
            startLevelTime = lastTime;
        }

        return Collections.unmodifiableList(levels);
    }

    private LinearLevel<Double> createLinearLevel(
        Instant startLevelTime,
        Instant lastTime,
        long startLevelIndex,
        long lastIndex,
        long frameSize
    ) {
        StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<>(
            linearModel.getLinearFunction(),
            startLevelIndex,
            lastIndex,
            linearModel.getInliers().size(),
            frameSize
        );

        return new LinearLevel<>(
            startLevelTime,
            lastTime,
            startLevelIndex,
            lastIndex,
            linearModel.getLinearFunction(),
            strengthCalculator.calculate(context)
        );
    }

    public LinearLevelDetector setStrengthCalculator(StrengthCalculator<Double> strengthCalculator) {
        this.strengthCalculator = strengthCalculator;
        return this;
    }

    private List<Frame> extractFrames(
        Instant startTime,
        List<Candle> candles,
        long frameSize,
        Function<List<Candle>, Point2D<Double>> frameAggregator
    ) {
        List<Frame> frames = new ArrayList<>();
        ArrayList<Candle> currentFrameCandles = new ArrayList<>();
        Instant startFrameTime = startTime;
        long startFrameIndex = currentCandleIndex;

        for (var candle : candles) {
            if (null == startFrameTime) {
                startFrameTime = candle.getTime();
            }

            currentFrameCandles.add(candle);

            if (currentFrameCandles.size() == frameSize) {
                Point2D<Double> keyPoint = frameAggregator.apply(currentFrameCandles);

                frames.add(
                    new Frame(
                        startFrameTime,
                        candle.getTime(),
                        startFrameIndex,
                        currentCandleIndex,
                        new Point2D<>((double) startFrameIndex + keyPoint.x(), keyPoint.y())
                    )
                );
                startFrameIndex = currentCandleIndex + 1;
                startFrameTime = null;
                currentFrameCandles.clear();
            }

            currentCandleIndex++;
        }

        if (!currentFrameCandles.isEmpty()) {
            Point2D<Double> keyPoint = frameAggregator.apply(currentFrameCandles);

            frames.add(
                new Frame(
                    startFrameTime,
                    candles.get(candles.size() - 1).getTime(),
                    startFrameIndex,
                    currentCandleIndex - 1,
                    new Point2D<>((double) startFrameIndex + keyPoint.x(), keyPoint.y())
                )
            );
        }

        return frames;
    }

    public static LinearLevelDetector createSupport(
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor,
        double priceMarginPercentage
    ) {
        return new LinearLevelDetector(
            frameSize,
            neighbourhoodRatio,
            getFrameAggregator(
                priceExtractor,
                Comparator.naturalOrder(),
                priceMarginPercentage
            )
        );
    }

    public static LinearLevelDetector createResistance(
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor,
        double priceMarginPercentage
    ) {
        return new LinearLevelDetector(
            frameSize,
            neighbourhoodRatio,
            getFrameAggregator(
                priceExtractor,
                Comparator.reverseOrder(),
                priceMarginPercentage
            )
        );
    }

    public static LinearLevelDetector createSupport(
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor
    ) {
        return createSupport(
            frameSize,
            neighbourhoodRatio,
            priceExtractor,
            0.0 // Default price margin percentage
        );
    }

    public static LinearLevelDetector createResistance(
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor
    ) {
        return createResistance(
            frameSize,
            neighbourhoodRatio,
            priceExtractor,
            0.0 // Default price margin percentage
        );
    }

    private static Function<List<Candle>, Point2D<Double>> getFrameAggregator(
        Function<Candle, Double> priceExtractor,
        Comparator<Double> comparator,
        double priceMarginPercentage
    ) {
        return candles -> {
            int targetIndex = 0;
            double targetPrice = priceExtractor.apply(candles.get(0));

            for (int i = 1; i < candles.size(); i++) {
                double price = priceExtractor.apply(candles.get(i));
                if (comparator.compare(price, targetPrice) < 0) {
                    targetPrice = price;
                    targetIndex = i;
                }
            }

            // Adjust the target price based on the margin percentage
            if (Math.abs(priceMarginPercentage) > 1e-6) {
                double margin = targetPrice * priceMarginPercentage;
                targetPrice += margin;
            }

            return new Point2D<>((double) targetIndex, targetPrice);
        };
    }
}
