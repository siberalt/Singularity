package com.siberalt.singularity.strategy.extremum.cache;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

public class RuntimeExtremeRepository implements ExtremeRepository {
    private final HashMap<String, TreeMap<Long, Candle>> storage = new HashMap<>();

    @Override
    public void saveBatch(Range range, List<Candle> extremes) {
        String instrumentId = range.instrumentId();
        String key = generateKey(instrumentId, range.extremeType());
        TreeMap<Long, Candle> instrumentStorage = storage.computeIfAbsent(key, k -> new TreeMap<>());

        for (Candle extreme : extremes) {
            instrumentStorage.put(extreme.getIndex(), extreme);
        }
    }

    @Override
    public Range getInnerRange(Range outerRange) {
        String instrumentId = outerRange.instrumentId();
        String key = generateKey(instrumentId, outerRange.extremeType());
        TreeMap<Long, Candle> instrumentStorage = storage.get(key);

        if (instrumentStorage == null) {
            return null;
        }

        Long firstKey = instrumentStorage.ceilingKey(outerRange.fromIndex());
        Long lastKey = instrumentStorage.floorKey(outerRange.toIndex());

        if (firstKey == null || lastKey == null) {
            return null;
        }

        return new Range(firstKey, lastKey, instrumentId, outerRange.extremeType());
    }

    @Override
    public List<Candle> getByRange(Range range) {
        String instrumentId = range.instrumentId();
        String key = generateKey(instrumentId, range.extremeType());
        TreeMap<Long, Candle> instrumentStorage = storage.get(key);

        if (instrumentStorage == null) {
            return List.of();
        }

        return instrumentStorage.subMap(range.fromIndex(), true, range.toIndex(), true)
                .values()
                .stream()
                .toList();
    }

    @Override
    public void deleteBatch(List<Range> ranges) {
        for (Range range : ranges) {
            String instrumentId = range.instrumentId();
            String key = generateKey(instrumentId, range.extremeType());
            TreeMap<Long, Candle> instrumentStorage = storage.get(key);

            if (instrumentStorage != null) {
                instrumentStorage.subMap(range.fromIndex(), true, range.toIndex(), true).clear();
            }
        }
    }

    private String generateKey(String instrumentId, String extremeType) {
        return instrumentId + ":" + extremeType;
    }
}
