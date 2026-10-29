package com.siberalt.singularity.strategy.signal;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremePositions;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;

import java.util.List;

/**
 * Показывает делегату не всё окно, а только <b>текущую ногу</b> - бары от последнего подтверждённого
 * разворота до конца. Сам ничего не меряет.
 * <p>
 * Сосед {@link WindowSignalSource}, и разница между ними в том, чем задана граница: тот отмеряет
 * фиксированное число баров, этот ставит начало туда, где цена повернула. Декоратором, а не полем у
 * каждого измерителя, потому что вопрос «по каким барам мерить» общий: так ногу получают и
 * {@link ChangeAngleSignalSource}, и {@link PriceChangeSignalSource}, и RSI - любой источник, не зная
 * про развороты ничего.
 *
 * <h2>Зачем вообще нога</h2>
 * У нарезки по счёту баров есть произвол выравнивания: граница может сесть поперёк разворота, и тогда
 * в окно попадут куски двух разных ног. Замена не косметическая - у угла корреляция якорного чтения с
 * фиксированным вышла <b>0.46</b> по ликвидной трети (0.39…0.52), то есть половина дисперсии не общая,
 * а медиана поднялась с 0.29 до 0.70.
 *
 * <h2>Ловушка, которую надо знать до выставления порога</h2>
 * Нога от разворота <b>односторонняя по построению</b>: локатор режет ряд ровно там, где
 * односторонность кончается. Мера, которой эту односторонность меряют, начинает поэтому частью
 * сообщать о правиле нарезки, а не о рынке. Насколько - решает порог локатора, и это измерено по углу:
 * <pre>
 *     порог 0.5 ATR   медиана 0.86, ровно предел на 27% баров
 *     порог 1.0 ATR   медиана 0.70, ровно предел на 11%
 *     порог 2.0 ATR   медиана 0.51, ровно предел на  3%
 *     фиксированные 20 баров   медиана 0.29, предела нет вовсе
 * </pre>
 * Рабочая точка около 2.0; 0.5 брать нельзя.
 *
 * <h2>Чего делегат лишается</h2>
 * Всего, что было до разворота. Для {@link ChangeAngleSignalSource} это безразлично - он нормирует
 * ход на пройденный путь того же окна и ничего извне не спрашивает. Но источнику, которому нужна
 * <b>опора пошире</b> измеряемого участка, срез вредит: {@link SlopeSignalSource} нормирует наклон
 * средней дельтой остального окна, и после среза этого остального не останется, а
 * {@link PivotSlopeSignalSource} берёт волатильность по всему окну нарочно - на ноге в восемь баров
 * ATR(14) не успевает прогреться. Такие источники ногу ищут у себя, и это не дублирование, а разные
 * требования к контексту.
 */
public class PivotWindowSignalSource implements SignalSource {
    /** Меньше трёх баров - не нога: по двум точкам мерить нечего почти ничем. */
    public static final int MIN_LEG_BARS = 3;

    private final SignalSource delegate;

    private final ExtremeLocator pivots;

    private final int minLegBars;

    /**
     * @param delegate   кому показывать ногу
     * @param pivots     чем искать разворот; у {@link ZigZagExtremeLocator} подтверждение стоит
     *                   медианы в 1-7 баров, у локатора по окрестности - ровно окрестность
     * @param minLegBars сколько баров нужно ноге, чтобы её вообще показывать
     */
    public PivotWindowSignalSource(SignalSource delegate, ExtremeLocator pivots, int minLegBars) {
        if (delegate == null || pivots == null) {
            throw new IllegalArgumentException("Нужны делегат и чем искать разворот");
        }

        if (minLegBars < MIN_LEG_BARS) {
            throw new IllegalArgumentException(
                "Ноге нужно хотя бы " + MIN_LEG_BARS + " бара, получено " + minLegBars);
        }

        this.delegate = delegate;
        this.pivots = pivots;
        this.minLegBars = minLegBars;
    }

    public PivotWindowSignalSource(SignalSource delegate, ExtremeLocator pivots) {
        this(delegate, pivots, MIN_LEG_BARS);
    }

    /** Разворот по откату на столько волатильностей - подтверждение дешевле, чем у окрестности. */
    public PivotWindowSignalSource(SignalSource delegate, double volatilities) {
        this(delegate, new ZigZagExtremeLocator(volatilities));
    }

    @Override
    public Signal calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < minLegBars) {
            return Signal.NEUTRAL;
        }

        ExtremePositions.requireIndexed(lastCandles);

        int[] found = ExtremePositions.of(lastCandles, pivots.locate(lastCandles));

        if (found.length == 0) {
            return Signal.NEUTRAL;
        }

        int from = found[found.length - 1];

        // Нога короче заказанного - не повод показать делегату что-нибудь другое: вернуть ему
        // фиксированное окно значило бы молча подменить то, о чём его спрашивают.
        if (lastCandles.size() - from < minLegBars) {
            return Signal.NEUTRAL;
        }

        return delegate.calculate(lastCandles.subList(from, lastCandles.size()));
    }
}
