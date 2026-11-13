package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.Arrays;
import java.util.List;

/**
 * Режим по расстоянию цены от кривой в волатильностях: {@link #UP}, когда цена ушла над кривой дальше
 * полосы, {@link #DOWN}, когда под неё, а внутри полосы - прежняя сторона.
 *
 * <h2>Чем это лучше пересечения с подтверждением</h2>
 * {@link DecisiveCrossing} меняет сторону на событии - пересечении, - и если событие не подтвердилось,
 * прежняя сторона может держаться сколько угодно: медленный рост над средней не набирает решительного
 * наклона никогда. Здесь залипание <b>ограничено</b>: дальше {@code width} волатильностей от кривой
 * сторона определена однозначно, без всяких условий.
 * <p>
 * И дрейф кривой отсекается сам, без наклона. Пока цена стоит, средняя подходит к ней монотонно и не
 * перехлёстывает - её окно заполняется той же ценой, - так что расстояние стремится к нулю с той же
 * стороны и до противоположного края полосы не доходит. «Средняя всплыла над стоящей ценой» полосу не
 * пересекает по построению.
 *
 * <h2>Цена полосы</h2>
 * Каждое переключение запаздывает на ход в {@code width} волатильностей от кривой, каждый ложный
 * пробой стоит около двух ширин. Это то же обменное отношение лага на дребезг, что у любого
 * гистерезиса, но с явной и постоянной ценой. Шкала в волатильностях переносится между бумагами: по
 * ликвидной трети дневными барами медиана {@code |цена − SMA(80)| / ATR(14)} вышла 2.48, а у отдельных
 * бумаг от 1.88 до 2.83.
 *
 * <h2>Сужение по наклону</h2>
 * {@link #setSlopeNarrowing} делает края подвижными: край в сторону выхода сужается на {@code τ·v}, где
 * {@code v} - наклон регрессии цены за последние приращения в волатильностях за бар. Условие
 * {@code d > width − τ·v} - то же, что {@code d + τ·v > width}: «расстояние, на котором цена окажется через
 * τ баров, если сохранит скорость». Резкий ход переключает раньше, медленный - как прежде.
 * <p>
 * Края зажаты в {@code [minWidth, width]}. Сверху - чтобы граница залипания осталась прежней: ход против
 * выхода полосу не расширяет. Снизу - чтобы однодневная шпилька не схлопывала полосу в сторону цены.
 * Наклон берётся у цены, а не у расстояния: производная расстояния смешивает «пошла цена» и
 * «поползла средняя», а дрейф средней полоса и так отсекает.
 * <p>
 * На стенде дневными барами по ликвидной трети (SMA(80), ATR(14), ширина 2.5, края от 1, наклон и
 * горизонт по 5 баров) сужение обошло статическую полосу ширины 1 на +7.6 ± 4.3 пп при издержках, с плюсом
 * во всех четырёх периодах, но правило «больше двух ошибок» не прошло. Статические ширины 1 и 2.5 между
 * собой неразличимы, так что разницу вносит именно наклон, а не узость.
 *
 * <h2>Отбитая средняя</h2>
 * {@link #setReclaim} схлопывает край в сторону выхода до самой кривой, когда наклон туда не меньше
 * {@code decisive}: цена отбила среднюю рывком - и сторона меняется сразу, едва цена её перешла. Ровно на
 * кривой - ещё не перешла. Дальше сторона держится как любая другая: до противоположного края, а не до
 * возврата к средней.
 * <p>
 * Одного положения цены для этого мало. Правило «режим вниз, а цена над средней - значит вверх» вместе
 * со своим зеркалом и есть сторона цены, полоса нулевой ширины: на Сбере 78 зон вместо 9. Наклон отличает
 * «цена пришла к средней» от «средняя сползла к стоящей цене» - дрейф средней наклона цены не создаёт.
 * <p>
 * В плоскости «расстояние × наклон» это та же область выхода, что у сужения, с другим краем: при
 * {@code decisive = (width − minWidth) / horizon} край линейно сужается до {@code minWidth} и на этом
 * наклоне падает до нуля. При {@code decisive = 0} почти любой возврат за кривую меняет сторону, и полоса
 * близка к стороне цены.
 *
 * <h2>Контроль - та же сборка</h2>
 * При {@code width == 0} полоса схлопывается, и ответ - сторона, с которой цена стоит, ровно как у
 * {@link DecisiveCrossing#naive}. Сравнивать полосу надо с ней самой в нулевой ширине; сужение - с
 * нулевым горизонтом, при котором оно совпадает со статической полосой.
 *
 * <h2>Состояния между вызовами нет</h2>
 * Сторона пересчитывается проходом по окну, как в {@link CrossingRegime}. Первый читаемый бар её
 * засевает по знаку расстояния, поэтому мёртвого участка сверх прогрева кривой нет; если за всё окно
 * цена ни разу не вышла из полосы, ответ - этот знак, то есть память ограничена окном.
 * <p>
 * Волатильность берётся у каждого бара через {@link VolatilityCalculator#profile}, а не одна на окно:
 * окно длинное, и мерить расстояние годовой давности сегодняшним ATR незачем.
 */
