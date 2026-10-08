package com.siberalt.singularity.strategy.market;

import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.DividendInstrumentService;
import com.siberalt.singularity.broker.contract.service.instrument.common.Dividend;
import com.siberalt.singularity.broker.contract.service.exception.ErrorCode;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetDividendsRequest;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetRequest;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetDividendsResponse;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetResponse;
import com.siberalt.singularity.broker.contract.service.instrument.response.GetTradableResponse;
import com.siberalt.singularity.broker.contract.value.money.Money;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Календарь от брокера: кэш, пересprос по свежести и громкий отказ. */
class BrokerDividendCalendarTest {
    private static final long MINE = 7;
    private static final String UID = "e6123145-9665-43e0-8413-cd61b8aa9b13";
    private static final Instant LAST_BUY = Instant.parse("2024-06-10T00:00:00Z");
    private static final Instant DECLARED = Instant.parse("2024-05-01T12:00:00Z");

    /** Служба, которая считает запросы и помнит, о чём спрашивали. */
    private static final class Counting implements DividendInstrumentService {
        private final List<GetDividendsRequest> asked = new ArrayList<>();
        private List<Dividend> answer;
        private AbstractException failure;

        Counting(List<Dividend> answer) {
            this.answer = answer;
        }

        @Override
        public GetDividendsResponse getDividends(GetDividendsRequest request) throws AbstractException {
            asked.add(request);

            if (failure != null) {
                throw failure;
            }

            return new GetDividendsResponse().setDividends(answer);
        }

        // Остальное контракта календарю не нужно, и подменять его тут нечем.
        @Override
        public GetResponse get(GetRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public GetTradableResponse getTradable(GetTradableRequest request) {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void asksTheBrokerOnceAndThenServesFromTheCache() {
        Counting service = new Counting(List.of(dividend(DECLARED, LAST_BUY)));
        DividendCalendar calendar = new BrokerDividendCalendar(service, id -> UID);

        assertEquals(LAST_BUY, calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T10:00:00Z")));
        assertEquals(LAST_BUY, calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T18:00:00Z")));

        assertEquals(1, service.asked.size());
    }

    /**
     * Срок свежести - не оптимизация, а защита: объявления приходят со временем, и снимок, снятый однажды,
     * не узнает о дивиденде, объявленном позже.
     */
    @Test
    void asksAgainOnceTheAnswerIsStale() {
        Counting service = new Counting(List.of());
        DividendCalendar calendar = new BrokerDividendCalendar(service, id -> UID,
            Duration.ofDays(400), Duration.ofDays(1));

        assertNull(calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T10:00:00Z")));

        // За сутки брокер объявил дивиденд, которого в первом ответе не было.
        service.answer = List.of(dividend(DECLARED, LAST_BUY));

        assertNull(calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T20:00:00Z")),
            "в пределах свежести ответ прежний");
        assertEquals(LAST_BUY, calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-03T10:00:00Z")));
        assertEquals(2, service.asked.size());
    }

    /** Время идёт по барам, а не по системным часам, поэтому и ход назад считается несвежестью. */
    @Test
    void asksAgainWhenTheClockGoesBackwards() {
        Counting service = new Counting(List.of(dividend(DECLARED, LAST_BUY)));
        DividendCalendar calendar = new BrokerDividendCalendar(service, id -> UID);

        calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T10:00:00Z"));
        calendar.nextLastBuyDate(MINE, Instant.parse("2024-05-01T10:00:00Z"));

        assertEquals(2, service.asked.size());
    }

    /** Запрос идёт с запасом назад и на горизонт вперёд - краем диапазона отсечку терять нельзя. */
    @Test
    void asksWithARangeAroundTheMoment() {
        Counting service = new Counting(List.of());
        Instant asOf = Instant.parse("2024-06-01T10:00:00Z");

        new BrokerDividendCalendar(service, id -> UID, Duration.ofDays(400), Duration.ofDays(1))
            .nextLastBuyDate(MINE, asOf);

        GetDividendsRequest asked = service.asked.getFirst();

        assertEquals(UID, asked.getInstrumentUid());
        assertTrue(asked.getFrom().isBefore(asOf), "запас назад: " + asked.getFrom());
        assertEquals(asOf.plus(Duration.ofDays(400)), asked.getTo());
    }

    /** Каждая бумага кэшируется отдельно, и спрашивают про её собственный uid. */
    @Test
    void keepsTheInstrumentsApart() {
        Counting service = new Counting(List.of(dividend(DECLARED, LAST_BUY)));
        DividendCalendar calendar = new BrokerDividendCalendar(service, id -> "uid-" + id);
        Instant asOf = Instant.parse("2024-06-01T10:00:00Z");

        calendar.nextLastBuyDate(MINE, asOf);
        calendar.nextLastBuyDate(36, asOf);
        calendar.nextLastBuyDate(MINE, asOf);

        assertEquals(2, service.asked.size());
        assertEquals("uid-7", service.asked.get(0).getInstrumentUid());
        assertEquals("uid-36", service.asked.get(1).getInstrumentUid());
    }

    /** Бумагу, которой у брокера нет, не спрашивают вовсе. */
    @Test
    void doesNotAskAboutAnInstrumentWithoutAUid() {
        Counting service = new Counting(List.of());

        assertNull(new BrokerDividendCalendar(service, id -> null)
            .nextLastBuyDate(MINE, Instant.parse("2024-06-01T10:00:00Z")));
        assertEquals(0, service.asked.size());
    }

    /**
     * Отказ брокера слышен. Пустой ответ означал бы «отсечек нет», и правило проехало бы отсечку молча - в
     * логе это выглядело бы как «дивидендов не было».
     */
    @Test
    void failsLoudlyRatherThanReportingNoDividends() {
        Counting service = new Counting(List.of());

        service.failure = new AbstractException(ErrorCode.UNAVAILABLE, "брокер недоступен") {
        };

        DividendCalendar calendar = new BrokerDividendCalendar(service, id -> UID);

        assertThrows(IllegalStateException.class,
            () -> calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T10:00:00Z")));
    }

    /** Правила отбора остались в снимке: о необъявленном дивиденде календарь не знает и здесь. */
    @Test
    void keepsTheAnnouncementRule() {
        Counting service = new Counting(List.of(dividend(DECLARED, LAST_BUY)));
        DividendCalendar calendar = new BrokerDividendCalendar(service, id -> UID);

        assertNull(calendar.nextLastBuyDate(MINE, Instant.parse("2024-04-20T10:00:00Z")));
        assertEquals(LAST_BUY, calendar.nextLastBuyDate(MINE, Instant.parse("2024-06-01T10:00:00Z")));
    }

    @Test
    void refusesWhatCannotBeComputed() {
        Counting service = new Counting(List.of());

        assertThrows(IllegalArgumentException.class,
            () -> new BrokerDividendCalendar(null, id -> UID));
        assertThrows(IllegalArgumentException.class,
            () -> new BrokerDividendCalendar(service, null));
        assertThrows(IllegalArgumentException.class,
            () -> new BrokerDividendCalendar(service, id -> UID, Duration.ZERO, Duration.ofDays(1)));
        assertThrows(IllegalArgumentException.class,
            () -> new BrokerDividendCalendar(service, id -> UID, Duration.ofDays(400),
                Duration.ofDays(-1)));
    }

    private static Dividend dividend(Instant declared, Instant lastBuy) {
        return new Dividend()
            .setDeclaredDate(declared)
            .setLastBuyDate(lastBuy)
            .setDividendNet(Money.of("RUB", 10.0));
    }
}
