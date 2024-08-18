package investtech.broker.contract.service.market.request;

import investtech.emulation.shared.market.candle.Candle;

import java.time.temporal.ChronoUnit;

public enum CandleInterval {
    UNSPECIFIED(null, 0),
    MIN_1(ChronoUnit.MINUTES, 1),
    MIN_5(ChronoUnit.MINUTES, 5),
    MIN_15(ChronoUnit.MINUTES, 15),
    HOUR(ChronoUnit.HOURS, 1),
    DAY(ChronoUnit.DAYS, 1),
    MIN_2(ChronoUnit.MINUTES, 2),
    MIN_3(ChronoUnit.MINUTES, 3),
    MIN_10(ChronoUnit.MINUTES, 10),
    MIN_30(ChronoUnit.MINUTES, 30),
    HOUR_2(ChronoUnit.HOURS, 2),
    HOUR_4(ChronoUnit.HOURS, 4),
    WEEK(ChronoUnit.WEEKS, 1),
    MONTH(ChronoUnit.MONTHS, 1),;

    private final ChronoUnit chronoUnit;

    private final int chronoUnitValue;

    CandleInterval(ChronoUnit chronoUnit, int chronoUnitValue) {
        this.chronoUnit = chronoUnit;
        this.chronoUnitValue = chronoUnitValue;
    }

    public boolean belongsToInterval(Candle startCandle, Candle currentCandle) {
        return chronoUnit.between(startCandle.getTime(), currentCandle.getTime()) <= chronoUnitValue;
    }

}
