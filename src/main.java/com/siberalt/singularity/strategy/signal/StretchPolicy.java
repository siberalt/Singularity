package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremePositions;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;

import java.util.List;

/**
 * Чем {@link SlopeChangeSignalSource} выбирает два отрезка под наклоны.
 * <p>
 * Политика, а не второй класс источника, и это сознательно: сама мера - одна формула,
 * {@code (sl − sw) / (|sl| + |sw|)}, и различаются варианты <b>только</b> тем, откуда берутся отрезки.
 * Две реализации источника расходились бы в остальном, хотя расходиться им не в чем, а третий вариант
 * выбора потребовал бы третьего класса.
 */
@FunctionalInterface
public interface StretchPolicy {
    /**
     * @return отрезки в номерах баров этого окна, или {@code null}, если выбрать их не из чего
     */
    SlopeStretches of(List<Candle> lastCandles);

    /**
     * Отрезки заданной длины, отсчитанные от конца окна: поздний последние {@code laterBars} баров,
     * ранний - {@code earlierBars} перед ним.
     *
     * <h2>Чем это плохо, и чем всё-таки хорошо</h2>
     * У фиксированной нарезки есть произвол выравнивания: граница может сесть поперёк разворота, и
     * тогда половина восходящей ноги попадёт в ранний отрезок, а мера прочитает изменение наклона,
     * которого не было. Измерено, во что это обходится: мера упирается ровно в предел на 48% баров при
     * отрезках 20 и 20 и на 53% при 120 и 120 - то есть примерно половина её чтений означает всего лишь
     * «в окне случился разворот».
     * <p>
     * Зато масштаб задаётся явно и не зависит от того, как рынок ходил: «кривизна за сто баров» - это
     * ровно сто баров. У {@link #betweenPivots} масштаб становится функцией порога.
     *
     * <h2>Разная длина отрезков - это другая мера, а не настройка</h2>
     * Разброс наклона регрессии падает примерно как {@code n³}, поэтому при отрезках 30 и 10 ошибка
     * короткого наклона выше примерно в пять раз, и почти вся изменчивость ответа приходит от него.
     * Мера тогда отвечает не «ход ускорился», а «последние десять баров отличаются от обычного хода».
     */
    static StretchPolicy fixed(int earlierBars, int laterBars) {
        checked(earlierBars, "ранний");
        checked(laterBars, "поздний");

        return candles -> {
            int needed = earlierBars + laterBars;

            if (candles == null || candles.size() < needed) {
                return null;
            }

            int last = candles.size() - 1;

            return new SlopeStretches(last - needed + 1, last - laterBars, last - laterBars + 1, last);
        };
    }

    /** Отрезки равной длины: изменчивость приходит от обоих поровну. */
    static StretchPolicy fixed(int barsPerSlope) {
        return fixed(barsPerSlope, barsPerSlope);
    }


    /**
     * Отрезки между разворотами: ранний от предпоследнего разворота к последнему, поздний от последнего
     * до конца окна.
     *
     * <h2>Якорь снимает произвол выравнивания - и приносит вырождение</h2>
     * Между разворотами отрезок однороден по построению, границе некуда сесть поперёк. Но развороты
     * <b>чередуются</b>, поэтому нога до последнего разворота и нога после него идут в разные стороны
     * почти всегда, а при разных знаках {@code |sl − sw| = |sl| + |sw|} - то есть мера равна ровно ±1.
     * <p>
     * Это не предположение: на ликвидной трети дневными барами ровно предел выходит на <b>99-100%</b>
     * баров при любом пороге. В таком виде мера сообщает знак текущей ноги, то есть меряет направление,
     * которое в этой базе измерено до дна, а не кривизну, ради которой всё строилось.
     * <p>
     * Кривизну в таком якоре дало бы сравнение с предыдущей ногой <i>того же направления</i> - «этот
     * подъём круче прошлого подъёма». Здесь реализованы соседние, как и было задумано сначала; эта
     * запись оставлена затем, чтобы вырождение не открывали заново.
     *
     * @param locator  чем искать развороты; ожидается {@link ZigZagExtremeLocator}, подтверждающий их
     *                 откатом, а не окрестностью - окрестность непричинна
     * @param lastOnly сколько последних разворотов нужно; двух хватает на соседние ноги
     */
    static StretchPolicy betweenPivots(ExtremeLocator locator, int lastOnly) {
        if (locator == null) {
            throw new IllegalArgumentException("Нужно, чем искать развороты");
        }

        if (lastOnly < 2) {
            throw new IllegalArgumentException(
                "Чтобы сравнить две ноги, нужно хотя бы два разворота, получено " + lastOnly);
        }

        return candles -> {
            if (candles == null || candles.isEmpty()) {
                return null;
            }

            ExtremePositions.requireIndexed(candles);

            int[] positions = ExtremePositions.of(candles, locator.locate(candles));

            if (positions.length < 2) {
                return null;
            }

            int last = positions[positions.length - 1];
            int previous = positions[positions.length - 2];

            return previous == last || last == candles.size() - 1 ? null
                : new SlopeStretches(previous, last, last, candles.size() - 1);
        };
    }

    static StretchPolicy betweenPivots(ExtremeLocator locator) {
        return betweenPivots(locator, 2);
    }

    private static void checked(int bars, String which) {
        if (bars < SlopeChangeSignalSource.MIN_SLOPE_BARS) {
            throw new IllegalArgumentException("Наклон нужно проводить хотя бы по "
                + SlopeChangeSignalSource.MIN_SLOPE_BARS + " барам, " + which + " отрезок: " + bars);
        }
    }
}
