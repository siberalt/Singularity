import java.time.Duration;

public class TimeSpendCalculator {
    public static void main(String[] args) {
        String[] spendsPerMonth = {
            // 2024
            "25:00", // May
            "24:02", // Jun
            "26:16", // Jul
            "24:01", // Aug
            "13:50", // Sep
            "6:59", // Oct
            "15:54", // Nov
            "19:13", // Dec

            // 2025
            "21:12", // Jan
        };

        Duration total = calculateTimeSpend(spendsPerMonth);

        System.out.printf("Total time spend: %s:%s", total.toHours(), total.toMinutesPart());
    }

    public static Duration calculateTimeSpend(String[] spendsPerMonth) {
        Duration total = Duration.ZERO;
        for (String spend : spendsPerMonth) {
            String[] parts = spend.split(":");
            total = total.plusHours(Long.parseLong(parts[0]));
            total = total.plusMinutes(Long.parseLong(parts[1]));
        }
        return total;
    }
}
