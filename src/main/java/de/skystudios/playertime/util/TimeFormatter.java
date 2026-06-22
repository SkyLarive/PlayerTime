package de.skystudios.playertime.util;

/**
 * Wandelt eine Anzahl Sekunden in eine lesbare Angabe wie "2 Tage 3 Stunden 15 Minuten" um.
 */
public final class TimeFormatter {

    private TimeFormatter() {
    }

    public static String format(long totalSeconds) {
        if (totalSeconds <= 0) {
            return "0 Sekunden";
        }

        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append(days == 1 ? " Tag " : " Tage ");
        }
        if (hours > 0) {
            builder.append(hours).append(hours == 1 ? " Stunde " : " Stunden ");
        }
        if (minutes > 0) {
            builder.append(minutes).append(minutes == 1 ? " Minute " : " Minuten ");
        }
        if (seconds > 0 || builder.length() == 0) {
            builder.append(seconds).append(seconds == 1 ? " Sekunde" : " Sekunden");
        }
        return builder.toString().trim();
    }
}
