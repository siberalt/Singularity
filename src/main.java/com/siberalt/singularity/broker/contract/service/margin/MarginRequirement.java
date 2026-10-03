package com.siberalt.singularity.broker.contract.service.margin;

/**
 * How much of a position's value the broker wants covered by the client's own money.
 * <p>
 * Four numbers, because two of them answer different questions. The <b>initial</b> rate decides whether an
 * order may be placed at all; the <b>maintenance</b> rate decides whether a position already open is still
 * allowed to stand. They differ on purpose - a broker lets a position it would not open today continue to
 * exist - and a model that collapses them into one either refuses trades it should allow or liquidates
 * positions it should not.
 * <p>
 * Long and short are separate because they are not mirror images. A short can lose without limit, so its
 * rate is normally the higher of the two, and on an instrument a broker will not lend it is one: the
 * position must be fully covered, which in practice means it cannot be opened.
 * <p>
 * These are the {@code dlong}, {@code dshort}, {@code dlong_min} and {@code dshort_min} of the T-Invest
 * API, in the same units: a share of the position's value, so 0.2 means a fifth.
 *
 * @param initialLong      own money needed to open a long, as a share of its value
 * @param initialShort     own money needed to open a short
 * @param maintenanceLong  own money needed to keep a long open
 * @param maintenanceShort own money needed to keep a short open
 */
public record MarginRequirement(double initialLong, double initialShort, double maintenanceLong,
                                double maintenanceShort) {
    /** Everything covered in full - no leverage, no borrowing, which is how a cash account behaves. */
    public static final MarginRequirement CASH = new MarginRequirement(1, 1, 1, 1);

    public MarginRequirement {
        if (initialLong <= 0 || initialShort <= 0 || maintenanceLong <= 0 || maintenanceShort <= 0) {
            throw new IllegalArgumentException("Ставки риска должны быть положительными");
        }

        // Единица - не предел. Брокер может требовать больше полной стоимости позиции, и для шорта это
        // обычное дело: у «Эталон Груп» dshort = 1.1, то есть шорт разрешён, но обеспечения нужно больше,
        // чем он стоит. Первая версия этой проверки такие бумаги отвергала, и нашли это настоящие ставки.
        if (maintenanceLong > initialLong || maintenanceShort > initialShort) {
            throw new IllegalArgumentException(
                "Поддерживающая ставка не может быть строже начальной: получено "
                    + maintenanceLong + " против " + initialLong + " в лонг и "
                    + maintenanceShort + " против " + initialShort + " в шорт");
        }
    }

    /** The same rate everywhere - the simplest thing that still has leverage in it. */
    public static MarginRequirement of(double rate) {
        return new MarginRequirement(rate, rate, rate, rate);
    }

    public double initial(boolean isLong) {
        return isLong ? initialLong : initialShort;
    }

    public double maintenance(boolean isLong) {
        return isLong ? maintenanceLong : maintenanceShort;
    }
}
