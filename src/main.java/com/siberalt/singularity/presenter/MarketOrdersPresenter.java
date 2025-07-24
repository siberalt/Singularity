package com.siberalt.singularity.presenter;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.ReadCandleRepository;
import com.siberalt.singularity.entity.order.Order;
import com.siberalt.singularity.entity.order.ReadOrderRepository;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.time.Instant;

public class MarketOrdersPresenter extends JFrame {
    private final ReadCandleRepository candleRepository;
    private final String instrumentUid;
    private final String accountId;
    private final ReadOrderRepository orderRepository;

    public MarketOrdersPresenter(
        ReadCandleRepository candleRepository,
        String instrumentUid,
        String accountId,
        ReadOrderRepository orderRepository
    ) {
        this.candleRepository = candleRepository;
        this.instrumentUid = instrumentUid;
        this.accountId = accountId;
        this.orderRepository = orderRepository;
    }

    public void show(Instant from, Instant to) {
        // Create dataset
        XYSeriesCollection dataset = createDataset(from, to);

        // Create chart
        JFreeChart chart = ChartFactory.createXYLineChart(
            "Stock Price Chart",
            "Date",
            "Price",
            dataset,
            PlotOrientation.VERTICAL,
            true, true, false);

        // Customize the chart
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis axis = new DateAxis("Date");
        axis.setDateFormatOverride(new SimpleDateFormat("yyyy-MM-dd"));
        plot.setDomainAxis(axis);

        // Customize renderer to show different colors and shapes for buy/sell orders
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, Color.BLACK); // Stock prices
        renderer.setSeriesShapesVisible(0, false); // Hide points for stock prices
        renderer.setSeriesLinesVisible(0, true); // Show line for stock prices
        renderer.setSeriesPaint(1, Color.GREEN); // Buy orders
        renderer.setSeriesPaint(2, Color.RED); // Sell orders
        renderer.setSeriesShapesVisible(1, true);
        renderer.setSeriesShapesVisible(2, true);
        renderer.setSeriesLinesVisible(1, false);
        renderer.setSeriesLinesVisible(2, false);
        plot.setRenderer(renderer);

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new java.awt.Dimension(800, 600));
        setContentPane(chartPanel);

        SwingUtilities.invokeLater(() -> {
            this.setSize(800, 600);
            this.setLocationRelativeTo(null);
            this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            this.setVisible(true);
        });
    }

    private XYSeriesCollection createDataset(Instant from, Instant to) {
        XYSeries stockPrices = new XYSeries("Stock Price");
        XYSeries buyOrders = new XYSeries("Buy Orders");
        XYSeries sellOrders = new XYSeries("Sell Orders");

        for (Candle candle : candleRepository.getPeriod(instrumentUid, from, to)) {
            double price = candle.getClosePrice().toBigDecimal().doubleValue();
            stockPrices.add((double) candle.getTime().getEpochSecond() * 1000, price);
        }

        Iterable<Order> orders = orderRepository.getByAccountIdAndInstrumentUid(accountId, instrumentUid);

        for (Order order : orders) {
            double price = order.getInstrumentPrice().toBigDecimal().doubleValue();
            if (order.getDirection().isBuy()) {
                buyOrders.add((double) order.getCreatedTime().getEpochSecond() * 1000, price);
            } else if (order.getDirection().isSell()) {
                sellOrders.add((double) order.getCreatedTime().getEpochSecond() * 1000, price);
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(stockPrices);
        dataset.addSeries(buyOrders);
        dataset.addSeries(sellOrders);

        return dataset;
    }
}
