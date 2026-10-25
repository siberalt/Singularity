package com.siberalt.singularity.strategy.signal;

/**
 * Два отрезка, по которым {@link SlopeChangeSignalSource} проводит наклоны. Границы - номера баров в
 * том окне, которое дали источнику, обе включительно.
 * <p>
 * Отрезки <b>не обязаны примыкать друг к другу</b>: {@link StretchPolicy#fixed} и
 * {@link StretchPolicy#betweenPivots} дают смежные, а сравнение с предыдущей ногой того же направления
 * - разнесённые, и ради этого случая зазор здесь и разрешён.
 */
public record SlopeStretches(int earlierFrom, int earlierTo, int laterFrom, int laterTo) {
    public SlopeStretches {
        if (earlierFrom < 0 || laterFrom < 0) {
            throw new IllegalArgumentException(
                "Отрезок начинается не раньше нулевого бара, получено "
                    + earlierFrom + " и " + laterFrom);
        }

        if (earlierTo < earlierFrom || laterTo < laterFrom) {
            throw new IllegalArgumentException("Отрезок идёт от бара к бару, получено "
                + earlierFrom + ".." + earlierTo + " и " + laterFrom + ".." + laterTo);
        }

        if (laterFrom < earlierFrom) {
            throw new IllegalArgumentException(
                "Поздний отрезок не может начинаться раньше раннего: " + laterFrom
                    + " перед " + earlierFrom);
        }
    }

    public int earlierBars() {
        return earlierTo - earlierFrom + 1;
    }

    public int laterBars() {
        return laterTo - laterFrom + 1;
    }

    /** Хватает ли обоим отрезков баров, чтобы провести по ним прямую. */
    public boolean longEnough(int minBars) {
        return earlierBars() >= minBars && laterBars() >= minBars;
    }
}
