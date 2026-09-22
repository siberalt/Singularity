package com.siberalt.singularity.strategy.level;

import com.siberalt.singularity.broker.contract.value.quotation.Quotation;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.extreme.ProminentExtremeLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

class VolumeProminenceRecencyWeigherTest {
    private static final Instant START = Instant.parse("2023-01-02T07:00:00Z");

    /**
     * Ровная цена - волатильности нет, и выпуклость в вес не входит. Остаются объём против медианного и
     * свежесть: последний бар с удвоенным объёмом весит два, бар с медианным объёмом за полураспад до
     * конца - половину.
     */
    @Test
    void volumeOverTheMedianTimesHalvingWithAge() {
        List<Candle> window = new ArrayList<>();

        for (int at = 0; at < 7; at++) {
            window.add(candle(at, at == 6 ? 4 : 2));
        }

        VolumeProminenceRecencyWeigher weigher = new VolumeProminenceRecencyWeigher(
            ProminentExtremeLocator.ofMinimums(candles -> candles, 1.0), candles -> 0, 2);
        ToDoubleFunction<Candle> weight = weigher.weigh(List.of(window.get(4), window.get(6)), window);

        assertEquals(2.0, weight.applyAsDouble(window.get(6)), 1e-9);
        assertEquals(0.5, weight.applyAsDouble(window.get(4)), 1e-9);
    }

    @Test
    void aDeeperPitWeighsMore() {
        double[] lows = {105, 103, 100, 103, 105, 104, 102, 104, 105};
        List<Candle> window = new ArrayList<>();

        for (int at = 0; at < lows.length; at++) {
            Quotation low = Quotation.of(lows[at]);
            Quotation high = Quotation.of(lows[at] + 1);
            window.add(new Candle(1, new TimePoint(at, START.plusSeconds(3600L * at)), low, high, high, low, 1));
        }

        VolumeProminenceRecencyWeigher weigher = new VolumeProminenceRecencyWeigher(
            ProminentExtremeLocator.ofMinimums(candles -> candles, 1.0), candles -> 1, 1_000_000);
        ToDoubleFunction<Candle> weight = weigher.weigh(List.of(window.get(2), window.get(6)), window);

        assertTrue(weight.applyAsDouble(window.get(2)) > weight.applyAsDouble(window.get(6)));
    }

    private static Candle candle(long index, long volume) {
        Quotation price = Quotation.of(100.0);

        return new Candle(1, new TimePoint(index, START.plusSeconds(3600L * index)), price, price, price, price, volume);
    }
}
