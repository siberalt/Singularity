package investtech.broker.contract.service.market.request;

import investtech.simulation.shared.market.candle.Candle;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public enum CandleInterval {
    UNSPECIFIED(null, 0),
    MIN_1(ChronoUnit.MINUTES, 1),
    MIN_2(ChronoUnit.MINUTES, 2),
    MIN_3(ChronoUnit.MINUTES, 3),
    MIN_5(ChronoUnit.MINUTES, 5),
    MIN_10(ChronoUnit.MINUTES, 10),
    MIN_15(ChronoUnit.MINUTES, 15),
    MIN_30(ChronoUnit.MINUTES, 30),
    HOUR(ChronoUnit.HOURS, 1),
    HOUR_2(ChronoUnit.HOURS, 2),
    HOUR_4(ChronoUnit.HOURS, 4),
    DAY(ChronoUnit.DAYS, 1),
    WEEK(ChronoUnit.WEEKS, 1),
    MONTH(ChronoUnit.MONTHS, 1);

    private final ChronoUnit chronoUnit;
    private final int chronoUnitValue;

    CandleInterval(ChronoUnit chronoUnit, int chronoUnitValue) {
        this.chronoUnit = chronoUnit;
        this.chronoUnitValue = chronoUnitValue;
    }

    public Duration getDuration() {
        if (chronoUnit == ChronoUnit.WEEKS || chronoUnit == ChronoUnit.MONTHS) {
            return chronoUnit.getDuration().multipliedBy(chronoUnitValue);
        }
        return Duration.of(chronoUnitValue, chronoUnit);
    }

    public int getChronoUnitValue() {
        return chronoUnitValue;
    }

    public ChronoUnit getChronoUnit() {
        return chronoUnit;
    }

    public boolean belongsToInterval(Candle startCandle, Candle currentCandle) {
        // If the interval is unspecified, we consider that the current candle belongs to the interval
        if (this == UNSPECIFIED) {
            return true;
        }

        ChronoUnit chronoUnit = this.chronoUnit;
        long chronoUnitValue = this.chronoUnitValue;

        if (this == WEEK || this == MONTH) {
            chronoUnit = ChronoUnit.SECONDS;
            chronoUnitValue = getDuration().toSeconds();
        }

        return chronoUnit.between(startCandle.getTime(), currentCandle.getTime()) < chronoUnitValue;
    }
}
