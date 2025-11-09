package com.siberalt.singularity.strategy.level.linear;
import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.strategy.extremum.ExtremumLocator;
import com.siberalt.singularity.strategy.level.Level;
import com.siberalt.singularity.strategy.market.CandleIndexProvider;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ClusterLevelDetectorTest {

    @Test
    void detectsLevelsFromCandlesWithinSensitivityRange() {
        CandleIndexProvider mockProvider = Mockito.mock(CandleIndexProvider.class);
        when(mockProvider.provideIndex(Mockito.any())).thenReturn(1L);

        Candle candle1 = Candle.of(Instant.now(), 100, 105.0);
        Candle candle2 = Candle.of(Instant.now(), 101, 106.0);
        List<Candle> candles = List.of(candle1, candle2);
        ExtremumLocator mockExtremumLocator = Mockito.mock(ExtremumLocator.class);
        when(mockExtremumLocator.locate(candles)).thenReturn(candles);

        ClusterLevelDetector detector = new ClusterLevelDetector(0.03, mockExtremumLocator);
        List<Level<Double>> levels = detector.detect(candles, mockProvider);

        assertEquals(1, levels.size());
        assertEquals(105.5, levels.get(0).function().apply(0.0));
    }

    @Test
    void createsSeparateLevelsForCandlesOutsideSensitivityRange() {
        CandleIndexProvider mockProvider = Mockito.mock(CandleIndexProvider.class);
        when(mockProvider.provideIndex(Mockito.any())).thenReturn(1L);

        Candle candle1 = Candle.of(Instant.now(), 100, 105.0);
        Candle candle2 = Candle.of(Instant.now(), 100, 105.0);
        Candle candle3 = Candle.of(Instant.now(), 110, 115.0);
        Candle candle4 = Candle.of(Instant.now(), 110, 115.0);
        List<Candle> candles = List.of(candle1, candle2, candle3, candle4);
        ExtremumLocator mockExtremumLocator = Mockito.mock(ExtremumLocator.class);
        when(mockExtremumLocator.locate(candles)).thenReturn(candles);

        ClusterLevelDetector detector = new ClusterLevelDetector(0.01, mockExtremumLocator);
        List<Level<Double>> levels = detector.detect(candles, mockProvider);

        assertEquals(2, levels.size());
    }

    @Test
    void updatesLevelWithNewCandleWithinSensitivityRange() {
        CandleIndexProvider mockProvider = Mockito.mock(CandleIndexProvider.class);
        when(mockProvider.provideIndex(Mockito.any())).thenReturn(1L);

        Candle candle1 = Candle.of(Instant.now(), 100, 105.0);
        Candle candle2 = Candle.of(Instant.now(), 101, 106.0);
        List<Candle> candles = List.of(candle1, candle2);
        ExtremumLocator mockExtremumLocator = Mockito.mock(ExtremumLocator.class);
        when(mockExtremumLocator.locate(candles)).thenReturn(candles);

        ClusterLevelDetector detector = new ClusterLevelDetector(0.03, mockExtremumLocator);
        List<Level<Double>> levels = detector.detect(candles, mockProvider);

        assertEquals(1, levels.size());
        assertEquals(105.5, levels.get(0).function().apply(0.0));
    }

    @Test
    void handlesEmptyCandleList() {
        CandleIndexProvider mockProvider = Mockito.mock(CandleIndexProvider.class);
        ExtremumLocator mockExtremumLocator = Mockito.mock(ExtremumLocator.class);
        when(mockExtremumLocator.locate(any())).thenReturn(List.of());

        ClusterLevelDetector detector = new ClusterLevelDetector(0.03, mockExtremumLocator);
        List<Level<Double>> levels = detector.detect(List.of(), mockProvider);

        assertEquals(0, levels.size());
    }

    @Test
    void detectsLevelsAcrossMultipleDetectCalls() {
        CandleIndexProvider mockProvider = Mockito.mock(CandleIndexProvider.class);
        when(mockProvider.provideIndex(Mockito.any())).thenReturn(1L);

        Candle candle1 = Candle.of(Instant.now(), 100, 105.0);
        Candle candle2 = Candle.of(Instant.now(), 101, 106.0);
        List<Candle> candlesFirstCall = List.of(candle1, candle2);

        Candle candle3 = Candle.of(Instant.now(), 110, 115.0);
        Candle candle4 = Candle.of(Instant.now(), 111, 116.0);
        List<Candle> candlesSecondCall = List.of(candle3, candle4);

        ExtremumLocator mockExtremumLocator = Mockito.mock(ExtremumLocator.class);
        when(mockExtremumLocator.locate(candlesFirstCall)).thenReturn(candlesFirstCall);
        when(mockExtremumLocator.locate(candlesSecondCall)).thenReturn(candlesSecondCall);

        ClusterLevelDetector detector = new ClusterLevelDetector(0.03, mockExtremumLocator);

        // First detect call
        List<Level<Double>> levelsFirstCall = detector.detect(candlesFirstCall, mockProvider);
        assertEquals(1, levelsFirstCall.size());
        assertEquals(105.5, levelsFirstCall.get(0).function().apply(0.0));

        // Second detect call
        List<Level<Double>> levelsSecondCall = detector.detect(candlesSecondCall, mockProvider);
        assertEquals(2, levelsSecondCall.size());
        assertEquals(115.5, levelsSecondCall.get(1).function().apply(0.0));
    }
}
