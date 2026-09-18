package com.siberalt.singularity.presenter.google.series;

import com.siberalt.singularity.entity.operation.Operation;

import java.util.List;
import java.util.Optional;

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
    private boolean includeOutOfRangeOrders = false;

    public OrderSeriesProvider(List<Operation> orders) {
        this.ordersOperations = orders;
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

    /**
     * Each order in the row of the bar it was executed during, found by time. Not the nearest bar:
     * an order at ten to eleven belongs to the ten o'clock hour, not to eleven. Orders before the
     * first bar or after the last are left out, or put on the edge bar when asked to include them.
     */
    @Override
    public Optional<SeriesChunk> provide(BarAxis axis, long stepInterval) {
        if (ordersOperations == null || ordersOperations.isEmpty() || axis.isEmpty()) {
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
            int row = axis.rowOfTime(order.executedDate());

            if (row < 0 || row >= axis.size()) {
                if (!includeOutOfRangeOrders) {
                    continue;
                }

                row = row < 0 ? 0 : axis.size() - 1;
            }

            // Points are placed by index, so a row is named by the index of the bar standing on it.
            (order.direction().isBuy() ? buyPoints : sellPoints).addPoint(axis.indexAt(row), order.price().toDouble());
        }

        return new SeriesDataAggregator()
            .addSeriesProvider(buyPoints)
            .addSeriesProvider(sellPoints)
            .provide(axis, stepInterval);
    }
}
