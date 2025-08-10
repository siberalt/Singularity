package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.math.IncrementalLinearRegression;
import com.siberalt.singularity.math.Point2D;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class LinearLevelCalculator {
    record Frame(
        Instant from,
        Instant to,
        long indexFrom,
        long indexTo,
        Point2D<Double> keyPoint
    ) {
    }

    private final ReadCandleRepository candleRepository;
    private final long frameSize;
    private final double neighbourhoodRatio; // Default neighborhood percentage for support level calculation
    private final Function<List<Candle>, Point2D<Double>> frameAggregator;
    private StrengthCalculator<Double> strengthCalculator = new DoubleStrengthCalculator();

    public LinearLevelCalculator(
        ReadCandleRepository candleRepository,
        long frameSize,
        double neighbourhoodRatio,
        Function<List<Candle>, Point2D<Double>> frameAggregator
    ) {
        this.candleRepository = candleRepository;
        this.frameSize = frameSize;
        this.neighbourhoodRatio = neighbourhoodRatio;
        this.frameAggregator = frameAggregator;
    }

    public Result<Double> calculate(String instrumentId, Instant from, Instant to) {
        // Fetch candles for the given instrument and time range
        var candles = candleRepository.getPeriod(instrumentId, from, to);

        // Check if there are enough candles to calculate support levels
        if (candles.size() < 2) {
            throw new IllegalArgumentException("Not enough data to calculate support levels");
        }

        IncrementalLinearRegression incrementalLinearRegression = new IncrementalLinearRegression(neighbourhoodRatio);

        // Initialize variables to track the lowest price and its timestamp
        ArrayList<Result.Level<Double>> levels = new ArrayList<>();
        Point2D<Double> lastPoint = null;
        Instant lastTime = null;
        List<Frame> frames = extractFrames(candles, frameSize, frameAggregator);

        long startLevelIndex = 0, lastIndex = -1;
        Instant startLevelTime = frames.get(0).from;

        for (Frame frame : frames) {
            Point2D<Double> point = frame.keyPoint;

            if (incrementalLinearRegression.addPoint(point)) {
                lastPoint = point;
                lastIndex = frame.indexTo;
                lastTime = frame.to;
            } else {
                if (lastPoint != null) {
                    StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<>(
                        incrementalLinearRegression.getLinearFunction(),
                        startLevelIndex,
                        lastIndex,
                        incrementalLinearRegression.getInliers().size(),
                        frameSize
                    );
                    levels.add(
                        new Result.Level<>(
                            startLevelTime,
                            lastTime,
                            startLevelIndex,
                            lastIndex,
                            incrementalLinearRegression.getLinearFunction(),
                            strengthCalculator.calculate(context)
                        )
                    );
                }
                startLevelTime = frame.from;
                startLevelIndex = frame.indexFrom;
                incrementalLinearRegression.reset();
                incrementalLinearRegression.addPoint(point);
            }
        }

        if (incrementalLinearRegression.getInliers().size() > 0) {
            StrengthCalculator.LevelContext<Double> context = new StrengthCalculator.LevelContext<>(
                incrementalLinearRegression.getLinearFunction(),
                startLevelIndex,
                frames.get(frames.size() - 1).indexTo,
                incrementalLinearRegression.getInliers().size(),
                frameSize
            );
            levels.add(
                new Result.Level<>(
                    startLevelTime,
                    frames.get(frames.size() - 1).to,
                    startLevelIndex,
                    frames.get(frames.size() - 1).indexTo,
                    incrementalLinearRegression.getLinearFunction(),
                    strengthCalculator.calculate(context)
                )
            );
        }

        return new Result<>(instrumentId, levels);
    }

    public LinearLevelCalculator setStrengthCalculator(StrengthCalculator<Double> strengthCalculator) {
        this.strengthCalculator = strengthCalculator;
        return this;
    }

    public static LinearLevelCalculator createSupport(
        ReadCandleRepository candleRepository,
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor,
        double priceMarginPercentage
    ) {
        return new LinearLevelCalculator(
            candleRepository,
            frameSize,
            neighbourhoodRatio,
            getFrameAggregator(
                priceExtractor,
                Comparator.naturalOrder(),
                priceMarginPercentage
            )
        );
    }

    public static LinearLevelCalculator createResistance(
        ReadCandleRepository candleRepository,
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor,
        double priceMarginPercentage
    ) {
        return new LinearLevelCalculator(
            candleRepository,
            frameSize,
            neighbourhoodRatio,
            getFrameAggregator(
                priceExtractor,
                Comparator.reverseOrder(),
                priceMarginPercentage
            )
        );
    }

    public static LinearLevelCalculator createSupport(
        ReadCandleRepository candleRepository,
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor
    ) {
        return createSupport(
            candleRepository,
            frameSize,
            neighbourhoodRatio,
            priceExtractor,
            0.0 // Default price margin percentage
        );
    }

    public static LinearLevelCalculator createResistance(
        ReadCandleRepository candleRepository,
        long frameSize,
        double neighbourhoodRatio,
        Function<Candle, Double> priceExtractor
    ) {
        return createResistance(
            candleRepository,
            frameSize,
            neighbourhoodRatio,
            priceExtractor,
            0.0 // Default price margin percentage
        );
    }

    private List<Frame> extractFrames(
        List<Candle> candles,
        long frameSize,
        Function<List<Candle>, Point2D<Double>> frameAggregator
    ) {
        List<Frame> frames = new ArrayList<>();
        ArrayList<Candle> currentFrameCandles = new ArrayList<>();
        long currentX = 0;
        Instant startFrameTime = null;
        long startFrameIndex = 0;

        for (var candle : candles) {
            if (null == startFrameTime) {
                startFrameTime = candle.getTime();
                startFrameIndex = currentX;
            }

            currentFrameCandles.add(candle);

            if (currentX % frameSize == frameSize - 1) {
                Point2D<Double> point2D = frameAggregator.apply(currentFrameCandles);

                frames.add(
                    new Frame(
                        startFrameTime,
                        candle.getTime(),
                        startFrameIndex,
                        currentX,
                        new Point2D<>(point2D.x() + startFrameIndex, point2D.y())
                    )
                );
                startFrameTime = null;
                currentFrameCandles.clear();
            }

            currentX++;
        }

        if (!currentFrameCandles.isEmpty()) {
            Point2D<Double> point2D = frameAggregator.apply(currentFrameCandles);

            frames.add(
                new Frame(
                    startFrameTime,
                    candles.get(candles.size() - 1).getTime(),
                    startFrameIndex,
                    currentX - 1,
                    new Point2D<>(point2D.x() + startFrameIndex, point2D.y())
                )
            );
        }

        return frames;
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
