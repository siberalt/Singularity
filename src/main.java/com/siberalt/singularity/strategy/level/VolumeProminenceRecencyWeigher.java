package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extreme.ProminentExtremeLocator;
import com.siberalt.singularity.strategy.volatility.ATRVolatilityCalculator;
import com.siberalt.singularity.strategy.volatility.VolatilityCalculator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

/**
 * Вес точки - произведение трёх множителей: объёма, выпуклости и свежести.
 * <p>
 * Каждый множитель безразмерен, чтобы произведение не зависело от бумаги и её цены: объём бара делится
 * на медианный объём окна, выпуклость экстремума - на волатильность его собственного времени, свежесть
 * половинится каждые {@code halfLife} баров от конца окна. Объём и выпуклость не опускаются ниже
 * {@link #FLOOR}: тихий бар или неглубокая яма должны весить мало, но не выпадать из уровня совсем, иначе
 * произведение обнуляет точку одним множителем.
 */
public class VolumeProminenceRecencyWeigher implements ExtremeWeigher {
    public static final int DEFAULT_HALF_LIFE = 300;
    static final double FLOOR = 0.1;

    private final ProminentExtremeLocator depth;
    private final VolatilityCalculator volatilityCalculator;
    private final int halfLife;

    /**
     * @param depth    чем мерить выпуклость; сторона - минимумы или максимумы - задаётся им
     * @param halfLife через сколько баров от конца окна вес точки падает вдвое
     */
    public VolumeProminenceRecencyWeigher(ProminentExtremeLocator depth, VolatilityCalculator volatilityCalculator,
                                          int halfLife) {
        if (halfLife < 1) {
            throw new IllegalArgumentException("Полураспад должен быть хотя бы в один бар, получено " + halfLife);
        }

        this.depth = Objects.requireNonNull(depth);
        this.volatilityCalculator = Objects.requireNonNull(volatilityCalculator);
        this.halfLife = halfLife;
    }

    public static VolumeProminenceRecencyWeigher ofMinimums(int halfLife) {
        return new VolumeProminenceRecencyWeigher(
            ProminentExtremeLocator.ofMinimums(candles -> candles, 1.0), new ATRVolatilityCalculator(14), halfLife);
    }

    public static VolumeProminenceRecencyWeigher ofMaximums(int halfLife) {
        return new VolumeProminenceRecencyWeigher(
            ProminentExtremeLocator.ofMaximums(candles -> candles, 1.0), new ATRVolatilityCalculator(14), halfLife);
    }

    @Override
    public ToDoubleFunction<Candle> weigh(List<Candle> extremes, List<Candle> window) {
        if (window.isEmpty()) {
            return extreme -> FLOOR;
        }

        Map<Long, Integer> positions = new HashMap<>(window.size());

        for (int at = 0; at < window.size(); at++) {
            positions.put(window.get(at).getIndex(), at);
        }

        long[] volumes = window.stream().mapToLong(Candle::volume).sorted().toArray();
        double medianVolume = Math.max(1, volumes[volumes.length / 2]);
        double[] volatility = volatilityCalculator.profile(window);
        int last = window.size() - 1;
        Map<Long, Double> weights = new HashMap<>(extremes.size());

        for (Candle extreme : extremes) {
            Integer at = positions.get(extreme.getIndex());

            if (at == null) {
                continue;
            }

            double volume = Math.max(FLOOR, extreme.volume() / medianVolume);
            double prominence = volatility[at] > 0
                ? Math.max(FLOOR, depth.prominenceOf(window, at) / volatility[at])
                : 1;
            double recency = Math.pow(0.5, (last - at) / (double) halfLife);

            weights.put(extreme.getIndex(), volume * prominence * recency);
        }

        return extreme -> weights.getOrDefault(extreme.getIndex(), FLOOR);
    }
}
