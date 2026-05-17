package com.siberalt.singularity.math.median;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public class RobustMedianCalculator implements MedianCalculator {
    @Override
    public double calculateMedian(List<Double> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            throw new IllegalArgumentException("The list of numbers cannot be null or empty");
        }

        List<Double> sortedNumbers = new ArrayList<>(numbers);
        Collections.sort(sortedNumbers);

        int size = sortedNumbers.size();
        double result;

        if (size % 2 == 1) {
            result = sortedNumbers.get(size / 2);
        } else {
            double mid1 = sortedNumbers.get((size / 2) - 1);
            double mid2 = sortedNumbers.get(size / 2);
            result = (mid1 + mid2) / 2.0;
        }

        return result;
    }
}
