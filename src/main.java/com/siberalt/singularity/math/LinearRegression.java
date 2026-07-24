package com.siberalt.singularity.math;

public class LinearRegression {
    private final double slope;
    private final double intercept;
    private final double r2;

    public LinearRegression(double[] x, double[] y) {
        if (x.length != y.length) {
            throw new IllegalArgumentException("Массивы x и y должны иметь одинаковую длину");
        }
        if (x.length < 2) {
            throw new IllegalArgumentException("Массивы должны содержать минимум 2 элемента");
        }

        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }
        
        double meanX = sumX / n;
        double meanY = sumY / n;
        
        double denominator = (n * sumX2 - sumX * sumX);
        if (Math.abs(denominator) < 1e-10) {
            this.slope = 0;
            this.intercept = meanY;
            this.r2 = 0;
            return;
        }
        
        this.slope = (n * sumXY - sumX * sumY) / denominator;
        this.intercept = meanY - slope * meanX;

        // Вычисляем R²
        double ssTot = 0, ssReg = 0;
        for (int i = 0; i < n; i++) {
            double yPred = slope * x[i] + intercept;
            ssTot += (y[i] - meanY) * (y[i] - meanY);
            ssReg += (yPred - meanY) * (yPred - meanY);
        }
        this.r2 = (ssTot > 0) ? ssReg / ssTot : 0;
    }

    public double getSlope() {
        return slope;
    }

    public double getIntercept() {
        return intercept;
    }

    public double getR2() {
        return r2;
    }

    public double predict(double x) {
        return slope * x + intercept;
    }
}
