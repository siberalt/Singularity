package com.siberalt.singularity.strategy.extreme;

import com.siberalt.singularity.entity.candle.BarSpacing;
import com.siberalt.singularity.entity.candle.Candle;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Где в окне стоят свечи, которые вернул {@link ExtremeLocator}.
 * <p>
 * Локаторы отдают <b>свечи</b>, а почти всё, что с ними делают дальше - прочитать индикатор в той же
 * точке, отмерить отрезок, проверить свежесть, - живёт в <b>номерах баров</b>. Перевод между ними
 * нужен всем и одинаково, поэтому он здесь, а не по копии у каждого читателя.
 *
 * <h2>Поиск по индексу свечи, а не вычитанием</h2>
 * Соблазнительное {@code индекс − индекс первой свечи} опирается на то, что бары идут без пропусков
 * и с постоянным шагом, а это неверно, и неверно измеримо:
 * {@link com.siberalt.singularity.entity.candle.CandleAggregator} отдаёт собранной свече
 * {@code timePoint} первой свечи своего ведра, поэтому у дневных баров, собранных из минутных, индексы
 * расходятся на сотни и неравномерно - короткие сессии и праздники. Делением на средний шаг
 * ({@link BarSpacing}) вышло бы близко, но «близко» здесь означает прочитать индикатор не в том баре и
 * выдумать событие, которого нет. Двоичный поиск точен при любом шаге и при любых пропусках: список упорядочен по времени, а
 * индекс растёт вместе с ним.
 * <p>
 * Ответ всё равно сверяется <b>по ссылке</b>: локаторы возвращают элементы того же списка, значит
 * совпасть должен он же, а не другая свеча с тем же индексом.
 */
public final class ExtremePositions {
    private ExtremePositions() {
    }

    /** Номер бара, на котором стоит эта свеча, или {@code -1}, если её в окне нет. */
    public static int of(List<Candle> candles, Candle extreme) {
        int at = Collections.binarySearch(candles, extreme,
            Comparator.comparingLong(Candle::getIndex));

        return at >= 0 && candles.get(at) == extreme ? at : -1;
    }

    /**
     * Номера всех найденных свечей, по порядку. Ненайденные пропускаются молча: локатор мог отдать
     * свечу из другого окна только по ошибке вызывающего, а ошибку громко объявляет
     * {@link #requireIndexed}.
     */
    public static int[] of(List<Candle> candles, List<Candle> extremes) {
        int[] positions = new int[extremes.size()];
        int found = 0;

        for (Candle extreme : extremes) {
            int at = of(candles, extreme);

            if (at >= 0) {
                positions[found++] = at;
            }
        }

        return found == positions.length ? positions : java.util.Arrays.copyOf(positions, found);
    }

    /**
     * Свечи без растущего индекса отвергаются вслух и до того, как их увидит локатор.
     * <p>
     * Проверять внутри поиска поздно: при одинаковых индексах
     * {@link ProximityGroupingExtremeLocator} считает все экстремумы одним - расстояние между ними
     * выходит нулевым, - и события не оказывается ещё до всякого поиска позиции. Такой отказ выглядит
     * как «ничего не нашлось», а означает «считать нечем», и различить их снаружи невозможно. Отсюда и
     * громкость: {@link Candle#DEFAULT_INDEX} у читателя индексов - ошибка вызывающего, а не данные.
     */
    public static void requireIndexed(List<Candle> candles) {
        // Пустое и одиночное окно проходят: индексам там негде расти, и это короткое окно, а не
        // неразмеченные данные. Отличить одно от другого можно только по DEFAULT_INDEX.
        if (candles.isEmpty()) {
            return;
        }

        if (candles.getFirst().getIndex() == Candle.DEFAULT_INDEX
            || (candles.size() > 1
                && candles.getLast().getIndex() <= candles.getFirst().getIndex())) {
            throw new IllegalArgumentException(
                "Свечи без растущего индекса: по ним не найти, где стоит экстремум");
        }
    }
}
