package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Цена выше уровня. Уровень - любая формула: {@code candles -> Sma.of(candles, 50)}, VWAP, нижняя граница
 * канала.
 * <p>
 * Формула передаётся {@link MarketCoefficient}, потому что он и есть «число, посчитанное по последним
 * свечам», - заводить для уровня отдельный тип было бы тем же контрактом под другим именем. Отсюда и
 * «любая другая формула поддержки» получается без изменений здесь.
 * <p>
 * Ниже уровня - это {@link MarketCondition#negated()}, а не второй класс. Условие и его отрицание часто
 * нужны оба и для разных дел: одно разрешает вход, другое - выход, и написанный дважды уровень однажды
 * разойдётся сам с собой.
 * <p>
 * Ровно на уровне считается «не выше»: граница должна принадлежать одной стороне, иначе цена, севшая на
 * среднюю, окажется одновременно и выше, и ниже её.
 */
public class PriceAbove implements MarketCondition {
    private final MarketCoefficient level;

    private final PriceExtractor priceExtractor;

    /**
     * @param level          чем считать уровень по последним свечам
     * @param priceExtractor что сравнивать с уровнем; по умолчанию закрытие
     */
    public PriceAbove(MarketCoefficient level, PriceExtractor priceExtractor) {
        if (level == null || priceExtractor == null) {
            throw new IllegalArgumentException("Нужны уровень и то, что с ним сравнивать");
        }

        this.level = level;
        this.priceExtractor = priceExtractor;
    }

    public PriceAbove(MarketCoefficient level) {
        this(level, Candle::close);
    }

    @Override
    public boolean holds(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return false;
        }

        double level = this.level.of(lastCandles);

        // Пока уровня нет - средняя не набрала период, - сказать нечего, и «не выше» здесь безопаснее:
        // условие, пропускающее всё, пока не прогрелось, это отсутствие условия на самом нужном участке.
        if (Double.isNaN(level)) {
            return false;
        }

        return priceExtractor.extract(lastCandles.getLast()).toDouble() > level;
    }
}
