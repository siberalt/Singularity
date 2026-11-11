package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.Arrays;
import java.util.List;

/**
 * Доли, названные заранее: каждая следующая фиксация снимает следующий шаг, и после последнего план
 * выполнен. {@code new StepTakePlan(0.1, 0.2, 0.2)} снимает половину счёта за три фиксации. Рынок здесь
 * не читается.
 */
public class StepTakePlan implements TakePlan {
    private final double[] shares;

    /** @param shares доли всего счёта по порядку, каждая в (0, 1], вместе не больше счёта */
    public StepTakePlan(double... shares) {
        if (shares == null || shares.length == 0) {
            throw new IllegalArgumentException("Нужен план: хотя бы одна фиксация");
        }

        for (double share : shares) {
            if (!(share > 0) || share > 1) {
                throw new IllegalArgumentException("Шаг плана - доля счёта в (0, 1], получено " + share);
            }
        }

        if (Arrays.stream(shares).sum() > 1 + 1e-9) {
            throw new IllegalArgumentException("Весь план больше счёта: " + Arrays.toString(shares));
        }

        this.shares = shares.clone();
    }

    @Override
    public double shareOf(int steps, double taken, double side, List<Candle> lastCandles) {
        return shares[steps];
    }

    @Override
    public boolean done(int steps, double taken) {
        return steps >= shares.length;
    }
}
