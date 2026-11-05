package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.math.LinearRegression;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.ExtremePositions;
import com.siberalt.singularity.strategy.extreme.ZigZagExtremeLocator;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.List;

/**
 * Режим по пересечениям цены с кривой, где пересечение считается сменой тренда только тогда, когда его
 * подтверждает наклон: {@link #UP} или {@link #DOWN}.
 *
 * <h2>Что именно гасится</h2>
 * Пересечение само по себе тренд не меняет, потому что цена уходит под кривую двумя разными способами.
 * Либо она проваливается сквозь неё - и тогда это пробой. Либо она стоит на месте, пока кривая медленно
 * всплывает над ней, - и тогда не случилось ничего: пересеклись потому, что средняя догнала. Событие
 * одно и то же, а рынок за ним разный, и различает их наклон.
 * <p>
 * Требуется он <b>решительным</b>: {@code сторона * наклон >= decisive * волатильность}. Одно
 * неравенство покрывает и знак, и величину, и это не экономия записи - пересечение вниз при наклоне
 * вверх противоречиво само по себе, и подтверждать его не следует ни при каком пороге.
 *
 * <h2>Гистерезис - ровно в том, что неподтверждённое пересечение не меняет ничего</h2>
 * Пока подтверждения нет, прежняя сторона остаётся, даже если цена уже стоит по другую сторону кривой.
 * Этим класс и отличается от {@link CrossingRegime}, который сторону цены отдаёт сразу: там задержка
 * назначается окрестностью, здесь - порогом на наклон.
 * <p>
 * Асимметрии двух порогов, как в {@link SteadySide}, тут нет и не нужно: сторону не нужно удерживать
 * отдельным условием, она удерживается тем, что сменить её может только подтверждённое пересечение.
 *
 * <h2>Подтверждение - не событие одного бара</h2>
 * Строго по смыслу «в точке пересечения» проверять наклон следовало бы только на том баре, где сторона
 * сменилась. Здесь сделано иначе: подтверждение проверяется на <b>каждом</b> баре, пока цена стоит не с
 * той стороны, что объявленная сторона. То есть пересечение - это не вспышка, а стоящее разногласие,
 * которое закрывается, когда наклон становится решительным.
 * <p>
 * Так сделано потому, что строгое чтение теряет медленный пробой: цена ушла под кривую на вялом баре,
 * подтверждения не получила, а через три бара посыпалась - и режим остался бы восходящим до тех пор,
 * пока цена не вернётся наверх и не пересечёт кривую снова. Это не гашение шума, а слепота. Цена
 * решения - в другую сторону: затяжное разногласие рано или поздно подтвердится случайным решительным
 * баром, так что порог здесь гасит дребезг, но не удерживает сторону вечно.
 *
 * <h2>По какому ряду считать наклон, и почему это не безразлично</h2>
 * По умолчанию - по самой цене. Два случая, которые надо различить, расходятся именно в ней: при пробое
 * наклон цены крут, при всплывании кривой он около нуля, потому что цена не шла никуда.
 * <p>
 * Наклон <b>кривой</b> подаётся через {@link #setSlopeSeries} и читает другое. Выглядит он более
 * устойчивым - в болтанке вокруг плоской средней наклон средней около нуля, сколько бы цена ни
 * дёргалась, - но у него ровно обратная слабость: средняя отстаёт, и на свежем пробое её наклон ещё
 * мал. То есть она отвергнет как раз то, ради чего всё строилось. Какая из двух слабостей дороже - не
 * измерено, и выбор поэтому вынесен в параметр, а не зашит.
 * <p>
 * Полезно помнить и то, что наклон средней - это не «движение средней», а ход цены за её период:
 * работает тождество {@code sma[t] − sma[t−1] = (p[t] − p[t−N]) / N}. Выбирая кривую, выбирают не
 * другую величину, а другой горизонт той же.
 *
 * <h2>Окно наклона: последние бары или нога от разворота с потолком</h2>
 * По умолчанию наклон проводится по последним {@code slopeBars} приращениям. У такого окна есть
 * произвол выравнивания: если вершина была три бара назад, в окно попадает и подъём к ней, и спуск от
 * неё, и наклон занижается как раз на пробое.
 * <p>
 * {@link #setSlopeAnchor} начинает окно от последнего разворота, но не дальше {@code slopeBars} назад:
 * {@code max(разворот, t − slopeBars)}. Нижняя граница снимает произвол - окно никогда не ложится
 * поперёк разворота. Потолок нужен по другой причине: чувствительность наклона регрессии к последней
 * точке равна {@code 6 / ((n+1)(n+2))}, и на ноге в 30 баров обвал на 3 ATR сдвигает наклон всего на
 * 0.02 ATR за бар - позднее ускорение без потолка размывалось бы длиной ноги. Нога без потолка - это
 * потолок не меньше окна. Короче двух приращений нога не меряется: пересечение ждёт, а не
 * подтверждается по двум точкам.
 * <p>
 * Чего это стоит, надо знать заранее. Развороты подтверждаются откатом и чередуются, поэтому, когда
 * разворот в окне есть, <b>знак наклона решает его тип</b>, а не рынок: от подтверждённой вершины нога
 * идёт вниз по построению, а пока вершина не подтверждена, нога от прежнего минимума идёт в целом
 * вверх, и пересечение вниз отвергается по знаку при любом пороге. То есть порог отката зигзага
 * становится вторым порогом гистерезиса, вместе со своим лагом. Подтверждение превращается в «зигзаг
 * объявил разворот в сторону пересечения, и разворот был быстрым». Однодневные провалы сквозь среднюю
 * это не гасит: такой провал сам подтверждает вершину, и короткая крутая нога проходит порог легко.
 * <p>
 * Развороты ищутся по префиксу окна до проверяемого бара, а не по всему окну разом: разворот,
 * подтверждённый позже, на этом баре ещё не известен, и взять его значило бы заглянуть вперёд. Плата -
 * поиск на каждом баре разногласия, квадрат длины окна в худшем случае.
 *
 * <h2>Шкала</h2>
 * Наклон размерен - рублей за бар, - и сравнивать его с числом напрямую нельзя: двадцать на бумаге по
 * триста это полпроцента, а на бумаге по десять - двести процентов. Он делится на волатильность у
 * <b>того же бара</b>, через {@link VolatilityCalculator#profile}, а не на одно число по всему окну:
 * окно здесь длинное, и судить пересечение годовой давности линейкой сегодняшнего дня незачем.
 * Поэтому {@code decisive} безразмерен и означает «наклон в стольких волатильностях за бар».
 *
 * <h2>Флэта здесь нет</h2>
 * Сторон две, и это осознанно: механизм, который здесь описан, флэта не содержит - у пересечения есть
 * направление, а у его отсутствия нет. Флэт в этой базе и так оказался определяемым не рынком, а
 * критерием: доля флэта вышла 8% по углу средней, 44% по углу цены, 69-80% по амплитуде ног. Заводить
 * здесь четвёртый ответ не на чем. Кому нужен флэт - {@link CrossingRegime} по частоте пересечений или
 * {@link SteadySide} над непрерывной мерой.
 *
 * <h2>Состояния между вызовами нет</h2>
 * Сторона не помнится, а пересчитывается проходом по окну, как в {@link CrossingRegime}, и ответ
 * зависит только от показанных свечей. Плата за это - память стороны ограничена окном: показав мере
 * двести баров, дальше двухсот баров назад её гистерезис не помнит. Первый читаемый бар окна сторону
 * <b>засевает</b> - по той, с которой цена на нём стоит, - поэтому мёртвого участка сверх прогрева
 * самой кривой нет.
 *
 * <h2>Контроль</h2>
 * {@link #naive} - та же разметка без подтверждения вовсе. Сравнивать гистерезис надо с ним: иначе
 * разница между «подтверждение помогает» и «кривая выбрана другая» не разделится.
 */