public class DistanceBand implements MarketCoefficient {
    public static final double UP = TrendZone.UP;

    public static final double DOWN = TrendZone.DOWN;

    private final MarketSeries level;

    private final double width;

    private PriceExtractor priceExtractor = Candle::close;

    private VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator();

    /** Ноль - края неподвижны. */
    private int slopeBars;

    private double horizon;

    private double minWidth;

    /** Бесконечность - отмены нет. */
    private double decisive = Double.POSITIVE_INFINITY;

    /**
     * Края полосы у каждого бара, оба положительные: {@code up} - насколько расстояние должно превысить
     * ноль, чтобы сторона стала {@link #UP}, {@code down} - насколько уйти под ноль для {@link #DOWN}.
     */
    public record Edges(double[] up, double[] down) {
    }

    /**
     * @param level кривая, расстояние до которой меряется; обычно средняя
     * @param width полуширина полосы в волатильностях; ноль даёт сторону цены без гистерезиса
     */
    public DistanceBand(MarketSeries level, double width) {
        if (level == null) {
            throw new IllegalArgumentException("Нужна кривая, расстояние до которой мерить");
        }

        if (width < 0 || Double.isNaN(width)) {
            throw new IllegalArgumentException("Ширина полосы неотрицательна, получено " + width);
        }

        this.level = level;
        this.width = width;
    }

    /** Мерит от кривой не закрытие, а что-нибудь ещё - типичную цену, скажем. */
    public DistanceBand setPriceExtractor(PriceExtractor priceExtractor) {
        if (priceExtractor == null) {
            throw new IllegalArgumentException("Нужно, какую цену сравнивать с кривой");
        }

        this.priceExtractor = priceExtractor;

        return this;
    }

    public DistanceBand setVolatilityCalculator(VolatilityCalculator volatilityCalculator) {
        if (volatilityCalculator == null) {
            throw new IllegalArgumentException("Нужно, чем мерить шкалу расстояния");
        }

        this.volatilityCalculator = volatilityCalculator;

        return this;
    }

    /**
     * Сужает край в сторону выхода на {@code horizon · v}, где {@code v} - наклон регрессии цены за
     * {@code slopeBars} приращений в волатильностях за бар; края не уже {@code minWidth} и не шире
     * {@code width}. Нулевой горизонт - статическая полоса.
     */
    public DistanceBand setSlopeNarrowing(int slopeBars, double horizon, double minWidth) {
        if (slopeBars < 1) {
            throw new IllegalArgumentException("Наклон нужен хотя бы по одному приращению, получено " + slopeBars);
        }

        if (horizon < 0 || Double.isNaN(horizon)) {
            throw new IllegalArgumentException("Горизонт проекции неотрицателен, получено " + horizon);
        }

        if (minWidth < 0 || minWidth > width || Double.isNaN(minWidth)) {
            throw new IllegalArgumentException(
                "Нижний край между нулём и шириной %s, получено %s".formatted(width, minWidth));
        }

        this.slopeBars = slopeBars;
        this.horizon = horizon;
        this.minWidth = minWidth;

        return this;
    }

