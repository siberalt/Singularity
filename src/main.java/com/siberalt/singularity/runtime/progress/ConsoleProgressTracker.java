package com.siberalt.singularity.runtime.progress;

public class ConsoleProgressTracker implements ProgressTracker {
    private final int total;
    private int current = 0;
    private final int barWidth;

    ConsoleProgressTracker(int total) {
        this.total = total;
        this.barWidth = 50;
        renderBar(); // render initial empty bar
    }

    public ConsoleProgressTracker(int total, int barWidth) {
        this.total = total;
        this.barWidth = barWidth;
    }

    @Override
    public void advance(int amount) {
        current += amount;
        renderBar();
    }

    private void renderBar() {
        int percent = total > 0 ? (current * 100) / total : 0;
        int filled = (barWidth * current) / total;
        String bar = "#".repeat(filled) + "-".repeat(barWidth - filled);
        System.out.printf("\r[%s] %3d%% (%d/%d)  ", bar, percent, current, total);
        System.out.flush();
        if (current >= total) {
            System.out.println();
        }
    }

    public int current() {
        return current;
    }
}
