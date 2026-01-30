package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.median.MedianCalculator;
import com.siberalt.singularity.math.median.RobustMedianCalculator;
import com.siberalt.singularity.strategy.extreme.BaseExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ConcurrentFrameExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.StatefulLevelDetector;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ClusterLevelDetector implements StatefulLevelDetector {
    private static final int MAX_LEVELS = 30;

    private record LevelDetails(Level<Double> level, double price, int touchesCount, List<Candle> extremes) {
    }

    private final Map<Double, Function<Double, Double>> functionsCache = new TreeMap<>();
    private final TreeMap<Double, LevelDetails> levelDetails = new TreeMap<>();
    private final double sensitivity;
    private final ExtremeLocator extremeLocator;
    private final MedianCalculator medianCalculator = new RobustMedianCalculator();
    private StrengthCalculator strengthCalculator = new BasicStrengthCalculator();

    // Параметры для управления "забыванием" старых уровней
    private final Duration levelTTL = Duration.ofDays(30);
    private int maxLevels = MAX_LEVELS;

    // Статистика для адаптивной чувствительности
    private double priceVolatility = 0.0;

    public ClusterLevelDetector(double sensitivity, ExtremeLocator extremeLocator) {
        this.sensitivity = sensitivity;
        this.extremeLocator = extremeLocator;
    }

    public ClusterLevelDetector(double sensitivity, ExtremeLocator extremeLocator, int maxLevels) {
        this.sensitivity = sensitivity;
        this.extremeLocator = extremeLocator;
        this.maxLevels = maxLevels;
    }

    public ClusterLevelDetector setStrengthCalculator(StrengthCalculator strengthCalculator) {
        this.strengthCalculator = strengthCalculator;
        return this;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return getCurrentLevels(); // Возвращаем текущие уровни, а не пустой список
        }

        // Очищаем устаревшие уровни перед обработкой новых данных
        cleanupOldLevels(candles.get(candles.size() - 1).getTime());

        // Обновляем статистику волатильности
        updateMarketStatistics(candles);

        // Обрабатываем только новые экстремумы
        List<Candle> newExtremes = extremeLocator.locate(candles);
        Map<Double, List<Candle>> levelsNewExtremes = new TreeMap<>();

        for (Candle extreme : newExtremes) {
            double price = extreme.getTypicalPriceAsDouble();

            // Адаптивная чувствительность на основе волатильности
            double adaptiveSensitivity = calculateAdaptiveSensitivity(price);
            double sensitivityRange = price * adaptiveSensitivity;

            Optional<Map.Entry<Double, LevelDetails>> closestLevel = findClosestLevel(price, sensitivityRange);

            if (closestLevel.isPresent()) {
                levelsNewExtremes
                    .computeIfAbsent(closestLevel.get().getKey(), k -> new ArrayList<>())
                    .add(extreme);
            } else {
                createNewLevel(extreme);
            }
        }

        for (Map.Entry<Double, List<Candle>> entry : levelsNewExtremes.entrySet()) {
            List<Candle> levelExtremes = levelDetails.get(entry.getKey()).extremes();
            List<Candle> newLevelExtremes = entry.getValue();
            levelExtremes.addAll(newLevelExtremes);

            Candle lastExtreme = newLevelExtremes.get(newLevelExtremes.size() - 1);
            long indexTo = lastExtreme.getIndex();
            Instant timeTo = lastExtreme.getTime();

            double updatedPrice = medianCalculator.calculateMedian(
                new ArrayList<>(levelExtremes.stream()
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
                    existingDetails.touchesCount() + newLevelExtremes.size()
                )
            );
            LevelDetails newDetails = new LevelDetails(
                updatedLevel,
                updatedPrice,
                existingDetails.touchesCount() + newLevelExtremes.size(),
                levelExtremes
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

    private void createNewLevel(Candle candle) {
        double price = candle.getTypicalPriceAsDouble();
        Instant time = candle.getTime();
        long index = candle.getIndex();

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
            ConcurrentFrameExtremeLocator.builder(BaseExtremeLocator.createMinLocator(Candle::getTypicalPriceAsDouble))
                .setFrameSize(frameSize)
                .setExtremeVicinity(15)
                .build()
        );
    }

    public static ClusterLevelDetector createResistance(
        int frameSize,
        double sensitivity
    ) {
        return new ClusterLevelDetector(
            sensitivity,
            ConcurrentFrameExtremeLocator.builder(BaseExtremeLocator.createMaxLocator(Candle::getTypicalPriceAsDouble))
                .setFrameSize(frameSize)
                .setExtremeVicinity(15)
                .build()
        );
    }
}
