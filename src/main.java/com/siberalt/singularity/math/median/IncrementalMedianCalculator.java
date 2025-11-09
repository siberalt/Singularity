package com.siberalt.singularity.math.median;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class IncrementalMedianCalculator implements MedianCalculator {
    private final PriorityQueue<Double> lowerHalf;
    private final PriorityQueue<Double> upperHalf;

    public IncrementalMedianCalculator() {
        // Максимальная куча для нижней половины
        lowerHalf = new PriorityQueue<>(Comparator.reverseOrder());
        // Минимальная куча для верхней половины
        upperHalf = new PriorityQueue<>();
    }

    public double calculateMedian(List<Double> numbers) {
        for (double price : numbers) {
            addPrice(price);
        }
        return getMedian();
    }

    public void addPrice(double price) {
        // Добавляем в соответствующую кучу
        if (lowerHalf.isEmpty() || price <= lowerHalf.peek()) {
            lowerHalf.offer(price);
        } else {
            upperHalf.offer(price);
        }

        // Балансируем кучи
        rebalance();
    }

    public double getMedian() {
        if (lowerHalf.isEmpty() && upperHalf.isEmpty()) {
            throw new IllegalStateException("No prices added");
        }

        if (lowerHalf.size() == upperHalf.size()) {
            return (lowerHalf.peek() + upperHalf.peek()) / 2.0;
        } else {
            return lowerHalf.peek();
        }
    }

    private void rebalance() {
        while (lowerHalf.size() > upperHalf.size() + 1) {
            upperHalf.offer(lowerHalf.poll());
        }

        while (upperHalf.size() > lowerHalf.size()) {
            lowerHalf.offer(upperHalf.poll());
        }
    }

    public void removePrice(double price) {
        if (lowerHalf.remove(price) || upperHalf.remove(price)) {
            rebalance();
        }
    }

    public int size() {
        return lowerHalf.size() + upperHalf.size();
    }
}
