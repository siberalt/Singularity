package com.siberalt.singularity.strategy.volatility;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

public record ATRVolatilityCalculator(int period) implements VolatilityCalculator {

    public ATRVolatilityCalculator() {
        this(14);
    }

    /** Волатильность на конце окна: сглаживание Уайлдера ведёт к ней все предыдущие бары. */
    @Override
    public double calculate(List<Candle> candles) {
        if (candles == null || candles.size() < period + 1) {
            return 0.0;
        }

        IncrementalATR atr = new IncrementalATR(period);

        for (Candle candle : candles) {
            atr.add(candle);
        }

        return atr.value();
    }

    /**
     * Волатильность у каждого бара - та, что была у рынка к его моменту.
     * <p>
     * Смотрит только назад: экстремум и так становится известен лишь через свою окрестность баров, и
     * заглядывать дальше неё ради его же линейки - значит мерить прошлое будущим.
     * <p>
     * Пока не набрался период, мерить нечем; таким барам достаётся первое посчитанное значение -
     * ближайшее из того, что вообще известно. Если окно короче периода, весь профиль нулевой, и
     * вызывающий сам решает, что делать без меры.
     */
    @Override
    public double[] profile(List<Candle> candles) {
        double[] profile = new double[candles == null ? 0 : candles.size()];

        if (profile.length == 0) {
            return profile;
        }

        IncrementalATR atr = new IncrementalATR(period);
        int first = -1;

        for (int at = 0; at < candles.size(); at++) {
            profile[at] = atr.add(candles.get(at));

            if (first < 0 && profile[at] > 0) {
                first = at;
            }
        }

        for (int at = 0; at < first; at++) {
            profile[at] = profile[first];
        }

        return profile;
    }

    public static VolatilityCalculator ofMultiplier(double multiplier, int period) {
        return new MultiplierVolatilityCalculator(new ATRVolatilityCalculator(period), multiplier);
    }

    public static VolatilityCalculator ofMultiplier(double multiplier) {
        return new MultiplierVolatilityCalculator(new ATRVolatilityCalculator(), multiplier);
    }
}