    /**
     * Схлопывает край в сторону выхода до кривой, когда наклон туда не меньше {@code decisive}
     * волатильностей за бар. Наклон тот же, что у {@link #setSlopeNarrowing}, поэтому сужение задаётся
     * раньше; без проекции - нулевым горизонтом.
     */
    public DistanceBand setReclaim(double decisive) {
        if (slopeBars == 0) {
            throw new IllegalStateException("Отмене нужен наклон: сначала setSlopeNarrowing");
        }

        if (decisive < 0 || Double.isNaN(decisive)) {
            throw new IllegalArgumentException("Порог наклона неотрицателен, получено " + decisive);
        }

        this.decisive = decisive;

        return this;
    }

    @Override
    public double of(List<Candle> lastCandles) {
        if (lastCandles == null || lastCandles.isEmpty()) {
            return Double.NaN;
        }

        double[] volatility = volatilityCalculator.profile(lastCandles);
        double[] distances = distancesOf(lastCandles, volatility);
        Edges edges = edgesOf(lastCandles, volatility);
        double side = 0;

        for (int at = 0; at < distances.length; at++) {
            double distance = distances[at];

            if (Double.isNaN(distance)) {
                continue;
            }

            if (distance > edges.up()[at]) {
                side = UP;
            } else if (distance < -edges.down()[at]) {
                side = DOWN;
            } else if (side == 0) {
                // Засев по знаку: внутри полосы прежней стороны ещё нет, а ровно на кривой нет и знака.
                side = Math.signum(distance);
            }
        }

        return side == 0 ? Double.NaN : side;
    }

    /**
     * Расстояние цены от кривой в волатильностях у каждого бара окна; {@link Double#NaN}, где мерить
     * нечем - кривая не прогрелась или волатильность не положительна.
     */
    public double[] distancesOf(List<Candle> lastCandles) {
        return distancesOf(lastCandles, volatilityCalculator.profile(lastCandles));
    }

    /**
     * Расстояние последнего бара окна - мера для того, кто читает расстояние по одному бару, например
     * {@link com.siberalt.singularity.strategy.signal.DistanceTakePlan} и
     * {@link com.siberalt.singularity.strategy.signal.DistanceReentry} через {@link ClosedBarsCoefficient}.
     * {@link Double#NaN}, если окна нет или мерить нечем.
     */
    public double lastDistanceOf(List<Candle> lastCandles) {
        double[] distances = distancesOf(lastCandles);

        return distances.length == 0 ? Double.NaN : distances[distances.length - 1];
    }

    /**
     * Края полосы у каждого бара. Без сужения оба равны ширине; с сужением там, где наклон ещё не
     * измерить, - тоже.
     */
    public Edges edgesOf(List<Candle> lastCandles) {
        return edgesOf(lastCandles, volatilityCalculator.profile(lastCandles));
    }

    private double[] distancesOf(List<Candle> lastCandles, double[] volatility) {
        double[] curve = level.of(lastCandles);
        double[] distances = new double[lastCandles.size()];

        for (int at = 0; at < distances.length; at++) {
            boolean measurable = at < curve.length && at < volatility.length
                && !Double.isNaN(curve[at]) && volatility[at] > 0;

            distances[at] = measurable
                ? (priceOf(lastCandles.get(at)) - curve[at]) / volatility[at]
                : Double.NaN;
        }

        return distances;
    }

    private Edges edgesOf(List<Candle> lastCandles, double[] volatility) {
        double[] up = new double[lastCandles.size()];
        double[] down = new double[lastCandles.size()];

        Arrays.fill(up, width);
        Arrays.fill(down, width);

        if (slopeBars == 0 || horizon == 0 && Double.isInfinite(decisive)) {
            return new Edges(up, down);
        }

        for (int at = slopeBars; at < up.length && at < volatility.length; at++) {
            double slope = PriceSlope.of(
                lastCandles.subList(at - slopeBars, at + 1),
                priceExtractor,
                volatility[at]
            );

            if (Double.isNaN(slope)) {
                continue;
            }

            double shift = horizon * slope;

            up[at] = slope >= decisive ? 0 : clamp(width - shift);
            down[at] = -slope >= decisive ? 0 : clamp(width + shift);
        }

        return new Edges(up, down);
    }

    private double clamp(double edge) {
        return Math.clamp(edge, minWidth, width);
    }

    private double priceOf(Candle candle) {
        return priceExtractor.extract(candle).toDouble();
    }
}
