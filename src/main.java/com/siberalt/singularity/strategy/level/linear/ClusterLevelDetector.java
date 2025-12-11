package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.strategy.extremum.BaseExtremumLocator;
import com.siberalt.singularity.strategy.extremum.ConcurrentFrameExtremumLocator;
import com.siberalt.singularity.strategy.extremum.ExtremumLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.StatefulLevelDetector;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClusterLevelDetector implements StatefulLevelDetector {
    private static final int MAX_LEVELS = 30;

    private record LevelDetails(Level<Double> level, double price, int touchesCount, List<Candle> extremums) {
    }

    private final Map<Double, Function<Double, Double>> functionsCache = new TreeMap<>();
    private final TreeMap<Double, LevelDetails> levelDetails = new TreeMap<>();
    private final double sensitivity;
    private final ExtremumLocator extremumLocator;
    private final MedianCalculator medianCalculator = new RobustMedianCalculator();
    private StrengthCalculator strengthCalculator = new BasicStrengthCalculator();

    // Параметры для управления "забыванием" старых уровней
    private final Duration levelTTL = Duration.ofDays(30);
    private int maxLevels = MAX_LEVELS;

    // Статистика для адаптивной чувствительности
    private double priceVolatility = 0.0;

    public ClusterLevelDetector(double sensitivity, ExtremumLocator extremumLocator) {
        this.sensitivity = sensitivity;
        this.extremumLocator = extremumLocator;
    }

    public ClusterLevelDetector(double sensitivity, ExtremumLocator extremumLocator, int maxLevels) {
        this.sensitivity = sensitivity;
        this.extremumLocator = extremumLocator;
        this.maxLevels = maxLevels;
    }

    public ClusterLevelDetector setStrengthCalculator(StrengthCalculator strengthCalculator) {
        this.strengthCalculator = strengthCalculator;
        return this;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles, CandleIndexProvider candleIndexProvider) {
        if (candles == null || candles.isEmpty()) {
            return getCurrentLevels(); // Возвращаем текущие уровни, а не пустой список
        }

        // Очищаем устаревшие уровни перед обработкой новых данных
        cleanupOldLevels(candles.get(candles.size() - 1).getTime());

        // Обновляем статистику волатильности
        updateMarketStatistics(candles);

        // Обрабатываем только новые экстремумы
        List<Candle> newExtremums = extremumLocator.locate(candles, candleIndexProvider);
        Map<Double, List<Candle>> levelsNewExtremums = new TreeMap<>();

        for (Candle extremum : newExtremums) {
            double price = extremum.getTypicalPriceAsDouble();

            // Адаптивная чувствительность на основе волатильности
            double adaptiveSensitivity = calculateAdaptiveSensitivity(price);
            double sensitivityRange = price * adaptiveSensitivity;

            Optional<Map.Entry<Double, LevelDetails>> closestLevel = findClosestLevel(price, sensitivityRange);

            if (closestLevel.isPresent()) {
                levelsNewExtremums
                    .computeIfAbsent(closestLevel.get().getKey(), k -> new ArrayList<>())
                    .add(extremum);
            } else {
                createNewLevel(extremum, candleIndexProvider);
            }
        }

        for (Map.Entry<Double, List<Candle>> entry : levelsNewExtremums.entrySet()) {
            List<Candle> levelExtremums = levelDetails.get(entry.getKey()).extremums();
            List<Candle> newLevelExtremums = entry.getValue();
            levelExtremums.addAll(newLevelExtremums);

            Candle lastExtremum = newLevelExtremums.get(newLevelExtremums.size() - 1);
            long indexTo = candleIndexProvider.provideIndex(lastExtremum);
            Instant timeTo = lastExtremum.getTime();

            double updatedPrice = medianCalculator.calculateMedian(
                new ArrayList<>(levelExtremums.stream()
                    .map(Candle::getTypicalPriceAsDouble)
                    .collect(Collectors.toList()))
            );

            Function<Double, Double> function = x -> updatedPrice;
            LevelDetails existingDetails = levelDetails.get(entry.getKey());
            Level<Double> updatedLevel = new Level<>(
                existingDetails.level().timeFrom(),
                timeTo,
                existingDetails.level().indexFrom(),
                indexTo,
                function,
                recalculateStrength(
                    new Level<>(
                        existingDetails.level().timeFrom(),
                        timeTo,
                        existingDetails.level().indexFrom(),
                        indexTo,
                        function,
                        existingDetails.level().strength()
                    ),
                    existingDetails.touchesCount() + newLevelExtremums.size()
                )
            );
            LevelDetails newDetails = new LevelDetails(
                updatedLevel,
                updatedPrice,
                existingDetails.touchesCount() + newLevelExtremums.size(),
                levelExtremums
            );
            levelDetails.remove(entry.getKey());
            levelDetails.put(updatedPrice, newDetails);
            updateFunctionCache(updatedPrice);
        }

        return getCurrentLevels();
    }

    /**
     * Очищает устаревшие уровни и уровни с низкой значимостью
     */
    private void cleanupOldLevels(Instant currentTime) {
        // Удаляем уровни, которые не обновлялись дольше TTL
        levelDetails.entrySet().removeIf(entry ->
            Duration.between(entry.getValue().level().timeTo(), currentTime).compareTo(levelTTL) > 0
        );

        // Если все еще слишком много уровней, удаляем самые слабые
        if (levelDetails.size() > maxLevels) {
            removeWeakestLevels(levelDetails.size() - maxLevels);
        }
    }

    private Optional<Map.Entry<Double, LevelDetails>> findClosestLevel(double price, double sensitivityRange) {
        return levelDetails.subMap(price - sensitivityRange, true, price + sensitivityRange, true)
            .entrySet()
            .stream()
            .min(Comparator.comparingDouble(entry -> Math.abs(entry.getKey() - price)));
    }

    private double recalculateStrength(Level<Double> updatedLevel, int touchesCount) {
        StrengthCalculator.LevelContext context = new StrengthCalculator.LevelContext(
            updatedLevel.timeFrom(),
            updatedLevel.timeTo(),
            updatedLevel.indexFrom(),
            updatedLevel.indexTo(),
            updatedLevel.function(),
            updatedLevel.strength(),
            touchesCount
        );
        return strengthCalculator.calculate(context);
    }

    private void createNewLevel(Candle candle, CandleIndexProvider candleIndexProvider) {
        double price = candle.getTypicalPriceAsDouble();
        Instant time = candle.getTime();
        long index = candleIndexProvider.provideIndex(candle);

        Function<Double, Double> function = x -> price;
        Level<Double> newLevel = new Level<>(time, time, index, index, function, 0);

        levelDetails.put(price, new LevelDetails(newLevel, price, 1, new ArrayList<>(List.of(candle))));
        functionsCache.put(price, function);
    }

    private boolean isSignificantLevel(LevelDetails levelDetails) {
        // Уровень считается значимым если:
        // 1. Имеет более одного касания ИЛИ
        // 2. Имеет достаточную силу И
        // 3. Временной диапазон достаточно большой
        return levelDetails.touchesCount() > 1;
    }

    private void updateFunctionCache(double price) {
        functionsCache.computeIfAbsent(price, p -> x -> p);
    }

    /**
     * Адаптивная чувствительность на основе текущей волатильности рынка
     */
    private double calculateAdaptiveSensitivity(double currentPrice) {
        return sensitivity;
//        if (priceVolatility == 0.0) {
//            return sensitivity;
//        }
//
//        // Увеличиваем чувствительность в периоды высокой волатильности
//        double volatilityFactor = Math.min(2.0, priceVolatility / (currentPrice * 0.01));
//        return sensitivity * volatilityFactor;
    }

    /**
     * Обновляет статистику рынка для адаптивной настройки
     */
    private void updateMarketStatistics(List<Candle> candles) {
        if (candles.size() < 2) {
            return;
        }

        // Вычисляем волатильность как среднее отклонение цен
        double sumDeviations = 0.0;
        for (int i = 1; i < candles.size(); i++) {
            double change = Math.abs(candles.get(i).getTypicalPriceAsDouble() -
                candles.get(i - 1).getTypicalPriceAsDouble());
            sumDeviations += change;
        }
        this.priceVolatility = sumDeviations / (candles.size() - 1);
    }

    private void removeWeakestLevels(int countToRemove) {
        levelDetails.entrySet().stream()
            .sorted(Comparator.comparingDouble(entry -> entry.getValue().level.strength()))
            .limit(countToRemove)
            .forEach(entry -> levelDetails.remove(entry.getKey()));
    }

    /**
     * Возвращает текущие значимые уровни
     */
    private List<Level<Double>> getCurrentLevels() {
        return levelDetails.values().stream()
            .filter(this::isSignificantLevel)
            .sorted(Comparator.comparingDouble(ld -> -ld.level.strength()))
            .map(LevelDetails::level)
            .collect(Collectors.toList());
    }

    // Дополнительные методы для управления состоянием

    public void reset() {
        levelDetails.clear();
        functionsCache.clear();
        priceVolatility = 0.0;
    }

    public static ClusterLevelDetector createSupport(
        int frameSize,
        double sensitivity
    ) {
        return new ClusterLevelDetector(
            sensitivity,
            new ConcurrentFrameExtremumLocator(
                frameSize,
                BaseExtremumLocator.createMinLocator(Candle::getTypicalPriceAsDouble),
                Runtime.getRuntime().availableProcessors(),
                15
            )
        );
    }

    public static ClusterLevelDetector createResistance(
        int frameSize,
        double sensitivity
    ) {
        return new ClusterLevelDetector(
            sensitivity,
            new ConcurrentFrameExtremumLocator(
                frameSize,
                BaseExtremumLocator.createMaxLocator(Candle::getTypicalPriceAsDouble),
                Runtime.getRuntime().availableProcessors(),
                15
            )
        );
    }
}