public class DecisiveCrossing implements MarketCoefficient {
    public static final double UP = TrendZone.UP;

    public static final double DOWN = TrendZone.DOWN;

    private final MarketSeries level;

    private final int slopeBars;

    private final double decisive;

    private PriceExtractor priceExtractor = Candle::close;

    private MarketSeries slopeSeries;

    private VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator();

    private ExtremeLocator slopeAnchor;

    /**
     * @param level     кривая, которую пересекает цена; обычно средняя
     * @param slopeBars по скольким приращениям проводить наклон, а с {@link #setSlopeAnchor} - потолок
     *                  ноги; минимум два, иначе это не регрессия, а одна разность
     * @param decisive  какой наклон подтверждает пересечение, в волатильностях за бар; ноль оставляет
     *                  только требование согласного знака
     */
    public DecisiveCrossing(MarketSeries level, int slopeBars, double decisive) {
        if (level == null) {
            throw new IllegalArgumentException("Нужна кривая, пересечения с которой считать");
        }

        if (slopeBars < 2) {
            throw new IllegalArgumentException(
                "Наклон нужно проводить хотя бы по двум приращениям, получено " + slopeBars);
        }

        if (decisive < 0 || Double.isNaN(decisive)) {
            throw new IllegalArgumentException(
                "Порог подтверждения неотрицателен, получено " + decisive);
        }

        this.level = level;
        this.slopeBars = slopeBars;
        this.decisive = decisive;
    }

    /** Сравнивает с кривой не закрытие, а что-нибудь ещё - типичную цену, скажем. */
    public DecisiveCrossing setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, какую цену сравнивать с кривой");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    /** Считать наклон не по цене, а по другому ряду - по самой кривой, скажем. */
    public DecisiveCrossing setSlopeSeries(MarketSeries slopeSeries) {
        if (slopeSeries == null) {
            throw new IllegalArgumentException("Нужен ряд, по которому считать наклон");
        }

        this.slopeSeries = slopeSeries;

        return this;
    }

