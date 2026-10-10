package com.siberalt.singularity.strategy.signal.condition;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.signal.Signal;
import com.siberalt.singularity.strategy.signal.SignalCondition;
import com.siberalt.singularity.strategy.signal.SignalType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Пропускать только сигналы названных типов.
 * <p>
 * Нужно это там, где позицию держат, но отдельные выходы всё же пускают: «пока цена выше средней не
 * выходим, кроме фиксации прибыли» - это {@code OfType(TAKE_PROFIT)}, поднятое поверх цепочки выходов.
 * Без типов такое правило было невыразимо: выход от стопа и выход от сигнала различались только знаком,
 * то есть не различались.
 * <p>
 * Это условие о сигнале, а не о рынке, поэтому {@link SignalCondition}: тип знает только сам сигнал. Оно
 * спрашивает его всегда - в отличие от условий о рынке, которые делегата не будят.
 * <p>
 * С {@link SignalCondition#or} получается «либо тип подходит, либо рынок в другом состоянии»: именно так
 * собирается «выше средней только фиксация прибыли, ниже - что угодно».
 */
public class OfType implements SignalCondition {
    private final Set<SignalType> allowed;

    public OfType(SignalType... allowed) {
        if (allowed == null || allowed.length == 0) {
            throw new IllegalArgumentException("Не названо ни одного типа, который пропускать");
        }

        Set<SignalType> types = EnumSet.noneOf(SignalType.class);

        for (SignalType type : allowed) {
            if (type == null) {
                throw new IllegalArgumentException("Тип в списке не может быть пустым");
            }

            types.add(type);
        }

        this.allowed = types;
    }

    @Override
    public boolean holds(List<Candle> lastCandles, Supplier<Signal> signal) {
        Signal reading = signal.get();

        return reading != null && allowed.contains(reading.type());
    }
}
