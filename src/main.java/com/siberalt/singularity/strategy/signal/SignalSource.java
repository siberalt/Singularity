package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * Откуда берётся сигнал: читает последние свечи и говорит, что с ними делать.
 * <p>
 * Источник, а не калькулятор, и это не придирка к слову. Вычисляют здесь меньше половины реализаций:
 * {@link FilterSignalSource}, {@link WindowSignalSource}, {@link InvertedSignalSource} и
 * {@link EntryExitSignalSource} ничего не считают - они заворачивают другие источники, переиначивая,
 * придерживая или сводя их ответы. «Источник» делает композицию читаемой: фильтр - это источник, который
 * иногда молчит.
 * <p>
 * Молчание выражается {@link Signal#NEUTRAL}, и означает оно «на этом баре сказать нечего», а не «сигнал
 * нулевой силы». Стратегия сравнивает {@link Signal#confidence()} со своими порогами, поэтому источник
 * решает, что считать сигналом, а порог - стоит ли на нём действовать; делить это надвое и значит держать
 * пороги в одном месте, а не в каждом источнике по-своему.
 * <p>
 * Окно решает не источник: сколько свечей ему давать, определяет {@link WindowSignalSource} или стратегия.
 * Источник обязан отвечать по тому, что ему дали, и {@link Signal#NEUTRAL}, если истории не хватило.
 */
public interface SignalSource {
    /**
     * @param lastCandles последние свечи инструмента, старшая первой
     * @return что источник думает об этом баре, или {@link Signal#NEUTRAL}, если сказать нечего
     */
    Signal calculate(List<Candle> lastCandles);
}
