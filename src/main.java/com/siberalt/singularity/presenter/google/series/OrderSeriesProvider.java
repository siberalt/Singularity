package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.operation.Operation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class OrderSeriesProvider implements SeriesProvider {
    private final List<Operation> ordersOperations;
    private String buyOrdersTitle = "Buy Orders";
    private String sellOrdersTitle = "Sell Orders";
    private Shape buyOrderShape = Shape.DIAMOND;
    private Shape sellOrderShape = Shape.CIRCLE;
    private String buyOrdersColor = "#00FF00";
    private String sellOrdersColor = "#FF0000";
    private int buyPointsSize = 5;
    private int sellPointsSize = 5;
    private final TreeMap<Instant, Long> indexesByTime = new TreeMap<>();
    private boolean includeOutOfRangeOrders = false;

    public OrderSeriesProvider(List<Operation> orders, List<Candle> candles) {
        this.ordersOperations = orders;

        for (int i = 0; i < candles.size(); i++) {
            indexesByTime.put(candles.get(i).getTime(), (long) i);
        }
    }

    public int getBuyPointsSize() {
        return buyPointsSize;
    }

    public OrderSeriesProvider setBuyPointsSize(int buyPointsSize) {
        this.buyPointsSize = buyPointsSize;
        return this;
    }

    public int getSellPointsSize() {
        return sellPointsSize;
    }

    public OrderSeriesProvider setSellPointsSize(int sellPointsSize) {
        this.sellPointsSize = sellPointsSize;
        return this;
    }

    public OrderSeriesProvider setIncludeOutOfRangeOrders(boolean includeOutOfRangeOrders) {
        this.includeOutOfRangeOrders = includeOutOfRangeOrders;
        return this;
    }

    public OrderSeriesProvider setBuyOrdersTitle(String buyOrdersTitle) {
        this.buyOrdersTitle = buyOrdersTitle;
        return this;
    }

    public OrderSeriesProvider setSellOrdersTitle(String sellOrdersTitle) {
        this.sellOrdersTitle = sellOrdersTitle;
        return this;
    }

    public OrderSeriesProvider setBuyOrderShape(Shape buyOrderShape) {
        this.buyOrderShape = buyOrderShape;
        return this;
    }

    public OrderSeriesProvider setSellOrderShape(Shape sellOrderShape) {
        this.sellOrderShape = sellOrderShape;
        return this;
    }

    public OrderSeriesProvider setBuyOrdersColor(String buyOrdersColor) {
        this.buyOrdersColor = buyOrdersColor;
        return this;
    }

    public OrderSeriesProvider setSellOrdersColor(String sellOrdersColor) {
        this.sellOrdersColor = sellOrdersColor;
        return this;
    }

    @Override
    public Optional<SeriesChunk> provide(long start, long end, long stepInterval) {
        if (ordersOperations == null || ordersOperations.isEmpty() || indexesByTime.isEmpty()) {
            return Optional.empty();
        }

        PointSeriesProvider buyPoints = new PointSeriesProvider(buyOrdersTitle)
            .setSize(buyPointsSize)
            .setColor(buyOrdersColor)
            .setShape(buyOrderShape);

        PointSeriesProvider sellPoints = new PointSeriesProvider(sellOrdersTitle)
            .setSize(sellPointsSize)
            .setColor(sellOrdersColor)
            .setShape(sellOrderShape);

        for (Operation order : ordersOperations) {
            Instant orderTime = order.date();
            double orderPrice = order.price().toDouble();

            Map.Entry<Instant, Long> floorEntry = indexesByTime.floorEntry(orderTime);
            Map.Entry<Instant, Long> ceilingEntry = indexesByTime.ceilingEntry(orderTime);

            if (floorEntry == null || ceilingEntry == null) {
                if (includeOutOfRangeOrders) {
                    long edgeIndex = floorEntry == null
                        ? indexesByTime.firstEntry().getValue()
                        : indexesByTime.lastEntry().getValue();

                    if (order.direction().isBuy()) {
                        buyPoints.addPoint(edgeIndex + start, orderPrice);
                    } else {
                        sellPoints.addPoint(edgeIndex + start, orderPrice);
                    }
                }
                continue; // Skip orders outside the range if the flag is not set
            }

            long floorDiff = Math.abs(orderTime.toEpochMilli() - floorEntry.getKey().toEpochMilli());
            long ceilingDiff = Math.abs(orderTime.toEpochMilli() - ceilingEntry.getKey().toEpochMilli());
            Map.Entry<Instant, Long> closestEntry = floorDiff <= ceilingDiff ? floorEntry : ceilingEntry;

            long timeOrderIndex = closestEntry.getValue();

            if (order.direction().isBuy()) {
                buyPoints.addPoint(timeOrderIndex + start, orderPrice);
            } else {
                sellPoints.addPoint(timeOrderIndex + start, orderPrice);
            }
        }

        SeriesDataAggregator aggregator = new SeriesDataAggregator()
            .addSeriesProvider(buyPoints)
            .addSeriesProvider(sellPoints);

        return aggregator.provide(start, end, stepInterval);
    }
}
