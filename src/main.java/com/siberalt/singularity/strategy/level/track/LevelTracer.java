package com.siberalt.singularity.strategy.level.track;

import com.siberalt.singularity.entity.candle.TimePoint;
import com.siberalt.singularity.strategy.level.Level;

import java.util.*;
import java.util.stream.Collectors;

public class LevelTracer {
    private final double traceSensitivity;

    public LevelTracer(double traceSensitivity) {
        this.traceSensitivity = traceSensitivity;
    }

    public LevelTraceGroup trace(List<SnapshotLevelGroup> snapshots) {
        if (snapshots.isEmpty()) {
            return LevelTraceGroup.EMPTY;
        }

        List<LevelTraceBuilder> resultTraceBuilders = new ArrayList<>();
        NavigableMap<Double, LevelTraceBuilder> previousTraceBuilders = new TreeMap<>();

        for (SnapshotLevelGroup snapshot : snapshots) {
            List<Level<Double>> levels = snapshot.levels();
            NavigableMap<Double, LevelTraceBuilder> newTraceBuilders = new TreeMap<>();

            for (Level<Double> level : levels) {
                Optional<LevelTraceBuilder> optionalClosestTraceBuilder = findClosestTraceBuilder(
                    snapshot.fromIndex(),
                    traceSensitivity,
                    level,
                    previousTraceBuilders
                );

                LevelTraceBuilder currentBuilder;

                if (optionalClosestTraceBuilder.isPresent()) {
                    optionalClosestTraceBuilder
                        .get()
                        .addFunction(snapshot.fromPoint(), snapshot.toPoint(), level.function());
                    currentBuilder = optionalClosestTraceBuilder.get();
                } else {
                    LevelTraceBuilder newTraceBuilder = new LevelTraceBuilder();
                    newTraceBuilder.addFunction(
                        snapshot.fromPoint(),
                        snapshot.toPoint(),
                        level.function()
                    );
                    resultTraceBuilders.add(newTraceBuilder);
                    currentBuilder = newTraceBuilder;
                }

                newTraceBuilders.put(level.function().apply((double) snapshot.toIndex()), currentBuilder);
            }

            previousTraceBuilders = newTraceBuilders;
        }

        TimePoint fromPoint = snapshots.get(0).fromPoint();
        TimePoint toPoint = snapshots.get(snapshots.size() - 1).toPoint();

        return new LevelTraceGroup(
            fromPoint,
            toPoint,
            resultTraceBuilders
                .stream()
                .map(LevelTraceBuilder::build)
                .collect(Collectors.toList())
        );
    }

    private Optional<LevelTraceBuilder> findClosestTraceBuilder(
        long levelStartIndex,
        double neighborhoodRatio,
        Level<Double> currentLevel,
        NavigableMap<Double, LevelTraceBuilder> allTraceBuilders
    ) {
        double startRangeValue = currentLevel.function().apply((double) levelStartIndex);
        double rangeStart = startRangeValue - neighborhoodRatio * startRangeValue;
        double rangeEnd = startRangeValue + neighborhoodRatio * startRangeValue;
        NavigableMap<Double, LevelTraceBuilder> subMap = allTraceBuilders
            .subMap(rangeStart, true, rangeEnd, true);
        Map.Entry<Double, LevelTraceBuilder> bottomEntry = subMap.floorEntry(startRangeValue);
        Map.Entry<Double, LevelTraceBuilder> topEntry = subMap.ceilingEntry(startRangeValue);

        if (bottomEntry == null && topEntry == null) {
            return Optional.empty();
        }

        if (bottomEntry != null && topEntry != null) {
            LevelTraceBuilder closestTraceBuilder = Math.abs(bottomEntry.getKey() - startRangeValue) <= Math.abs(topEntry.getKey() - startRangeValue)
                ? bottomEntry.getValue()
                : topEntry.getValue();

            return Optional.of(closestTraceBuilder);
        }

        return Optional.of(Objects.requireNonNullElse(bottomEntry, topEntry).getValue());
    }
}
