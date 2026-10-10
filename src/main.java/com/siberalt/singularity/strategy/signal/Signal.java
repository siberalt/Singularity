package com.siberalt.singularity.strategy.signal;

/**
 * What a {@link SignalSource} makes of the market: о чём сигнал, в какую сторону, насколько уверенно и
 * какую долю счёта просит.
 *
 * @param type       о чём сигнал - см. {@link SignalType}. {@link SignalType#UNSPECIFIED} у источника,
 *                   который знает направление, но не знает, вход это или выход; таких большинство
 * @param confidence которая сторона и насколько уверенно, в [-1, 1]. Знак - сторона: выше нуля покупка,
 *                   ниже продажа. Величина - уверенность; источник, которому про степень сказать нечего,
 *                   отдаёт ±1 и этим ограничивается. Пороги стратегии читаются против ровно этого числа
 * @param strength   величина, которую источник измерил, <b>в его собственных единицах</b> - не вторая
 *                   уверенность. {@code |MACD| / цена} у трендового MACD, угол объёма у
 *                   {@link ChangeAngleSignalSource}, зазор импульса у расхождения. Поэтому порог на неё
 *                   ставится рядом с тем источником, который её произвёл - см.
 *                   {@link com.siberalt.singularity.strategy.signal.condition.Deadband}
 * @param positionBalance какую долю счёта сигнал просит держать, в [0, 1], или
 *                   {@link #UNDEFINED_BALANCE}, если источник этого не говорит. Ноль - встать в деньги
 *
 * @see SignalType о чём бывает сигнал и чего тип пока не делает
 */
public record Signal(SignalType type, double confidence, double strength, double positionBalance) {
    /**
     * Доля счёта не названа - решать её тому, кто считает размер.
     * <p>
     * {@link Double#NaN} здесь - идиома этой базы: так же отвечают {@link
     * com.siberalt.singularity.strategy.indicator.Macd} и {@link
     * com.siberalt.singularity.strategy.indicator.IncrementalRsi}, когда читать нечего. Но NaN тихо
     * протекает в арифметику и даёт NaN-позицию, поэтому спрашивать надо {@link #hasPositionBalance()}, а
     * не сравнивать с константой и не считать по ней не глядя.
     */
    public static final double UNDEFINED_BALANCE = Double.NaN;

    /** Сказать нечего. Не «сигнал нулевой силы», а отсутствие мнения на этом баре. */
    public static final Signal NEUTRAL =
        new Signal(SignalType.NONE, 0, 0, UNDEFINED_BALANCE);

    public Signal {
        if (type == null) {
            throw new IllegalArgumentException("Сигнал без типа: о чём он, знать обязательно");
        }

        // Диапазон confidence здесь не проверяется, и это измеренное решение, а не упущение. Проверка,
        // поставленная на пробу, поймала EwmaVolumeSignalSource с уверенностью до +-2.03 и
        // AdaptiveSignalSource, пропускающий NaN, - то есть настоящие ошибки, из которых первая просит у
        // SignalScaledQuantity двести процентов счёта. Но ужесточение контракта меняет поведение и ломает
        // три тест-класса, которые нарочно подают значения вне диапазона, так что ему место в отдельной
        // правке вместе с починкой источников, а не в переименовании словаря.
        if (!Double.isNaN(positionBalance) && (positionBalance < 0 || positionBalance > 1)) {
            throw new IllegalArgumentException(
                "Доля счёта лежит в [0, 1], получено " + positionBalance);
        }
    }

    /**
     * Сигнал о направлении и уверенности, без мнения о позиции и о размере - форма, которой отвечают почти
     * все источники.
     */
    public Signal(double confidence, double strength) {
        this(SignalType.UNSPECIFIED, confidence, strength, UNDEFINED_BALANCE);
    }

    /** Назвал ли источник долю счёта. Спрашивать это, а не сравнивать с {@link #UNDEFINED_BALANCE}. */
    public boolean hasPositionBalance() {
        return !Double.isNaN(positionBalance);
    }

    /** Тот же сигнал, но о позиции: {@code new Signal(-1, 1).withType(SignalType.STOP_LOSS)}. */
    public Signal withType(SignalType type) {
        return new Signal(type, confidence, strength, positionBalance);
    }

    /** Тот же сигнал с названной долей счёта. */
    public Signal withPositionBalance(double positionBalance) {
        return new Signal(type, confidence, strength, positionBalance);
    }
}
