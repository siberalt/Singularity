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
 * @param share      какую долю счёта торгует <b>этот</b> сигнал, в [0, 2], или {@link #UNDEFINED_SHARE},
 *                   если источник этого не говорит. Не цель позиции, а размер сделки: доля стоимости
 *                   всего счёта, а не позиции и не того, что осталось. Поэтому доли складываются -
 *                   фиксации 0.1, 0.2 и 0.2 вместе снимают половину счёта. Сторону сделки говорит
 *                   {@code confidence}, здесь только сколько. Больше единицы - только переворот: закрыть
 *                   лонг на весь счёт и открыть шорт на половину - сделка в полтора счёта
 *
 * @see SignalType о чём бывает сигнал и чего тип пока не делает
 */
public record Signal(SignalType type, double confidence, double strength, double share) {
    /**
     * Доля счёта не названа - решать её тому, кто считает размер.
     * <p>
     * {@link Double#NaN} здесь - идиома этой базы: так же отвечают {@link
     * com.siberalt.singularity.strategy.indicator.Macd} и {@link
     * com.siberalt.singularity.strategy.indicator.IncrementalRsi}, когда читать нечего. Но NaN тихо
     * протекает в арифметику и даёт NaN-позицию, поэтому спрашивать надо {@link #hasShare()}, а
     * не сравнивать с константой и не считать по ней не глядя.
     */
    public static final double UNDEFINED_SHARE = Double.NaN;

    /** Сказать нечего. Не «сигнал нулевой силы», а отсутствие мнения на этом баре. */
    public static final Signal NEUTRAL =
        new Signal(SignalType.NONE, 0, 0, UNDEFINED_SHARE);

    public Signal {
        if (type == null) {
            throw new IllegalArgumentException("Сигнал без типа: о чём он, знать обязательно");
        }

        // Диапазон уверенности - инвариант, а не пожелание, и держится он здесь по одной причине:
        // размер позиции считается умножением на это число. EwmaVolumeSignalSource отдавал до +-2.03, то
        // есть заявку на двести процентов счёта, и обнаружилось это только когда проверку поставили.
        // Наружу такое уходило молча.
        if (Double.isNaN(confidence)) {
            throw new IllegalArgumentException(
                "Уверенность NaN: отсутствие мнения выражается Signal.NEUTRAL, а не NaN");
        }

        if (confidence < -1 || confidence > 1) {
            throw new IllegalArgumentException(
                "Уверенность лежит в [-1, 1], получено " + confidence);
        }

        // Сторона - у уверенности, здесь только размер. Больше двух счетов не бывает и у переворота: закрыть
        // весь лонг и открыть шорт на весь счёт. Плечо - решение счёта, не сигнала.
        if (!Double.isNaN(share) && (share < 0 || share > 2)) {
            throw new IllegalArgumentException("Доля сделки лежит в [0, 2], получено " + share);
        }
    }

    /**
     * Сигнал о направлении и уверенности, без мнения о позиции и о размере - форма, которой отвечают почти
     * все источники.
     */
    public Signal(double confidence, double strength) {
        this(SignalType.UNSPECIFIED, confidence, strength, UNDEFINED_SHARE);
    }

    /** Назвал ли источник долю счёта. Спрашивать это, а не сравнивать с {@link #UNDEFINED_SHARE}. */
    public boolean hasShare() {
        return !Double.isNaN(share);
    }

    /** Тот же сигнал, но о позиции: {@code new Signal(-1, 1).withType(SignalType.STOP_LOSS)}. */
    public Signal withType(SignalType type) {
        return new Signal(type, confidence, strength, share);
    }

    /** Тот же сигнал с названной долей счёта. */
    public Signal withShare(double share) {
        return new Signal(type, confidence, strength, share);
    }
}
