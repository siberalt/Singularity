package com.siberalt.singularity.strategy.signal;

/**
 * Чем источник, складывающий чужие голоса, решает, о чём получился сигнал.
 * <p>
 * Делегаты тип не подскажут: у трендовых источников он {@link SignalType#UNSPECIFIED} по существу - MACD
 * не знает, в позиции вы или нет. А сливать несогласные типы нечем. Значит тип у слияния - решение того,
 * кто его собрал, и выражается он здесь.
 */
@FunctionalInterface
public interface TypePolicy {
    SignalType of(Votes votes);

    /** Тип не называть - {@link SignalType#UNSPECIFIED} при любом раскладе. Поведение по умолчанию. */
    TypePolicy UNDECIDED = votes -> SignalType.UNSPECIFIED;

    /**
     * Знак как тип: покупка - вход, продажа - выход.
     * <p>
     * <b>Это верно только для книги, которая не шортит</b>, и оговорка не формальная. Знак - это сторона,
     * а «продать» означает либо закрыть лонг, либо открыть шорт, и из знака это неразличимо: ровно для
     * этого {@link SignalType} и появился. В книге, умеющей шорт, такая политика назовёт выходом открытие
     * короткой позиции.
     * <p>
     * Почти все измеренные в этом проекте правила - лонг-онли, поэтому политика полезна; но выбирает её
     * тот, кто знает, что его книга такая, и выбор остаётся видимым в коде, а не спрятанным в дефолте.
     */
    TypePolicy BY_SIDE = votes -> {
        if (votes.confidence() > 0) {
            return SignalType.POSITION_ENTRY;
        }

        return votes.confidence() < 0 ? SignalType.POSITION_EXIT : SignalType.UNSPECIFIED;
    };

    /** Один и тот же тип, каким бы ни был расклад - «этот состав собран, чтобы входить в позицию». */
    static TypePolicy fixed(SignalType type) {
        if (type == null) {
            throw new IllegalArgumentException("Нет типа, который надо ставить");
        }

        return votes -> type;
    }
}
