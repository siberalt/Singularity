package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * Локатор последнего подтверждённого экстремума (минимума или максимума).
 * <p>
 * Экстремум считается подтверждённым, если:
 * <ul>
 *   <li>Все {@code extremeVicinity} свечей в будущем — строго более экстремальны</li>
 *   <li>Все {@code extremeVicinity} свечей в прошлом — строго менее экстремальны</li>
 * </ul>
 * Допускается до {@code maxAllowedEqualPeers} свечей с равной ценой в окрестности.
 * </p>
 */
public class LastExtremeLocator implements ExtremeLocator {
    public static int DEFAULT_EXTREME_VICINITY = 2;
    public static int DEFAULT_MAX_EQUAL_PEERS = 0; // по умолчанию — не допускаем равных

    private final int extremeVicinity;
    private final int maxAllowedEqualPeers;
    private final Comparator<Candle> comparator;

    public LastExtremeLocator(Comparator<Candle> comparator) {
        this(DEFAULT_EXTREME_VICINITY, DEFAULT_MAX_EQUAL_PEERS, comparator);
    }

    public LastExtremeLocator(int extremeVicinity, Comparator<Candle> comparator) {
        this(extremeVicinity, DEFAULT_MAX_EQUAL_PEERS, comparator);
    }

    public LastExtremeLocator(int extremeVicinity, int maxAllowedEqualPeers, Comparator<Candle> comparator) {
        if (extremeVicinity < 0) {
            throw new IllegalArgumentException("Extreme vicinity must be non-negative");
        }
        if (maxAllowedEqualPeers < 0 || maxAllowedEqualPeers > extremeVicinity) {
            throw new IllegalArgumentException("Max allowed equal peers must be in range [0, extremeVicinity]");
        }
        this.extremeVicinity = extremeVicinity;
        this.maxAllowedEqualPeers = maxAllowedEqualPeers;
        this.comparator = comparator;
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        if (candles == null) {
            throw new IllegalArgumentException("Candles list cannot be null");
        }
        if (candles.size() < 2 * extremeVicinity + 1) {
            return List.of();
        }
        if (extremeVicinity == 0) {
            return List.of(candles.get(candles.size() - 1));
        }

        // Идём с конца к началу, начиная с позиции, где может быть экстремум
        for (int i = candles.size() - extremeVicinity - 1; i >= extremeVicinity; i--) {
            Candle candidate = candles.get(i);

            // Проверяем обе стороны с учётом допустимых равных
            if (isConfirmedExtreme(candidate, i, candles)) {
                return List.of(candidate);
            }
        }

        return List.of();
    }

    private boolean isConfirmedExtreme(Candle candidate, int index, List<Candle> candles) {
        int equalCount = 0;

        // Проверка будущего: все должны быть >= экстремума, но не более maxAllowedEqualPeers с равными
        for (int j = 1; j <= extremeVicinity; j++) {
            int cmp = comparator.compare(candidate, candles.get(index + j));
            if (cmp > 0) {
                return false;// дальше уже точно нет
            } else if (cmp == 0) {
                equalCount++;
                if (equalCount > maxAllowedEqualPeers) {
                    return false;
                }
            }
        }

        equalCount = 0; // сбрасываем для прошлого

        // Проверка прошлого: все должны быть >= экстремума
        for (int j = 1; j <= extremeVicinity; j++) {
            int cmp = comparator.compare(candidate, candles.get(index - j));
            if (cmp > 0) {
                return false;// дальнейшие тоже
            } else if (cmp == 0) {
                equalCount++;
                if (equalCount > maxAllowedEqualPeers) {
                    return false;
                }
            }
        }

        return true;
    }

    // --- Фабричные методы ---

    public static LastExtremeLocator ofMinimums(int extremeVicinity, Function<Candle, Double> priceExtractor) {
        return new LastExtremeLocator(extremeVicinity, Comparator.comparing(priceExtractor));
    }

    public static LastExtremeLocator ofMaximums(int extremeVicinity, Function<Candle, Double> priceExtractor) {
        return new LastExtremeLocator(extremeVicinity, Comparator.comparing(priceExtractor).reversed());
    }

    public static LastExtremeLocator ofMinimums(int extremeVicinity, int maxAllowedEqualPeers, Function<Candle, Double> priceExtractor) {
        return new LastExtremeLocator(extremeVicinity, maxAllowedEqualPeers, Comparator.comparing(priceExtractor));
    }

    public static LastExtremeLocator ofMaximums(int extremeVicinity, int maxAllowedEqualPeers, Function<Candle, Double> priceExtractor) {
        return new LastExtremeLocator(extremeVicinity, maxAllowedEqualPeers, Comparator.comparing(priceExtractor).reversed());
    }

    public static LastExtremeLocator ofMinimums(Function<Candle, Double> priceExtractor) {
        return ofMinimums(DEFAULT_EXTREME_VICINITY, priceExtractor);
    }

    public static LastExtremeLocator ofMaximums(Function<Candle, Double> priceExtractor) {
        return ofMaximums(DEFAULT_EXTREME_VICINITY, priceExtractor);
    }

    public static LastExtremeLocator ofMinimums() {
        return ofMinimums(Candle::getTypicalAsDouble);
    }

    public static LastExtremeLocator ofMaximums() {
        return ofMaximums(Candle::getTypicalAsDouble);
    }
}
