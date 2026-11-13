package com.siberalt.singularity.strategy.signal;

/**
 * Где позиция в своём плане фиксации: это {@link PartialExitSignalSource} отдаёт {@link TakePlan} и
 * {@link ReentryRule}, а сами они ничего не помнят.
 *
 * @param steps сколько фиксаций у позиции уже было
 * @param taken сколько они сняли вместе, доля всего счёта в [0, 1]
 * @param side  сторона позиции: 1 - лонг, −1 - шорт
 */
public record TakeProgress(int steps, double taken, double side) {
    public TakeProgress {
        if (steps < 0) {
            throw new IllegalArgumentException("Фиксаций не бывает меньше нуля, получено " + steps);
        }

        if (!(taken >= 0) || taken > 1 + 1e-9) {
            throw new IllegalArgumentException("Снятое - доля счёта в [0, 1], получено " + taken);
        }

        if (side != 1 && side != -1) {
            throw new IllegalArgumentException("Сторона позиции - 1 или −1, получено " + side);
        }
    }

    /** Позиция только что открыта: ничего не снято. */
    public static TakeProgress fresh(double side) {
        return new TakeProgress(0, 0, side);
    }

    /** То же после ещё одной фиксации на {@code share}. */
    public TakeProgress after(double share) {
        return new TakeProgress(steps + 1, taken + share, side);
    }
}
