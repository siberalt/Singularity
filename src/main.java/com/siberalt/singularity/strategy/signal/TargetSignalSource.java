package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Цель позиции как сигнал: каждая смена цели - одна сделка на её разность. Откуда цель берётся, источник
 * не знает; он знает только, как сказать стратегии, сколько докупить или продать.
 *
 * <h2>Сделка, а не цель</h2>
 * Доля сигнала - размер сделки ({@link Signal#share()}), поэтому сигнал звучит один бар, на смене цели, а
 * дальше источник молчит: повтор той же доли докупил бы её ещё раз. Уверенность - сторона сделки: {@code +1},
 * если цель выросла, {@code -1}, если упала. Доля и сила - на сколько она сдвинулась, в долях счёта.
 * Переворот из лонга на весь счёт в шорт на половину - одна продажа на 1.5 счёта.
 * <p>
 * Цена этого - недобор. Если за бар рынок не отдал всю разность, остаток не доберётся: о нём знает счёт,
 * а не сигнал.
 *
 * <h2>Тип - по тому, что стало с позицией</h2>
 * Вход - когда позиции стало больше или она сменила сторону; выход - когда её стало меньше, хоть до нуля,
 * хоть частично. Выход позицию не переворачивает и при расчёте размера: см.
 * {@link com.siberalt.singularity.strategy.impl.quantity.TradeQuantity#namedLots}.
 *
 * <h2>Состояние</h2>
 * Источник помнит прошлую цель; до первого чтения она ноль, то есть деньги. Один экземпляр - одна бумага и
 * один прогон.
 */
public class TargetSignalSource implements SignalSource {
    private final PositionTarget target;

    private double last = 0;

    public TargetSignalSource(PositionTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("Нужно, откуда брать цель");
        }

        this.target = target;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        double next = target.of(lastCandles);

        if (!(next >= -1 && next <= 1)) {
            throw new IllegalArgumentException("Цель - доля счёта в [-1, 1], получено " + next);
        }

        if (next == last) {
            return Signal.NEUTRAL;
        }

        double change = Math.abs(next - last);
        SignalType type = opens(last, next) ? SignalType.POSITION_ENTRY : SignalType.POSITION_EXIT;
        Signal signal = new Signal(type, Math.signum(next - last), change, change);

        last = next;

        return signal;
    }

    private static boolean opens(double before, double after) {
        return Math.abs(after) > Math.abs(before) || after != 0 && Math.signum(after) != Math.signum(before);
    }
}
