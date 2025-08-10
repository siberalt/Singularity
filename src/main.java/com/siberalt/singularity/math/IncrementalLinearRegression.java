package com.siberalt.singularity.math;

import java.util.ArrayList;
import java.util.List;

public class IncrementalLinearRegression {
    private final List<Point2D<Double>> inliers = new ArrayList<>();
    private double slope;
    private double intercept;
    private final double threshold;
    private boolean modelValid = false;

    // Статистические суммы для инкрементальных вычислений
    private double sumX = 0;
    private double sumY = 0;
    private double sumXY = 0;
    private double sumXX = 0;
    private int n = 0;

    public IncrementalLinearRegression(double threshold) {
        this.threshold = threshold;
    }

    public boolean addPoint(Point2D<Double> p) {
        // Для первых двух точек просто добавляем без проверки
        if (n < 2) {
            addToModel(p);
            if (n == 2) {
                recalculateModel();
            }
            return true;
        }

        // Для последующих точек проверяем отклонение
        double predictedY = predict(p.x());
        double error = Math.abs((p.y() - predictedY) / p.y());

        if (error <= threshold) {
            addToModel(p);
            recalculateModel();
            return true;
        }

        return false;
    }

    private void addToModel(Point2D<Double> p) {
        inliers.add(p);
        n++;
        sumX += p.x();
        sumY += p.y();
        sumXY += p.x() * p.y();
        sumXX += p.x() * p.x();
    }

    private void recalculateModel() {
        double denominator = n * sumXX - sumX * sumX;

        if (Math.abs(denominator) < 1e-10) {
            modelValid = false;
            // Вертикальная линия или недостаточно данных
            return;
        }

        slope = (n * sumXY - sumX * sumY) / denominator;
        intercept = (sumY - slope * sumX) / n;
        modelValid = true;
    }

    public double predict(double x) {
        if (!modelValid) {
            throw new IllegalStateException("Модель не инициализирована. Нужно минимум 2 точки.");
        }
        return slope * x + intercept;
    }

    public List<Point2D<Double>> getInliers() {
        return new ArrayList<>(inliers);
    }

    public boolean isModelValid() {
        return modelValid;
    }

    public double getSlope() {
        if (!modelValid) {
            throw new IllegalStateException("Модель не инициализирована.");
        }
        return slope;
    }

    public double getIntercept() {
        if (!modelValid) {
            throw new IllegalStateException("Модель не инициализирована.");
        }
        return intercept;
    }

    public void reset() {
        inliers.clear();
        sumX = 0;
        sumY = 0;
        sumXY = 0;
        sumXX = 0;
        n = 0;
        modelValid = false;
    }

    public LinearFunction2D<Double> getLinearFunction() {
        if (!modelValid) {
            throw new IllegalStateException("Модель не инициализирована.");
        }
        return new LinearFunction2D<>(slope, intercept, ArithmeticOperations.DOUBLE);
    }
}
