package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Оставляет только глубокие экстремумы: яму, из которой цена поднялась хотя бы на заданное число
 * волатильностей, прежде чем нашлась яма глубже.
 * <p>
 * Зачем это рядом с окрестностью пивота. Окрестность отвечает на вопрос «сколько баров рядом выше», а
 * не «насколько выше»: в тихой пиле минимум проходит окрестность в двадцать баров просто потому, что
 * рядом ничего не происходило, хотя глубина ямы - четверть волатильности. Такие экстремумы и есть шум,
 * из которого потом собираются уровни ни о чём. Строже брать окрестность - плохое лекарство: при
 * тридцати барах в каждую сторону пивот становится редкостью, и уровней остаётся втрое меньше, включая
 * настоящие.
 * <p>
 * Мера здесь - топографическая выпуклость. От экстремума идём влево, пока цена не опустится ниже него
 * самого, и запоминаем, насколько высоко она поднималась по пути; то же вправо; выпуклость - меньший из
 * двух подъёмов. Для минимума это «насколько глубока яма относительно окружающего рельефа», и меряется
 * она в волатильностях, поэтому порог переносится между бумагами и таймфреймами, в отличие от
 * окрестности, которую приходится подбирать под интервал.
 * <p>
 * Если с какой-то стороны яма глубже так и не нашлась до края окна, в зачёт идёт подъём, набранный до
 * края. Это осторожнее, чем считать такую сторону бесконечно высокой: свежий минимум у правого края
 * окна ещё не показал, что он глубокий, и заслуживать места среди сильных экстремумов ему рано.
 * <p>
 * Декоратор, а не часть локатора: выпуклость меряется по тем же барам, что уже просмотрел базовый
 * локатор, и одинаково применима к пивотам, кадровым минимумам и чему угодно ещё.
 */
public class ProminentExtremeLocator implements ExtremeLocator {
    public static final double DEFAULT_VOLATILITIES = 1.0;

    private final ExtremeLocator extremeLocator;
    private final VolatilityCalculator volatilityCalculator;
    private final double volatilities;
    private final PriceExtractor level;
    private final PriceExtractor reach;

    /**
     * @param level цена самого экстремума - та, ниже которой ищется яма глубже
     * @param reach цена, до которой рынок дошёл от экстремума, - ею меряется подъём
     */
    protected ProminentExtremeLocator(
        ExtremeLocator extremeLocator,
        VolatilityCalculator volatilityCalculator,
        double volatilities,
        PriceExtractor level,
        PriceExtractor reach
    ) {
        if (extremeLocator == null || volatilityCalculator == null || level == null || reach == null) {
            throw new IllegalArgumentException("Нужны экстремумы, чем мерить их глубину и какие цены читать");
        }

        if (volatilities <= 0) {
            throw new IllegalArgumentException("Порог выпуклости должен быть положительным, получено " + volatilities);
        }

        this.extremeLocator = extremeLocator;
        this.volatilityCalculator = volatilityCalculator;
        this.volatilities = volatilities;
        this.level = level;
        this.reach = reach;
    }

    /** Минимумы базового локатора, из которых цена поднималась хотя бы на столько волатильностей. */
    public static ProminentExtremeLocator ofMinimums(ExtremeLocator minimumLocator, double volatilities) {
        return ofMinimums(minimumLocator, new ATRVolatilityCalculator(14), volatilities);
    }

    public static ProminentExtremeLocator ofMinimums(
        ExtremeLocator minimumLocator,
        VolatilityCalculator volatilityCalculator,
        double volatilities
    ) {
        return new ProminentExtremeLocator(
            minimumLocator, volatilityCalculator, volatilities, Candle::low, Candle::high);
    }

    /** Максимумы, ниже которых цена опускалась хотя бы на столько волатильностей. */
    public static ProminentExtremeLocator ofMaximums(ExtremeLocator maximumLocator, double volatilities) {
        return ofMaximums(maximumLocator, new ATRVolatilityCalculator(14), volatilities);
    }

    /**
     * Максимум - это минимум перевёрнутой цены, поэтому обход остаётся тем же: цены берутся со знаком
     * минус, верх бара читается как уровень, низ - как то, куда рынок дошёл, и «ниже» снова означает
     * «дальше от уровня в сторону рынка».
     */
    public static ProminentExtremeLocator ofMaximums(
        ExtremeLocator maximumLocator,
        VolatilityCalculator volatilityCalculator,
        double volatilities
    ) {
        return new ProminentExtremeLocator(
            maximumLocator,
            volatilityCalculator,
            volatilities,
            candle -> candle.high().multiply(-1),
            candle -> candle.low().multiply(-1)
        );
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        List<Candle> extremes = extremeLocator.locate(candles);

        if (extremes.isEmpty() || candles == null || candles.isEmpty()) {
            return extremes;
        }

        double volatility = volatilityCalculator.calculate(candles);

        if (volatility <= 0) {
            // Мерить нечем - окно короче периода волатильности; фильтровать наугад хуже, чем не
            // фильтровать вовсе.
            return extremes;
        }

        double threshold = volatilities * volatility;
        Map<Long, Integer> positions = new HashMap<>();

        for (int at = 0; at < candles.size(); at++) {
            positions.put(candles.get(at).getIndex(), at);
        }

        List<Candle> prominent = new ArrayList<>();

        for (Candle extreme : extremes) {
            Integer position = positions.get(extreme.getIndex());

            if (position != null && prominenceOf(candles, position) >= threshold) {
                prominent.add(extreme);
            }
        }

        return prominent;
    }

    /**
     * Насколько цена поднялась над ямой с обеих сторон, прежде чем нашлась яма глубже - меньшая из двух
     * величин. Для максимума то же зеркально.
     */
    public double prominenceOf(List<Candle> candles, int position) {
        double level = levelOf(candles.get(position));
        double left = 0;

        for (int at = position - 1; at >= 0; at--) {
            if (levelOf(candles.get(at)) < level) {
                break;
            }

            left = Math.max(left, reachOf(candles.get(at)) - level);
        }

        double right = 0;

        for (int at = position + 1; at < candles.size(); at++) {
            if (levelOf(candles.get(at)) < level) {
                break;
            }

            right = Math.max(right, reachOf(candles.get(at)) - level);
        }

        return Math.min(left, right);
    }

    private double levelOf(Candle candle) {
        return level.extract(candle).toDouble();
    }

    private double reachOf(Candle candle) {
        return reach.extract(candle).toDouble();
    }
}
