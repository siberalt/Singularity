package com.siberalt.singularity.strategy.level.zone;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ExtremeLocator;
import com.siberalt.singularity.strategy.level.ExtremeWeigher;
import com.siberalt.singularity.strategy.level.VolumeProminenceRecencyWeigher;
import com.siberalt.singularity.strategy.market.PriceExtractor;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Горизонтальные зоны как плотные по цене скопления экстремумов - DBSCAN, в котором вес точки участвует
 * трижды.
 * <ol>
 * <li><b>Ядро по весу.</b> Точка становится ядром, если суммарный вес её соседей, её саму включая, не
 * меньше {@link #setMinWeight порога}. Зона растёт от ядра через соседей, и дальше - только через те из
 * них, что сами ядра; соседи, которые ядрами не стали, входят в зону краем, но её не продолжают.</li>
 * <li><b>Дальность по весу.</b> Соседи точки - те, чья цена отстоит от её цены не дальше её радиуса, а
 * радиус растёт с весом: {@code eps_i = base_i * (1 + reach * w_i / max w)}. Тяжёлая точка собирает
 * вокруг себя шире, лёгкая - только ближних. Соседство поэтому несимметрично: тяжёлая точка может
 * видеть лёгкую, а та её - нет.</li>
 * <li><b>Центр по весу.</b> Цена зоны - средневзвешенная цена её экстремумов.</li>
 * </ol>
 * Базовый радиус точки - столько волатильностей её собственного времени, сколько задано, а не одна мерка
 * на всё окно: окно - это месяцы, и январская яма не должна мериться мартовской линейкой.
 * <p>
 * Границы зоны - от самой низкой до самой высокой цены её экстремумов, раздвинутые на {@link #setBuffer
 * запас} в базовых радиусах её точек. Верх разворотного бара в границы не входит: у каждого десятого
 * бара свинг-минимума размах больше четырёх ATR, и такая свеча растягивала бы зону на всю себя.
 * <p>
 * Веса перед этим приводятся к среднему, равному единице, по всем экстремумам окна: порог тогда меряется
 * в «средних точках» и не зависит ни от бумаги, ни от того, из каких множителей вес собран. Радиус от
 * этого не меняется - он зависит только от отношения веса к наибольшему. При равных весах и нулевой
 * дальности это обычный DBSCAN с порогом в точках.
 */
public class WeightedDBSCANZoneDetector implements ZoneDetector {
    public static final double DEFAULT_VOLATILITIES = 0.5;
    public static final double DEFAULT_REACH = 1.0;
    public static final double DEFAULT_BUFFER = 0.5;
    public static final double DEFAULT_MIN_WEIGHT = 3.0;
    public static final int DEFAULT_MIN_POINTS = 1;
    public static final int DEFAULT_MAX_ZONES = 10;

    private final ExtremeLocator extremeLocator;
    private PriceExtractor priceExtractor = Candle::low;
    private VolatilityCalculator volatilityCalculator = new ATRVolatilityCalculator(14);
    private double volatilities = DEFAULT_VOLATILITIES;
    private double reach = DEFAULT_REACH;
    private double buffer = DEFAULT_BUFFER;
    private ExtremeWeigher weigher = ExtremeWeigher.EQUAL;
    private double minWeight = DEFAULT_MIN_WEIGHT;
    private int minPoints = DEFAULT_MIN_POINTS;
    private int maxZones = DEFAULT_MAX_ZONES;

    public WeightedDBSCANZoneDetector(ExtremeLocator extremeLocator) {
        this.extremeLocator = Objects.requireNonNull(extremeLocator);
    }

    /** Зоны поддержки: скопления минимумов по низам баров, веса - объём × выпуклость ямы × свежесть. */
    public static WeightedDBSCANZoneDetector createSupport(ExtremeLocator minimumLocator) {
        return new WeightedDBSCANZoneDetector(minimumLocator)
            .setPriceExtractor(Candle::low)
            .setWeigher(VolumeProminenceRecencyWeigher.ofMinimums(VolumeProminenceRecencyWeigher.DEFAULT_HALF_LIFE));
    }

    /** Зоны сопротивления: скопления максимумов по верхам баров, веса - объём × выпуклость × свежесть. */
    public static WeightedDBSCANZoneDetector createResistance(ExtremeLocator maximumLocator) {
        return new WeightedDBSCANZoneDetector(maximumLocator)
            .setPriceExtractor(Candle::high)
            .setWeigher(VolumeProminenceRecencyWeigher.ofMaximums(VolumeProminenceRecencyWeigher.DEFAULT_HALF_LIFE));
    }

    /** По какой цене экстремума мерить расстояние между точками: для поддержки - низ, для сопротивления - верх. */
    public WeightedDBSCANZoneDetector setPriceExtractor(PriceExtractor priceExtractor) {
        this.priceExtractor = Objects.requireNonNull(priceExtractor);
        return this;
    }

    /** Базовый радиус соседства - в волатильностях собственного времени точки. */
    public WeightedDBSCANZoneDetector setVolatilityTolerance(VolatilityCalculator volatilityCalculator,
                                                             double volatilities) {
        if (volatilities <= 0) {
            throw new IllegalArgumentException("Допуск должен быть положительным, получено " + volatilities);
        }

        this.volatilityCalculator = Objects.requireNonNull(volatilityCalculator);
        this.volatilities = volatilities;
        return this;
    }

    /**
     * Насколько радиус самой тяжёлой точки окна больше базового: при единице - вдвое, при нуле радиус от
     * веса не зависит.
     */
    public WeightedDBSCANZoneDetector setReach(double reach) {
        if (reach < 0) {
            throw new IllegalArgumentException("Дальность не может быть отрицательной, получено " + reach);
        }

        this.reach = reach;
        return this;
    }

    /** На сколько базовых радиусов раздвинуть границы зоны за крайние цены её точек. */
    public WeightedDBSCANZoneDetector setBuffer(double buffer) {
        if (buffer < 0) {
            throw new IllegalArgumentException("Запас не может быть отрицательным, получено " + buffer);
        }

        this.buffer = buffer;
        return this;
    }

    public WeightedDBSCANZoneDetector setWeigher(ExtremeWeigher weigher) {
        this.weigher = Objects.requireNonNull(weigher);
        return this;
    }

    /** Какой суммарный вес соседей делает точку ядром - в весах средней точки окна. */
    public WeightedDBSCANZoneDetector setMinWeight(double minWeight) {
        if (minWeight <= 0) {
            throw new IllegalArgumentException("Порог веса должен быть положительным, получено " + minWeight);
        }

        this.minWeight = minWeight;
        return this;
    }

    /**
     * Сколько экстремумов делают скопление зоной, каков бы ни был их вес. По умолчанию одного хватает:
     * решает вес, и одна достаточно тяжёлая точка - уже зона.
     */
    public WeightedDBSCANZoneDetector setMinPoints(int minPoints) {
        if (minPoints < 1) {
            throw new IllegalArgumentException("Зону образует хотя бы одна точка, получено " + minPoints);
        }

        this.minPoints = minPoints;
        return this;
    }

    public WeightedDBSCANZoneDetector setMaxZones(int maxZones) {
        if (maxZones < 1) {
            throw new IllegalArgumentException("Зон должно быть хотя бы одна, получено " + maxZones);
        }

        this.maxZones = maxZones;
        return this;
    }

    @Override
    public List<Zone> detect(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        List<Candle> extremes = extremeLocator.locate(candles);

        if (extremes.isEmpty() || extremes.size() < minPoints) {
            return List.of();
        }

        int count = extremes.size();
        double[] prices = new double[count];
        double[] bases = basesOf(extremes, candles);
        double[] weights = normalisedWeightsOf(extremes, candles);
        double heaviest = Arrays.stream(weights).max().orElse(1);

        for (int point = 0; point < count; point++) {
            prices[point] = priceExtractor.extract(extremes.get(point)).toDouble();
        }

        List<List<Integer>> neighbours = new ArrayList<>(count);
        boolean[] core = new boolean[count];

        for (int point = 0; point < count; point++) {
            double radius = bases[point] * (1 + reach * weights[point] / heaviest);
            List<Integer> mine = new ArrayList<>();
            double weight = 0;

            for (int other = 0; other < count; other++) {
                if (Math.abs(prices[point] - prices[other]) <= radius) {
                    mine.add(other);
                    weight += weights[other];
                }
            }

            neighbours.add(mine);
            core[point] = weight >= minWeight;
        }

        int[] clusterOf = new int[count];
        Arrays.fill(clusterOf, -1);
        List<List<Integer>> clusters = new ArrayList<>();

        for (int seed = 0; seed < count; seed++) {
            if (!core[seed] || clusterOf[seed] >= 0) {
                continue;
            }

            int cluster = clusters.size();
            List<Integer> members = new ArrayList<>();

            clusterOf[seed] = cluster;
            members.add(seed);

            // Участники добавляются в конец по ходу обхода - это и есть очередь.
            for (int next = 0; next < members.size(); next++) {
                int current = members.get(next);

                // Край зоны её не продолжает: дальше идут только от ядер.
                if (!core[current]) {
                    continue;
                }

                for (int neighbour : neighbours.get(current)) {
                    if (clusterOf[neighbour] < 0) {
                        clusterOf[neighbour] = cluster;
                        members.add(neighbour);
                    }
                }
            }

            clusters.add(members);
        }

        List<Zone> zones = new ArrayList<>();

        for (List<Integer> members : clusters) {
            if (members.size() >= minPoints) {
                zones.add(zoneOf(members, extremes, prices, weights, bases));
            }
        }

        zones.sort(Comparator.comparingDouble(Zone::strength).reversed());

        if (zones.size() > maxZones) {
            zones.subList(maxZones, zones.size()).clear();
        }

        return zones;
    }

    private Zone zoneOf(List<Integer> members, List<Candle> extremes, double[] prices, double[] weights,
                        double[] bases) {
        Candle first = null;
        Candle last = null;
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        double weight = 0;
        double weightedPrice = 0;
        double base = 0;

        for (int member : members) {
            Candle extreme = extremes.get(member);

            if (first == null || extreme.getIndex() < first.getIndex()) {
                first = extreme;
            }

            if (last == null || extreme.getIndex() > last.getIndex()) {
                last = extreme;
            }

            lowest = Math.min(lowest, prices[member]);
            highest = Math.max(highest, prices[member]);
            weight += weights[member];
            weightedPrice += weights[member] * prices[member];
            base += bases[member] / members.size();
        }

        return new Zone(
            new TimePoint(first.getIndex(), first.getTime()),
            new TimePoint(last.getIndex(), last.getTime()),
            lowest - buffer * base,
            highest + buffer * base,
            weight > 0 ? weightedPrice / weight : (lowest + highest) / 2,
            weight,
            members.size()
        );
    }

    /**
     * Веса, приведённые к среднему в единицу. Если все веса нулевые, приводить нечего, и все точки
     * получают по единице - как без весов.
     */
    private double[] normalisedWeightsOf(List<Candle> extremes, List<Candle> candles) {
        ToDoubleFunction<Candle> weigh = weigher.weigh(extremes, candles);
        double[] weights = new double[extremes.size()];
        double total = 0;

        for (int point = 0; point < weights.length; point++) {
            weights[point] = Math.max(0, weigh.applyAsDouble(extremes.get(point)));
            total += weights[point];
        }

        if (total <= 0) {
            Arrays.fill(weights, 1);
            return weights;
        }

        double scale = weights.length / total;

        for (int point = 0; point < weights.length; point++) {
            weights[point] *= scale;
        }

        return weights;
    }

    /**
     * Базовый радиус каждой точки: столько волатильностей её времени, сколько задано. Точке, у которой
     * волатильность не посчиталась, достаётся радиус по всему окну.
     */
    private double[] basesOf(List<Candle> extremes, List<Candle> candles) {
        double[] volatility = volatilityCalculator.profile(candles);
        double fallback = fallbackBaseOf(candles);
        Map<Long, Double> byIndex = new HashMap<>(candles.size());

        for (int at = 0; at < candles.size(); at++) {
            byIndex.put(candles.get(at).getIndex(), volatility[at] > 0 ? volatilities * volatility[at] : fallback);
        }

        double[] bases = new double[extremes.size()];

        for (int point = 0; point < bases.length; point++) {
            bases[point] = byIndex.getOrDefault(extremes.get(point).getIndex(), fallback);
        }

        return bases;
    }

    private double fallbackBaseOf(List<Candle> candles) {
        double volatility = volatilityCalculator.calculate(candles);

        if (volatility > 0) {
            return volatilities * volatility;
        }

        double high = candles.stream().mapToDouble(Candle::getHighAsDouble).max().orElse(0);
        double low = candles.stream().mapToDouble(Candle::getLowAsDouble).min().orElse(0);

        return volatilities * Math.max(1e-9, (high - low) / candles.size());
    }
}