    public DecisiveCrossing setVolatilityCalculator(VolatilityCalculator volatilityCalculator) {
        if (volatilityCalculator == null) {
            throw new IllegalArgumentException("Нужно, чем мерить шкалу наклона");
        }

        this.volatilityCalculator = volatilityCalculator;

        return this;
    }

    /**
     * Проводить наклон от последнего разворота, но не дальше {@code slopeBars} назад.
     *
     * @param slopeAnchor чем искать развороты; ожидается {@link ZigZagExtremeLocator} - он причинный, а
     *                    локатор по окрестности на правом краю окна заглядывал бы вперёд
     */
    public DecisiveCrossing setSlopeAnchor(ExtremeLocator slopeAnchor) {
        if (slopeAnchor == null) {
            throw new IllegalArgumentException("Нужно, чем искать развороты");
        }

        this.slopeAnchor = slopeAnchor;

        return this;
    }

    @Override
    public double of(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Double.NaN;
        }

        if (slopeAnchor != null) {
            ExtremePositions.requireIndexed(lastCandles);
        }

        double[] curve = level.of(lastCandles);
        double[] slopes = slopeSeries == null ? pricesOf(lastCandles) : slopeSeries.of(lastCandles);
        double[] volatility = volatilityCalculator.profile(lastCandles);
        int bars = Math.min(curve.length, lastCandles.size());
        double side = 0;

        for (int at = 0; at < bars; at++) {
            if (Double.isNaN(curve[at])) {
                continue;
            }

            double now = Math.signum(priceOf(lastCandles, at) - curve[at]);

            // Цена ровно на кривой - не сторона: ни засеять по ней, ни объявить разногласие нечем.
            if (now == 0) {
                continue;
            }

            if (side == 0 || (now != side && decisiveAt(lastCandles, at, now, slopes, volatility))) {
                side = now;
            }
        }

        return side == 0 ? Double.NaN : side;
    }

    /** Решителен ли наклон на этом баре в сторону пересечения. Нечем проверить - значит нет. */
    private boolean decisiveAt(List<Candle> lastCandles, int at, double side, double[] slopes,
                               double[] volatility) {
        int from = slopeStartOf(lastCandles, at);
        int increments = at - from;

        if (from < 0 || increments < 2 || at >= volatility.length || slopes.length < at + 1) {
            return false;
        }

        double scale = volatility[at];

        if (!(scale > 0)) {
            return false;
        }

        double[] x = new double[increments + 1];
        double[] y = new double[increments + 1];

        for (int step = 0; step <= increments; step++) {
            double value = slopes[from + step];

            if (Double.isNaN(value)) {
                return false;
            }

            x[step] = step;
            y[step] = value;
        }

        return side * new LinearRegression(x, y).getSlope() >= decisive * scale;
    }

    /** Откуда проводить наклон: последние бары или нога от разворота, известного на этом баре. */
    private int slopeStartOf(List<Candle> lastCandles, int at) {
        int capped = at - slopeBars;

        if (slopeAnchor == null) {
            return capped;
        }

        List<Candle> known = lastCandles.subList(0, at + 1);
        int[] pivots = ExtremePositions.of(known, slopeAnchor.locate(known));

        return pivots.length == 0 ? capped : Math.max(pivots[pivots.length - 1], capped);
    }

    private double[] pricesOf(List<Candle> lastCandles) {
        double[] prices = new double[lastCandles.size()];

        for (int at = 0; at < prices.length; at++) {
            prices[at] = priceOf(lastCandles, at);
        }

        return prices;
    }

    private double priceOf(List<Candle> lastCandles, int at) {
        return priceExtractor.extract(lastCandles.get(at)).toDouble();
    }

    /**
     * Та же разметка без подтверждения: сторона, с которой цена стоит на последнем читаемом баре.
     * <p>
     * Это контроль, а не вариант сборки, и живёт он здесь, а не в {@link CrossingRegime}, чтобы
     * сравнение было с тем же самым - той же кривой и той же ценой, - а не с другой конструкцией.
     */
    public static MarketCoefficient naive(MarketSeries level, PriceExtractor priceExtractor) {
        if (level == null || priceExtractor == null) {
            throw new IllegalArgumentException("Нужны кривая и цена, которую с ней сравнивать");
        }

        return candles -> {
            if (candles == null || candles.isEmpty()) {
                return Double.NaN;
            }

            double[] curve = level.of(candles);

            for (int at = Math.min(curve.length, candles.size()) - 1; at >= 0; at--) {
                if (Double.isNaN(curve[at])) {
                    continue;
                }

                double side = Math.signum(
                    priceExtractor.extract(candles.get(at)).toDouble() - curve[at]);

                if (side != 0) {
                    return side;
                }
            }

            return Double.NaN;
        };
    }

    public static MarketCoefficient naive(MarketSeries level) {
        return naive(level, Candle::close);
    }
}
