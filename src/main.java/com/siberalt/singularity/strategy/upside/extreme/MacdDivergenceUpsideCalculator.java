package com.siberalt.singularity.strategy.upside.extreme;

import com.siberalt.singularity.entity.candle.BarSpacing;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.extreme.PivotPointExtremeLocator;
import com.siberalt.singularity.strategy.indicator.Macd;
import com.siberalt.singularity.strategy.upside.Upside;
import com.siberalt.singularity.strategy.upside.UpsideCalculator;
import com.siberalt.singularity.strategy.upside.trend.MacdUpsideCalculator;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Расхождение цены и MACD: цена сделала минимум ниже прошлого, а индикатор - выше прошлого.
 * <p>
 * Сигналом является сторона, как у остальных калькуляторов входа: <b>+1</b> - бычье расхождение (цена
 * продолжила падать, импульс падать перестал), <b>−1</b> - медвежье (новый максимум цены при более низком
 * максимуме индикатора). Величина расхождения отдана в {@code strength}.
 * <p>
 * Экстремумы не ищутся здесь: за ними идут два {@link ExtremeLocator}, как у
 * {@link MaximinUpsideCalculator}. Значит {@link PivotPointExtremeLocator} с его окрестностью и склейкой
 * близких пивотов и {@link com.siberalt.singularity.strategy.extreme.ProminentExtremeLocator} с prominence
 * подставляются снаружи и меряются как варианты, а не зашиты внутрь.
 *
 * <h2>Что в этом сигнале неустранимо</h2>
 * <b>Пивот опознаётся только через правую окрестность баров после себя.</b> До того, как цена развернулась,
 * знать, что минимум был минимумом, нельзя - поэтому вход приходит позже самого минимума на столько баров,
 * сколько требует локатор, и на дневных свечах с окрестностью 5 это неделя. Это свойство идеи, а не
 * реализации, в отличие от мёртвой зоны у нуля, которая оказалась чистой задержкой (см.
 * {@code docs/signals.md}). Цену этой задержки надо мерить развёрткой окрестности, а не принимать на веру.
 * <p>
 * Заглядывания в будущее здесь нет по той же причине: калькулятор видит только переданную ему историю, и
 * пивот, которому не хватает баров справа, локатором просто не возвращается.
 * <p>
 * <b>Свежесть должна быть не меньше правой окрестности локатора.</b> Самый молодой пивот, который вообще
 * может прийти, уже на {@code rightVicinity} баров в прошлом; если требовать свежести строже, не сработает
 * никогда ничего. {@link Builder#pivots} это проверяет, потому что знает окрестность; при локаторах,
 * переданных через {@link Builder#locators}, проверить нечем - это на совести вызывающего.
 *
 * <h2>Решения, принятые здесь, и альтернативы к ним</h2>
 * <ul>
 *   <li><b>Сила - это зазор импульса, нормированный на цену.</b> Она мерит само расхождение, то есть то,
 *   что сигнал утверждает. Альтернатива - глубина просадки цены между пивотами: в правиле на падении
 *   измерено, что для размера позиции глубина работает, а RSI нет, так что как переменную для сайзинга
 *   стоит попробовать и её - но отдельным вариантом, а не молча.</li>
 *   <li><b>Расстояние между пивотами не параметр.</b> Его ограничивает окно, которое передаёт стратегия, и
 *   там ему и место: лишний параметр здесь - лишняя степень свободы в подгонке.</li>
 *   <li><b>Требования «индикатор ниже нуля» нет.</b> Классическая практика ищет расхождение в
 *   перепроданности; это отдельный вариант, и ставится он снаружи - {@link
 *   com.siberalt.singularity.strategy.upside.FilterUpsideCalculator} с условием на сигнал.</li>
 *   <li><b>Если расхождения есть с обеих сторон</b>, побеждает то, чей поздний пивот свежее: сигнал о
 *   развороте - утверждение про последнее, что произошло. Ровно одновременные дают {@link Upside#NEUTRAL}.
 *   </li>
 * </ul>
 *
 * <h2>Чего здесь нет и не будет</h2>
 * Выхода из позиции. Это сигнал входа, а выход - своё решение и свой калькулятор:
 * {@link com.siberalt.singularity.strategy.upside.EntryExitUpsideCalculator} для этого и существует.
 * Измерено, что фильтровать выход входным условием дорого - 51 пункт на RSI, - так что читать этот сигнал
 * наоборот ради закрытия позиции не стоит.
 */
public class MacdDivergenceUpsideCalculator implements UpsideCalculator {
    public static final int DEFAULT_VICINITY = 5;

    private final int fastPeriod;

    private final int slowPeriod;

    private final int signalPeriod;

    private final MacdUpsideCalculator.Source source;

    private final ExtremeLocator lows;

    private final ExtremeLocator highs;

    private final int freshness;

    private MacdDivergenceUpsideCalculator(int fastPeriod, int slowPeriod, int signalPeriod,
                                           MacdUpsideCalculator.Source source,
                                           ExtremeLocator lows, ExtremeLocator highs, int freshness) {
        if (source == null) {
            throw new IllegalArgumentException("Нужно, что считать импульсом: линия или гистограмма");
        }

        if (lows == null || highs == null) {
            throw new IllegalArgumentException("Расхождение не с чем искать без локаторов экстремумов");
        }

        if (freshness < 1) {
            throw new IllegalArgumentException(
                "Свежесть меряется барами и не может быть меньше одного, получено " + freshness);
        }

        // Периоды проверяет сам индикатор - второе место с теми же правилами разойдётся с первым.
        new Macd(fastPeriod, slowPeriod, signalPeriod);

        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
        this.source = source;
        this.lows = lows;
        this.highs = highs;
        this.freshness = freshness;
    }

    /**
     * MACD(12, 26, 9) по линии на пивотах с окрестностью {@value #DEFAULT_VICINITY} и такой же свежестью -
     * пока не сказано иное.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Сборка калькулятора: параметров семь, и половина из них связана друг с другом, так что порядок в
     * конструкторе запоминать было нечем.
     * <p>
     * Связь, из-за которой билдер и нужен: свежесть имеет смысл только рядом с правой окрестностью пивота.
     * Когда окрестность задана через {@link #pivots}, она известна, и {@link #build} проверяет, что
     * свежесть её достаёт. Когда локаторы переданы готовыми через {@link #locators}, окрестность билдеру
     * неизвестна и проверить нечего - тогда за согласованность отвечает вызывающий.
     */
    public static final class Builder {
        private int fastPeriod = Macd.DEFAULT_FAST;

        private int slowPeriod = Macd.DEFAULT_SLOW;

        private int signalPeriod = Macd.DEFAULT_SIGNAL;

        private MacdUpsideCalculator.Source source = MacdUpsideCalculator.Source.LINE;

        private ExtremeLocator lows;

        private ExtremeLocator highs;

        /** Окрестность, которую билдер знает наверняка; {@code null} - локаторы пришли готовыми. */
        private Integer vicinity = DEFAULT_VICINITY;

        /** {@code null} - «такая же, как окрестность»: самый свежий возможный разворот. */
        private Integer freshness;

        private Builder() {
        }

        public Builder macd(int fast, int slow, int signal) {
            this.fastPeriod = fast;
            this.slowPeriod = slow;
            this.signalPeriod = signal;

            return this;
        }

        /** По линии или по гистограмме - то же различие, что у {@link MacdUpsideCalculator}. */
        public Builder source(MacdUpsideCalculator.Source source) {
            this.source = source;

            return this;
        }

        /** Пивоты с одинаковой окрестностью слева и справа - оба локатора сразу. */
        public Builder pivots(int vicinity) {
            this.vicinity = vicinity;
            this.lows = null;
            this.highs = null;

            return this;
        }

        /**
         * Свои локаторы минимумов и максимумов - например
         * {@link com.siberalt.singularity.strategy.extreme.ProminentExtremeLocator} с prominence.
         * <p>
         * Оба сразу, а не по одному: половинчатое состояние, где минимумы ищутся одним способом, а
         * максимумы остались от окрестности по умолчанию, никому не нужно, а поймать его трудно.
         */
        public Builder locators(ExtremeLocator lows, ExtremeLocator highs) {
            this.lows = lows;
            this.highs = highs;
            this.vicinity = null;

            return this;
        }

        /** На сколько баров назад поздний пивот ещё считается свежим. */
        public Builder freshness(int freshness) {
            this.freshness = freshness;

            return this;
        }

        public MacdDivergenceUpsideCalculator build() {
            int age = freshness != null ? freshness
                : vicinity != null ? vicinity : DEFAULT_VICINITY;

            if (vicinity != null && age < vicinity) {
                throw new IllegalArgumentException("Пивот с окрестностью " + vicinity
                    + " не бывает свежее " + vicinity + " баров, а свежести требуется " + age);
            }

            return new MacdDivergenceUpsideCalculator(fastPeriod, slowPeriod, signalPeriod, source,
                vicinity != null ? PivotPointExtremeLocator.ofMinimums(vicinity) : lows,
                vicinity != null ? PivotPointExtremeLocator.ofMaximums(vicinity) : highs,
                age);
        }
    }

    @Override
    public Upside calculate(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.size() < slowPeriod) {
            return Upside.NEUTRAL;
        }

        checkIndexed(lastCandles);

        double[] readings = source == MacdUpsideCalculator.Source.HISTOGRAM
            ? Macd.seriesOf(lastCandles, fastPeriod, slowPeriod, signalPeriod)
            : Macd.lineSeriesOf(lastCandles, fastPeriod, slowPeriod, signalPeriod);
        Divergence bullish = divergenceOf(lastCandles, readings, lows.locate(lastCandles), 1);
        Divergence bearish = divergenceOf(lastCandles, readings, highs.locate(lastCandles), -1);

        if (bullish == null && bearish == null) {
            return Upside.NEUTRAL;
        }

        if (bullish == null) {
            return new Upside(-1, bearish.strength());
        }

        if (bearish == null) {
            return new Upside(1, bullish.strength());
        }

        // С обеих сторон сразу: разворот - утверждение про последнее, что случилось.
        if (bullish.at() == bearish.at()) {
            return Upside.NEUTRAL;
        }

        return bullish.at() > bearish.at()
            ? new Upside(1, bullish.strength())
            : new Upside(-1, bearish.strength());
    }

    /** Найденное расхождение: где стоит его поздний пивот и насколько разошлись цена с импульсом. */
    private record Divergence(int at, double strength) {
    }

    /**
     * Расхождение по двум последним экстремумам одной стороны, или {@code null}, если его нет.
     *
     * @param side +1 для минимумов, −1 для максимумов: знак того направления, о котором сигнал
     */
    private Divergence divergenceOf(List<Candle> lastCandles, double[] readings, List<Candle> pivots,
                                    int side) {
        if (pivots == null || pivots.size() < 2) {
            return null;
        }

        int at = positionOf(lastCandles, pivots.get(pivots.size() - 1));
        int before = positionOf(lastCandles, pivots.get(pivots.size() - 2));

        if (at < 0 || before < 0 || before >= at) {
            return null;
        }

        // Расхождение, о котором уже нельзя торговать, - не сигнал, а история.
        if (lastCandles.size() - 1 - at > freshness) {
            return null;
        }

        double momentum = readings[at];
        double momentumBefore = readings[before];

        if (Double.isNaN(momentum) || Double.isNaN(momentumBefore)) {
            return null;
        }

        double price = lastCandles.get(at).getCloseAsDouble();
        double priceBefore = lastCandles.get(before).getCloseAsDouble();
        double last = lastCandles.getLast().getCloseAsDouble();

        if (last <= 0) {
            return null;
        }

        // Цена ушла против направления сигнала - новый минимум для покупки, новый максимум для продажи, -
        // а импульс пошёл вместе с ним. В этом и расхождение.
        if ((price - priceBefore) * side >= 0 || (momentum - momentumBefore) * side <= 0) {
            return null;
        }

        return new Divergence(at, Math.abs(momentum - momentumBefore) / last);
    }

    /**
     * Позиция свечи в списке - двоичным поиском по её индексу.
     * <p>
     * Вычитанием индексов её не получить, и это не придирка: {@link
     * com.siberalt.singularity.entity.candle.CandleAggregator} отдаёт собранной свече {@code timePoint}
     * первой свечи своего ведра, поэтому у дневных баров, собранных из минутных, индексы расходятся на
     * сотни и неравномерно - короткие сессии и праздники. Делением на средний шаг
     * ({@link BarSpacing}) вышло бы близко, но «близко» здесь означает
     * прочитать индикатор не в том баре и выдумать расхождение, которого нет.
     * <p>
     * Двоичный поиск точен при любом шаге: список свечей упорядочен по времени, а индекс растёт вместе с
     * ним. Ответ всё равно сверяется по ссылке - локаторы возвращают элементы того же списка, так что
     * совпасть должен он же.
     */
    private static int positionOf(List<Candle> lastCandles, Candle pivot) {
        int at = Collections.binarySearch(lastCandles, pivot,
            Comparator.comparingLong(Candle::getIndex));

        return at >= 0 && lastCandles.get(at) == pivot ? at : -1;
    }

    /**
     * Свечи без растущего индекса отвергаются вслух и до того, как их увидит локатор.
     * <p>
     * Проверка стоит здесь, а не в {@link #positionOf}, потому что туда дело не доходит: при одинаковых
     * индексах {@link com.siberalt.singularity.strategy.extreme.ProximityGroupingExtremeLocator} считает
     * все экстремумы одним - расстояние между ними выходит нулевым, - и сигнала не оказывается ещё до
     * всякого поиска позиции. Такой отказ выглядит как «расхождения нет», а означает «считать нечем», и
     * различить их снаружи невозможно. Отсюда и громкость: {@link Candle#DEFAULT_INDEX} в этом
     * калькуляторе - ошибка вызывающего, а не данные.
     */
    private static void checkIndexed(List<Candle> lastCandles) {
        if (lastCandles.getFirst().getIndex() == Candle.DEFAULT_INDEX
            || lastCandles.getLast().getIndex() <= lastCandles.getFirst().getIndex()) {
            throw new IllegalArgumentException(
                "Свечи без растущего индекса: по ним не найти, где стоит экстремум");
        }
    }
}
