package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;
import java.util.function.Supplier;

/**
 * Пересоздаёт делегата на событии: на каждом баре сначала спрашивает триггер, и если тот сказал хоть
 * что-то, заменяет делегата свежим из фабрики и отдаёт сигнал триггера. Иначе делегирует.
 * <pre>
 *     new ResettingSignalSource(
 *         new RegimeChangeSignalSource(trend),
 *         () -> new RangeSwitchSignalSource(trend, List.of(
 *             Branch.below(0, new PartialExitSignalSource(slopeChange, -1, 0.9, 0.9, plan, reentry)),
 *             Branch.from(0, new PartialExitSignalSource(slopeChange, 1, 0.9, 0.9, plan, reentry)))))
 * </pre>
 * Нужно это источникам с состоянием, которое принадлежит одному периоду: фиксер, вернувшийся через два
 * тренда, принёс бы старое «снято 0.3» и откупил бы то, чего уже нет. Пересоздаётся всё поддерево сразу, так
 * что сброс не знает, сколько там направлений и как они выбираются.
 * <p>
 * <b>Событие - всё, что не молчание</b> ({@link SignalType#NONE}), в том числе измеренный ноль. На баре
 * события отвечает только триггер: новый делегат начинает со следующего бара и этих свечей не видит, так
 * что закрытие старого и вход нового на один бар не придутся.
 * <p>
 * Что создавать внутри фабрики, а что снаружи, решает, чьё это состояние. Состояние позиции - фиксер,
 * {@link EntryExitSignalSource} - внутри, иначе оно переживёт сброс. Чтение рынка, которому нужна вся лента, -
 * режим через {@link com.siberalt.singularity.strategy.market.ClosedBarsCoefficient}, как {@code trend}
 * выше, - снаружи и общее: созданное заново, оно до первого закрытого дня отвечало бы {@link Double#NaN}.
 * Без состояния, как голая кривизна, можно и так и так.
 *
 * <h2>Состояние</h2>
 * Текущий делегат; первый создаётся в конструкторе.
 */
public class ResettingSignalSource implements SignalSource {
    private final SignalSource trigger;

    private final Supplier<? extends SignalSource> factory;

    private SignalSource delegate;

    /**
     * @param trigger на чём пересоздавать
     * @param factory кого пересоздавать; вызывается сейчас и на каждом событии
     */
    public ResettingSignalSource(SignalSource trigger, Supplier<? extends SignalSource> factory) {
        if (trigger == null || factory == null) {
            throw new IllegalArgumentException("Нужны триггер и фабрика делегата");
        }

        this.trigger = trigger;
        this.factory = factory;
        this.delegate = fresh();
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        Signal event = trigger.calculate(lastCandles);

        if (event.type() == SignalType.NONE) {
            return delegate.calculate(lastCandles);
        }

        delegate = fresh();

        return event;
    }

    private SignalSource fresh() {
        SignalSource made = factory.get();

        if (made == null) {
            throw new IllegalStateException("Фабрика не дала делегата");
        }

        return made;
    }
}
