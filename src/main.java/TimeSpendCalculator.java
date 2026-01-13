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
            "25:23", // Jan
            "10:52", // Feb
            "22:37", // Mar
            "21:06", // Apr
            "25:41", // May
            "12:46", // Jun
            "20:50", // Jul
            "28:09", // Aug
            "22:44", // Sep
            "20:16", // Oct
            "21:29", // Nov
            "18:21", // Dec

            // 2026
            "8:05", // Jan
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
