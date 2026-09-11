package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PivotPointExtremeLocator implements ExtremeLocator {
    public static final int DEFAULT_LEFT_VICINITY = 5;
    public static final int DEFAULT_RIGHT_VICINITY = 5;
    public static final int DEFAULT_EXTREME_AREA = 10;

    private final int leftVicinity;
    private final int rightVicinity;
    private final Comparator<Candle> comparator;
    private final int extremeArea;

    // Конструктор с настраиваемыми буферами
    public PivotPointExtremeLocator(Comparator<Candle> comparator, int leftVicinity, int rightVicinity) {
        this(comparator, leftVicinity, rightVicinity, DEFAULT_EXTREME_AREA);
    }

    public PivotPointExtremeLocator(Comparator<Candle> comparator, int leftVicinity, int rightVicinity, int extremeArea) {
        if (leftVicinity < 1 || rightVicinity < 1) {
            throw new IllegalArgumentException("Buffer values must be at least 1");
        }
        this.leftVicinity = leftVicinity;
        this.rightVicinity = rightVicinity;
        this.comparator = comparator;
        this.extremeArea = extremeArea;
    }

    // Конструктор по умолчанию: 1 слева и 1 справа
    public PivotPointExtremeLocator(Comparator<Candle> comparator) {
        this(comparator, DEFAULT_LEFT_VICINITY, DEFAULT_RIGHT_VICINITY, DEFAULT_EXTREME_AREA);
    }

    public PivotPointExtremeLocator(Comparator<Candle> comparator, int vicinity) {
        this(comparator, vicinity, vicinity);
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        if (candles == null || candles.size() <= leftVicinity + rightVicinity) {
            return List.of();
        }

        IntStream stream = IntStream.range(leftVicinity, candles.size() - rightVicinity);

        List<Candle> rawExtremes = (candles.size() > 10000 ? stream.parallel() : stream)
            .filter(i -> isLocalExtreme(candles, i, leftVicinity, rightVicinity))
            .mapToObj(candles::get)
            .sorted(Comparator.comparingLong(Candle::getIndex)) // гарантируем порядок
            .toList();

        if (rawExtremes.isEmpty()) {
            return List.of();
        }

        List<List<Candle>> groups = groupByProximity(rawExtremes);

        return groups.stream()
            .map(group -> group.stream().max(comparator).orElseThrow())
            .collect(Collectors.toList());
    }

    private List<List<Candle>> groupByProximity(List<Candle> extremes) {
        // Группируем близкие экстремумы
        List<List<Candle>> groups = new ArrayList<>();
        List<Candle> currentGroup = new ArrayList<>();
        currentGroup.add(extremes.get(0));

        for (int i = 1; i < extremes.size(); i++) {
            Candle current = extremes.get(i);
            Candle lastInCurrentGroup = currentGroup.get(currentGroup.size() - 1);

            if (current.getIndex() - lastInCurrentGroup.getIndex() <= extremeArea) {
                // В пределах одной зоны — добавляем в текущую группу
                currentGroup.add(current);
            } else {
                // Начинаем новую группу
                groups.add(new ArrayList<>(currentGroup));
                currentGroup.clear();
                currentGroup.add(current);
            }
        }

        if (!currentGroup.isEmpty()) {
            groups.add(currentGroup);
        }

        return groups;
    }

    /**
     * Whether a candle is the extreme of its vicinity. A plateau - neighbours closing at the same
     * price - yields its first candle only: a neighbour on the left has to be strictly worse, one on
     * the right merely no better.
     * <p>
     * Both candles of a plateau used to count, which reported one turn of the market twice. Grouping
     * hid that on minute bars, but only because the group reaches {@code extremeArea} index units
     * and minute bars sit one unit apart; hourly ones sit dozens apart and the duplicates stood -
     * about a tenth of all minimums on one share.
     * <p>
     * It also made the locator impossible to cache as designed. A cache rescans only past the last
     * extreme it holds, trusting that nothing within that extreme's vicinity can be another one - a
     * neighbour at least as good sits right there. Only a tie broke that, and every extreme a cache
     * lost on real bars was the second candle of such a pair.
     */
    private boolean isLocalExtreme(List<Candle> candles, int index, int left, int right) {
        Candle current = candles.get(index);

        for (int i = index - left; i < index; i++) {
            if (comparator.compare(candles.get(i), current) <= 0) {
                return false;
            }
        }

        for (int i = index + 1; i <= index + right; i++) {
            if (comparator.compare(candles.get(i), current) < 0) {
                return false;
            }
        }

        return true;
    }

    public static PivotPointExtremeLocator ofMinimums(int vicinity) {
        return ofMinimums(vicinity, vicinity);
    }

    public static PivotPointExtremeLocator ofMinimums(int leftVicinity, int rightVicinity) {
        return new PivotPointExtremeLocator(Comparator.comparingDouble(Candle::getCloseAsDouble), leftVicinity, rightVicinity);
    }

    public static PivotPointExtremeLocator ofMinimums() {
        return new PivotPointExtremeLocator(Comparator.comparingDouble(Candle::getCloseAsDouble));
    }

    public static PivotPointExtremeLocator ofMaximums(int vicinity) {
        return ofMaximums(vicinity, vicinity);
    }

    public static PivotPointExtremeLocator ofMaximums(int leftVicinity, int rightVicinity) {
        return new PivotPointExtremeLocator(Comparator.comparingDouble(Candle::getCloseAsDouble).reversed(), leftVicinity, rightVicinity);
    }

    public static PivotPointExtremeLocator ofMaximums() {
        return new PivotPointExtremeLocator(Comparator.comparingDouble(Candle::getCloseAsDouble).reversed());
    }
}
