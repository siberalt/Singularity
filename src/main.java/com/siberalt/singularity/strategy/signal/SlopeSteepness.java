package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.List;

/**
 * Насколько круты отрезки, на которых {@link SlopeChangeSignalSource} меряет кривизну: наибольший из двух
 * наклонов в волатильностях за бар.
 * <pre>
 *     крутизна = max(|sw|, |sl|) / волатильность
 * </pre>
 * Отрезки - те же, что у источника: его {@link StretchPolicy}, его цена. Кривизна однородна нулевой степени и
 * масштаба наклонов не видит вовсе - разворот после резкого обвала и разворот после пологого сползания дают
 * ей одно число. Здесь этот масштаб и возвращается.
 * <p>
 * {@link Double#NaN} - мерить нечего: источник молчит на этих свечах или волатильность не положительна.
 *
 * @see com.siberalt.singularity.strategy.signal.condition.SteepnessGate порог на неё
 */
public class SlopeSteepness implements MarketCoefficient {
    private final SlopeChangeSignalSource source;

    private final VolatilityCalculator volatility;

    /**
     * @param source     чьи отрезки мерить
     * @param volatility в чём мерить: волатильность по тем же свечам, в цене за бар
     */
    public SlopeSteepness(SlopeChangeSignalSource source, VolatilityCalculator volatility) {
        if (source == null || volatility == null) {
            throw new IllegalArgumentException("Нужны источник с отрезками и мера волатильности");
        }

        this.source = source;
        this.volatility = volatility;
    }

    /** В ATR(14) по тем же свечам. */
    public SlopeSteepness(SlopeChangeSignalSource source) {
        this(source, new ATRVolatilityCalculator());
    }

    @Override
    public double of(List<Candle> lastCandles) {
        SlopeChangeSignalSource.Slopes slopes = source.slopesOf(lastCandles);

        if (slopes == null) {
            return Double.NaN;
        }

        double scale = volatility.calculate(lastCandles);

        return scale > 0 ? Math.max(Math.abs(slopes.earlier()), Math.abs(slopes.later())) / scale : Double.NaN;
    }
}
