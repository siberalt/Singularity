package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Минимумы и максимумы из двух односторонних локаторов, сведённые в один список по возрастанию бара.
 * <p>
 * Из этого получается зигзаг - второй в пакете, рядом с {@link ZigZagExtremeLocator}, - и отличается
 * он тем, чем вообще считает разворот. Тот ждёт <b>отката на столько-то волатильностей</b>, то есть
 * зависит от того, насколько рынок сейчас широк. Здесь разворотом объявляет вложенный локатор - скажем
 * {@link PivotPointExtremeLocator}, сравнивающий бар с соседями, - и тогда <b>волатильность не участвует
 * вовсе</b>: ни порога в ATR, ни постоянной при корне, одни сравнения.
 * <p>
 * «Волатильно независимый» при этом свойство не класса, а того, что в него положили: с
 * {@link ProminentExtremeLocator} внутри волатильность вернётся. Класс отвечает только за сведение.
 *
 * <h2>Он сливает, а не чередует</h2>
 * Возвращается <b>всё</b>, что нашли вложенные локаторы, по возрастанию индекса свечи - и ничего
 * сверх того. Соседними могут оказаться два максимума подряд, если минимума между ними локатор не
 * нашёл, а на одном баре - и низ, и верх сразу (так бывает на внешнем баре при маленькой
 * окрестности).
 * <p>
 * Схлопывать серии в крайний элемент класс не берётся, хотя соблазн велик: «нога между соседними
 * разворотами» при этом иногда идёт от максимума к максимуму. Но локатора спрашивают, где экстремумы,
 * а не где чередующиеся экстремумы, и выбрасывать найденное вложенными локаторами он не вправе.
 * Прореживание в этом пакете и так живёт отдельно - см. {@link ProximityGroupingExtremeLocator}, - и
 * {@link PivotPointExtremeLocator} применяет его сам, оставляя от близких экстремумов лучший. Повторять
 * это здесь значило бы завести второе место, делающее то же самое. Чередование, если оно кому-то нужно,
 * тоже дело отдельного слоя.
 *
 * <h2>Параметр никуда не делся, он переехал</h2>
 * У {@link ZigZagExtremeLocator} ответ задаёт порог отката, здесь - окрестность вложенного локатора.
 * Длина ноги снизу ограничена этой окрестностью, и короткие ноги никуда не исчезают: в этой базе
 * измерено, что без всякого фильтра отрезок от последнего разворота до конца окна имеет медиану в
 * <b>один бар</b>, и регрессию по нему строить не по чему на 76% баров.
 *
 * <h2>Окрестность непричинна, и это главное отличие</h2>
 * {@link ZigZagExtremeLocator} подтверждает разворот откатом, то есть событием, которое уже случилось,
 * и ни один его ответ не опирается на бары после себя. Локатор, сравнивающий бар с соседями, требует
 * соседей <b>справа</b>: просматривая окно целиком, он просто не назовёт разворотом последние
 * {@code vicinity} баров, и это и есть его задержка - известная заранее, в отличие от отката, где она
 * зависит от данных.
 * <p>
 * Отсюда же предупреждение про память: с общим кэшем хвост окна оказывается подтверждён барами, до
 * которых это окно не дошло, и {@link TrailingMarginExtremeLocator} существует ровно для этого. Здесь
 * он нужен так же, как любому локатору по окрестности, - в отличие от {@link ZigZagExtremeLocator},
 * которому не нужен вовсе.
 */
public class MergingExtremeLocator implements ExtremeLocator {
    private final ExtremeLocator minimums;

    private final ExtremeLocator maximums;

    /**
     * @param minimums чем искать низы
     * @param maximums чем искать верхи
     */
    public MergingExtremeLocator(ExtremeLocator minimums, ExtremeLocator maximums) {
        if (minimums == null || maximums == null) {
            throw new IllegalArgumentException("Нужны локаторы низов и верхов");
        }

        this.minimums = minimums;
        this.maximums = maximums;
    }

    /** Зигзаг по пивотам с одной окрестностью на обе стороны - самая простая сборка без волатильности. */
    public static MergingExtremeLocator ofPivots(int vicinity) {
        return new MergingExtremeLocator(
            PivotPointExtremeLocator.ofMinimums(vicinity),
            PivotPointExtremeLocator.ofMaximums(vicinity));
    }

    @Override
    public List<Candle> locate(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        ExtremePositions.requireIndexed(candles);

        List<Candle> pivots = new ArrayList<>(minimums.locate(candles));

        pivots.addAll(maximums.locate(candles));
        pivots.sort(Comparator.comparingLong(Candle::getIndex));

        return pivots;
    }
}
