package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.market.MarketCoefficient;

import java.util.List;

/**
 * Закрытие на смене режима: когда знак коэффициента сменился, - {@link SignalType#POSITION_EXIT} против
 * старой стороны на весь счёт; в остальное время - {@link Signal#NEUTRAL}. Триггер для
 * {@link ResettingSignalSource}: смена тренда закрывает позицию и пересоздаёт тех, кто её вёл.
 * <p>
 * Сменой считается и потеря режима: сторона сменилась на «не измерить» ({@link Double#NaN} или ноль) -
 * позиция закрывается, раз основания держать её больше нет. Обратное - не смена: из «не измерить» в сторону
 * закрывать нечего, и первая увиденная сторона запоминается молча.
 * <p>
 * Доля названа - весь счёт, - и это не размер, а гарантия: выход с названной долей позицию не переворачивает
 * и при пустой позиции не торгует ({@link com.siberalt.singularity.strategy.impl.quantity.TradeQuantity#namedLots}).
 * Без неё закрытие шорта, которого не было, в счёте только лонга купило бы весь счёт.
 * <p>
 * Чем это не {@link OnChangeSignalSource}: тот отдаёт новую сторону, а не закрытие старой, и молчание
 * смены не считает - потерю тренда он пропустил бы.
 *
 * <h2>Состояние</h2>
 * Помнит прошлую сторону. Один экземпляр - одна бумага и один прогон.
 */
public class RegimeChangeSignalSource implements SignalSource {
    private final MarketCoefficient regime;

    /** Прошлая сторона; ноль - режима не было. */
    private double side = 0;

    /** @param regime режим со знаком: плюс - вверх, минус - вниз, {@link Double#NaN} - не измерить */
    public RegimeChangeSignalSource(MarketCoefficient regime) {
        if (regime == null) {
            throw new IllegalArgumentException("Нужен режим, смену которого ловить");
        }

        this.regime = regime;
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        double reading = regime.of(lastCandles);
        double now = Double.isNaN(reading) ? 0 : Math.signum(reading);
        double before = side;

        side = now;

        if (before == 0 || now == before) {
            return Signal.NEUTRAL;
        }

        return new Signal(SignalType.POSITION_EXIT, -before, 1, 1);
    }
}
