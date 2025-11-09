package com.siberalt.singularity.presenter;

import com.siberalt.singularity.entity.candle.Candle;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public class StockPriceChart extends JFrame {

    public StockPriceChart(String title) {
        super(title);

        // Create dataset
        XYSeriesCollection dataset = createDataset();

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
        renderer.setSeriesPaint(0, Color.BLUE); // Stock prices
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
    }

    private XYSeriesCollection createDataset() {
        CvsFileCandleRepositoryFactory repositoryFactory = new CvsFileCandleRepositoryFactory();

        XYSeries stockPrices = new XYSeries("Stock Price");
        XYSeries buyOrders = new XYSeries("Buy Orders");
        XYSeries sellOrders = new XYSeries("Sell Orders");

        // Add data points (Date, Price)
        try (CvsCandleRepository candleRepository = repositoryFactory.create(
            "TMOS",
            "src/test/resources/entity.candle.cvs/TMOS"
        )) {
            Iterable<Candle> candles = candleRepository.getPeriod(
                "TMOS",
                Instant.parse("2019-01-01T00:00:00Z"),
                Instant.parse("2023-05-01T00:00:00Z")
            );
            for (Candle candle : candles) {
                double price = candle.getClosePrice().toDouble();
                stockPrices.add((double) candle.getTime().getEpochSecond() * 1000, price);

                // Example buy/sell orders based on some conditions
                if (Math.random() < 0.0001) {
                    buyOrders.add((double) candle.getTime().getEpochSecond() * 1000, price);
                } else if (Math.random() > 0.9999 ) {
                    sellOrders.add((double) candle.getTime().getEpochSecond() * 1000, price);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

//        // Add buy order points (Date, Price)
//        buyOrders.add(convertToDate(LocalDate.of(2023, 1, 15)), 155);
//        buyOrders.add(convertToDate(LocalDate.of(2023, 3, 10)), 168);
//
//        // Add sell order points (Date, Price)
//        sellOrders.add(convertToDate(LocalDate.of(2023, 2, 20)), 162);
//        sellOrders.add(convertToDate(LocalDate.of(2023, 4, 5)), 170);

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(stockPrices);
        dataset.addSeries(buyOrders);
        dataset.addSeries(sellOrders);

        return dataset;
    }

    private double convertToDate(LocalDate date) {
        return (double) Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()).getTime();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StockPriceChart example = new StockPriceChart("Stock Price Chart Example");
            example.setSize(800, 600);
            example.setLocationRelativeTo(null);
            example.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            example.setVisible(true);
        });
    }
}
