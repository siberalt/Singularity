package com.siberalt.singularity.math.median;

public class RobustMedianCalculator implements MedianCalculator {
    @Override
    public double calculateMedian(java.util.List<Double> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            throw new IllegalArgumentException("The list of numbers cannot be null or empty");
        }

        java.util.List<Double> sortedNumbers = new java.util.ArrayList<>(numbers);
        java.util.Collections.sort(sortedNumbers);

        int size = sortedNumbers.size();
        if (size % 2 == 1) {
            return sortedNumbers.get(size / 2);
        } else {
            double mid1 = sortedNumbers.get((size / 2) - 1);
            double mid2 = sortedNumbers.get(size / 2);
            return (mid1 + mid2) / 2.0;
        }
    }
}
