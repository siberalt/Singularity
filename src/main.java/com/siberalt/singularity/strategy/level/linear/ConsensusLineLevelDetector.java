package com.siberalt.singularity.strategy.level.linear;

import com.siberalt.singularity.entity.candle.BarSpacing;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.math.ArithmeticOperations;
import com.siberalt.singularity.math.LinearFunction2D;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.level.LevelDetector;
import com.siberalt.singularity.strategy.level.strength.SimpleStrengthCalculator;
import com.siberalt.singularity.strategy.level.strength.StrengthCalculator;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Наклонный уровень как прямая, вокруг которой собралось больше всего экстремумов окна.
 * <p>
 * Зачем он нужен рядом с {@link LinearLevelDetector}: тот работает инкрементно. Первые два экстремума
 * задают направление прямой, а каждый следующий только проверяется на попадание в допуск, и если не
 * попал - уровень обрывается и начинается новый. Значит направление уровня решают две точки, выбранные
 * не за то, что они лучшие, а за то, что они первые: два выброса подряд - и прямая уходит в сторону,
 * а все настоящие касания оказываются вне допуска. Полного пересчёта по окну там нет вовсе.
 * <p>
 * Здесь пересчёт полный, как у {@link StatelessClusterLevelDetector}: ничего не переносится между
 * вызовами, и прямая выбирается по всем экстремумам сразу. Через каждую пару экстремумов проводится
 * прямая, у неё считаются согласные - экстремумы в пределах допуска по вертикали, - затем по одним
 * согласным прямая пересчитывается методом наименьших квадратов и согласные пересчитываются ещё раз.
 * Побеждает прямая с наибольшим числом согласных; её точки изымаются, и поиск повторяется на
 * оставшихся, пока согласных хватает на уровень. Перебор пар - это {@code O(n^2)} по числу экстремумов,
 * которых в окне десятки, а не тысячи, и он полный: результат не зависит от случайных выборок, в
 * отличие от RANSAC.
 * <p>
 * Допуск задаётся в волатильностях, а не в доле цены - см. {@link #setVolatilityTolerance}: сколько
 * стоит промах, решает рынок, а не цена бумаги.
 */
public class ConsensusLineLevelDetector implements LevelDetector {
    public static final int DEFAULT_MIN_POINTS = 3;
    public static final double DEFAULT_VOLATILITIES = 1.0;
    public static final int DEFAULT_MAX_LEVELS = 10;

    /** Насколько ниже огибающей точка всё ещё считается лежащей на ней: запас на округление. */
    private static final double TOUCHING = 1e-9;

    private final ExtremeLocator extremeLocator;
    private PriceExtractor priceExtractor = Candle::low;
    private VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator(14);
    private StrengthCalculator strengthCalculator = new SimpleStrengthCalculator();
    private double volatilities = DEFAULT_VOLATILITIES;
    private int minPoints = DEFAULT_MIN_POINTS;
    private int maxLevels = DEFAULT_MAX_LEVELS;
    private boolean envelope;
    private long freshTouchWithin;

    public ConsensusLineLevelDetector(ExtremeLocator extremeLocator) {
        this.extremeLocator = Objects.requireNonNull(extremeLocator);
    }

    /** Какую цену экстремума считать точкой уровня: для поддержки - низ бара, для сопротивления - верх. */
    public ConsensusLineLevelDetector setPriceExtractor(PriceExtractor priceExtractor) {
        this.priceExtractor = Objects.requireNonNull(priceExtractor);
        return this;
    }

    /**
     * Насколько далеко от прямой может лежать экстремум, чтобы считаться её касанием - в волатильностях
     * окна.
     */
    public ConsensusLineLevelDetector setVolatilityTolerance(VolatilityCalculator volatilityCalculator, double volatilities) {
        if (volatilities <= 0) {
            throw new IllegalArgumentException("Допуск должен быть положительным, получено " + volatilities);
        }

        this.volatilityCalculator = Objects.requireNonNull(volatilityCalculator);
        this.volatilities = volatilities;
        return this;
    }

    /** Сколько согласных экстремумов делают прямую уровнем. Меньше трёх - это просто прямая через точки. */
    public ConsensusLineLevelDetector setMinPoints(int minPoints) {
        if (minPoints < 2) {
            throw new IllegalArgumentException("Уровень нужно подтвердить хотя бы двумя точками, получено " + minPoints);
        }

        this.minPoints = minPoints;
        return this;
    }

    public ConsensusLineLevelDetector setMaxLevels(int maxLevels) {
        if (maxLevels < 1) {
            throw new IllegalArgumentException("Уровней должно быть хотя бы один, получено " + maxLevels);
        }

        this.maxLevels = maxLevels;
        return this;
    }

    public ConsensusLineLevelDetector setStrengthCalculator(StrengthCalculator strengthCalculator) {
        this.strengthCalculator = Objects.requireNonNull(strengthCalculator);
        return this;
    }

    /**
     * Держать уровень под своими точками, а не посередине них.
     * <p>
     * Прямая наименьших квадратов проходит через середину облака экстремумов: примерно половина
     * минимумов оказывается под ней, и это уже не поддержка, а ось симметрии низов. Направление такой
     * прямой задаёт то, какое подмножество лоёв многочисленнее, поэтому на растущем ряду она может
     * идти вниз - что и было видно на графике: три линии из шести шли против цены.
     * <p>
     * С огибающей прямая после подгонки опускается параллельно себе до самого низкого своего минимума,
     * и согласными считаются только те, кто лежит в допуске над ней. Уровень получает опору снизу, и
     * против растущих лоёв он идти уже не может - справа ему не на что опереться.
     * <p>
     * Выключено по умолчанию: это другой смысл уровня, и включать его задним числом для уже сделанных
     * замеров нельзя.
     */
    public ConsensusLineLevelDetector setEnvelope(boolean envelope) {
        this.envelope = envelope;
        return this;
    }

    /**
     * Требовать, чтобы уровень опирался на свежую точку: его последнее касание не старше столька баров
     * от конца окна. Ноль - не требовать ничего, как и было.
     * <p>
     * Зачем. Наклон прямой задаёт то подмножество экстремумов, которое её подтвердило, и подмножество
     * может целиком лежать в прошлом окна: линия честно проходит под старыми лоями и при этом идёт вниз,
     * пока цена растёт - на графике таких было три из шести. Это не ошибка подгонки, это уровень,
     * говорящий о прошлом. Уровень, обязанный держаться за недавний лой, через растущие лои вниз идти не
     * может: справа ему не на что опереться.
     * <p>
     * Срок считается в барах окна, а не в индексах: часовой бар несёт индекс минуты, так что сто баров -
     * это шесть тысяч единиц индекса, и путать их значит не фильтровать вовсе.
     */
    public ConsensusLineLevelDetector setFreshTouchWithin(long bars) {
        if (bars < 0) {
            throw new IllegalArgumentException("Срок свежести не может быть отрицательным, получено " + bars);
        }

        this.freshTouchWithin = bars;
        return this;
    }

    @Override
    public List<Level<Double>> detect(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        List<Candle> extremes = new ArrayList<>(extremeLocator.locate(candles));

        if (extremes.size() < minPoints) {
            return List.of();
        }

        Tolerances tolerance = tolerancesOf(candles);
        long freshFrom = freshFrom(candles);
        List<Level<Double>> levels = new ArrayList<>();

        while (levels.size() < maxLevels && extremes.size() >= minPoints) {
            Line best = bestLine(extremes, tolerance, freshFrom);

            if (best == null) {
                break;
            }

            levels.add(levelOf(best, candles));
            extremes.removeAll(best.points);
        }

        levels.sort(Comparator.comparingDouble(Level<Double>::strength).reversed());

        return List.copyOf(levels);
    }

    /** Прямая, у которой в допуске оказалось больше всего экстремумов; при равенстве - та, что шире. */
    protected Line bestLine(List<Candle> extremes, Tolerances tolerance, long freshFrom) {
        Line best = null;

        for (int first = 0; first < extremes.size(); first++) {
            for (int second = first + 1; second < extremes.size(); second++) {
                Line candidate = consensusAround(extremes, extremes.get(first), extremes.get(second), tolerance);

                if (candidate == null || candidate.points.size() < minPoints) {
                    continue;
                }

                // Уровень, чьё последнее касание слишком старое, уступает место другому: он не
                // отбрасывается в конце, а просто не участвует в соревновании.
                if (candidate.points.getLast().getIndex() < freshFrom) {
                    continue;
                }

                if (best == null
                    || candidate.points.size() > best.points.size()
                    || (candidate.points.size() == best.points.size() && candidate.span() > best.span())) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    /**
     * Согласные вокруг прямой через две точки, а затем вокруг той же прямой, пересчитанной по одним
     * согласным. Второй проход - это и есть отличие от инкрементного поиска: направление определяют не
     * две выбранные точки, а все, кто к ним присоединился.
     */
    private Line consensusAround(List<Candle> extremes, Candle first, Candle second, Tolerances tolerance) {
        if (first.getIndex() == second.getIndex()) {
            return null;
        }

        double slope = (priceOf(second) - priceOf(first)) / (double) (second.getIndex() - first.getIndex());
        double intercept = priceOf(first) - slope * first.getIndex();
        List<Candle> agreeing = agreeing(extremes, slope, intercept, tolerance);

        if (agreeing.size() < minPoints) {
            return null;
        }

        double[] refitted = leastSquares(agreeing);
        double refittedIntercept = envelope ? restingIntercept(agreeing, refitted[0]) : refitted[1];

        return new Line(refitted[0], refittedIntercept, agreeing(extremes, refitted[0], refittedIntercept, tolerance));
    }

    /**
     * Свободный член прямой того же наклона, опущенной до самой низкой из точек - линия ложится на них
     * снизу, касаясь ближайшей.
     */
    protected double restingIntercept(List<Candle> points, double slope) {
        double lowest = Double.POSITIVE_INFINITY;

        for (Candle point : points) {
            lowest = Math.min(lowest, priceOf(point) - slope * point.getIndex());
        }

        return lowest;
    }

    /**
     * Экстремумы в допуске от прямой. С огибающей допуск односторонний: точка под линией её нарушает,
     * а не подтверждает, - иначе уровень снова оказался бы посередине.
     * <p>
     * Допуск у каждой точки свой: промах меряется волатильностью её собственного времени, поэтому
     * минимум из тихого месяца не обязан попадать в мерку, взятую с буйного.
     */
    private List<Candle> agreeing(List<Candle> extremes, double slope, double intercept, Tolerances tolerance) {
        List<Candle> agreeing = new ArrayList<>();

        for (Candle extreme : extremes) {
            double distance = priceOf(extreme) - (slope * extreme.getIndex() + intercept);
            double allowed = tolerance.at(extreme);

            if (envelope ? distance >= -TOUCHING && distance <= allowed : Math.abs(distance) <= allowed) {
                agreeing.add(extreme);
            }
        }

        return agreeing;
    }

    /** Прямая наименьших квадратов по точкам; при совпадающих индексах - горизонталь через их среднее. */
    protected double[] leastSquares(List<Candle> points) {
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;

        for (Candle point : points) {
            double x = point.getIndex();
            double y = priceOf(point);

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        int count = points.size();
        double denominator = count * sumXX - sumX * sumX;

        if (Math.abs(denominator) < 1e-9) {
            return new double[]{0, sumY / count};
        }

        double slope = (count * sumXY - sumX * sumY) / denominator;

        return new double[]{slope, (sumY - slope * sumX) / count};
    }

    /**
     * Индекс, раньше которого касание считается несвежим: конец окна минус заданный срок в барах,
     * переведённый в единицы индекса по среднему шагу окна. Без требования свежести - минус
     * бесконечность, то есть никакого порога.
     */
    protected long freshFrom(List<Candle> candles) {
        if (freshTouchWithin <= 0 || candles.size() < 2) {
            return Long.MIN_VALUE;
        }

        return candles.getLast().getIndex() - BarSpacing.unitsOf(candles, freshTouchWithin);
    }

    /**
     * Допуск у каждого бара окна: столько волатильностей его собственного времени, сколько задано.
     * Барам, у которых волатильность не посчиталась, достаётся допуск по всему окну - тот же, что был
     * до появления локальной меры.
     */
    protected Tolerances tolerancesOf(List<Candle> candles) {
        double[] volatility = volatilityCalculator.profile(candles);
        double fallback = toleranceOf(candles);
        Map<Long, Double> byIndex = new HashMap<>(candles.size());

        for (int at = 0; at < candles.size(); at++) {
            byIndex.put(candles.get(at).getIndex(), volatility[at] > 0 ? volatilities * volatility[at] : fallback);
        }

        return new Tolerances(byIndex, fallback);
    }

    /** Допуск в цене: столько волатильностей окна, сколько задано. */
    protected double toleranceOf(List<Candle> candles) {
        double volatility = volatilityCalculator.calculate(candles);

        if (volatility > 0) {
            return volatilities * volatility;
        }

        // Волатильность не посчиталась - окно короче её периода; тогда мерка берётся из самого окна,
        // чтобы допуск не оказался нулевым и уровень не выродился в точное совпадение цен.
        double high = candles.stream().mapToDouble(Candle::getHighAsDouble).max().orElse(0);
        double low = candles.stream().mapToDouble(Candle::getLowAsDouble).min().orElse(0);

        return volatilities * Math.max(1e-9, (high - low) / candles.size());
    }

    private Level<Double> levelOf(Line line, List<Candle> candles) {
        Candle first = line.points.getFirst();
        Candle last = line.points.getLast();
        Level<Double> level = new Level<>(
            new TimePoint(first.getIndex(), first.getTime()),
            new TimePoint(last.getIndex(), last.getTime()),
            new LinearFunction2D<>(line.slope, line.intercept, ArithmeticOperations.DOUBLE),
            0,
            line.points.size()
        );

        return level.withStrength(strengthCalculator.calculate(level, candles));
    }

    private double priceOf(Candle candle) {
        return priceExtractor.extract(candle).toDouble();
    }

    /** Прямая и экстремумы, которые её подтвердили, по возрастанию индекса. */
    protected record Line(double slope, double intercept, List<Candle> points) {
        protected Line {
            points = points.stream().sorted(Comparator.comparingLong(Candle::getIndex)).toList();
        }

        long span() {
            return points.getLast().getIndex() - points.getFirst().getIndex();
        }
    }

    /**
     * Сколько цене позволено отстоять от прямой у каждого бара окна. Бар, которого в окне не было,
     * меряется допуском по всему окну: такого не бывает, пока экстремумы приходят из того же окна, но
     * молча выдать ноль было бы хуже.
     */
    protected record Tolerances(Map<Long, Double> byIndex, double fallback) {
        double at(Candle candle) {
            return byIndex.getOrDefault(candle.getIndex(), fallback);
        }
    }

    /** Поддержка: прямая под ценой, проведённая по низам найденных минимумов. */
    public static ConsensusLineLevelDetector createSupport(ExtremeLocator minimumLocator) {
        return new ConsensusLineLevelDetector(minimumLocator).setPriceExtractor(Candle::low);
    }

    /** Сопротивление: прямая над ценой, по верхам найденных максимумов. */
    public static ConsensusLineLevelDetector createResistance(ExtremeLocator maximumLocator) {
        return new ConsensusLineLevelDetector(maximumLocator).setPriceExtractor(Candle::high);
    }
}
