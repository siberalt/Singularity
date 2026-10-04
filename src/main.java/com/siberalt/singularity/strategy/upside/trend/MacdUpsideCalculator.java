package com.siberalt.singularity.strategy.upside.trend;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.indicator.Macd;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;

import java.util.List;

/**
 * Тренд по MACD: 1 - гистограмма положительна, -1 - отрицательна.
 * <p>
 * Как и у {@link MovingAverageCrossUpsideCalculator}, сигналом является сторона, а не величина: позиция
 * либо есть, либо нет, и стратегия с порогом покупки не должна отказываться входить на свежем пересечении.
 * Величина гистограммы отдана в {@code strength}, нормированная на цену, - чтобы её можно было сравнивать
 * между бумагами разной стоимости.
 * <p>
 * Что именно считать сигналом, у MACD вариантов два, и они не эквивалентны. {@link Source#HISTOGRAM} -
 * линия пересекла свою сигнальную, то есть отрыв быстрой средней от медленной начал расти; срабатывает рано
 * и часто. {@link Source#LINE} - сама линия пересекла ноль, то есть быстрая средняя обогнала медленную; это
 * в точности пересечение двух EMA и потому медленнее. Оба доступны, потому что в этом проекте уже измерено,
 * что скорость сигнала решает: медленный лучше для позиции на месяцы, быстрый - для сделки на дни.
 * <p>
 * Пока индикатор не набрал период, возвращается {@link Upside#NEUTRAL}.
 */
public class MacdUpsideCalculator implements UpsideCalculator {
    /** Что считать сигналом: пересечение линией своей сигнальной или пересечение линией нуля. */
    public enum Source {
        HISTOGRAM,
        LINE
    }

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;
    private final Source source;

    public MacdUpsideCalculator(int fastPeriod, int slowPeriod, int signalPeriod, Source source) {
        if (source == null) {
            throw new IllegalArgumentException("Нужно, что считать сигналом");
        }

        // Остальные проверки делает сам индикатор - дублировать их здесь значит заводить второе место,
        // где написано, какие периоды допустимы.
        new Macd(fastPeriod, slowPeriod, signalPeriod);

        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
        this.source = source;
    }

    public MacdUpsideCalculator(Source source) {
        this(Macd.DEFAULT_FAST, Macd.DEFAULT_SLOW, Macd.DEFAULT_SIGNAL, source);
    }

    /** MACD(12, 26, 9) по гистограмме - то, что имеют в виду под «сигналом MACD». */
    public MacdUpsideCalculator() {
        this(Source.HISTOGRAM);
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < slowPeriod) {
            return Upside.NEUTRAL;
        }

        double[] readings = source == Source.HISTOGRAM
            ? Macd.seriesOf(lastCandles, fastPeriod, slowPeriod, signalPeriod)
            : Macd.lineSeriesOf(lastCandles, fastPeriod, slowPeriod, signalPeriod);
        double now = readings[readings.length - 1];
        double price = lastCandles.getLast().getCloseAsDouble();

        if (Double.isNaN(now) || now == 0 || price <= 0) {
            return Upside.NEUTRAL;
        }

        return new Upside(Math.signum(now), Math.abs(now) / price);
    }
}
