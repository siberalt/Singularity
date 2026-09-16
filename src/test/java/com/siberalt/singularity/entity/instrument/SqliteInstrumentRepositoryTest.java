package com.siberalt.singularity.entity.instrument;

import com.siberalt.singularity.broker.contract.service.instrument.common.InstrumentType;
import com.siberalt.singularity.db.initialize.FlywayDatabaseInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteInstrumentRepositoryTest {
    private static final String TINKOFF = "tinkoff";
    private static final String OTHER_BROKER = "another-broker";
    private static final String UID = "e6123145-9665-43e0-8413-cd61b8aa9b13";

    private Connection connection;
    private SqliteInstrumentRepository listings;
    private SqliteCanonicalInstrumentRepository instruments;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        Class.forName("org.sqlite.JDBC");

        String jdbcUrl = "jdbc:sqlite:file:" + UUID.randomUUID() + "?mode=memory&cache=shared";
        connection = DriverManager.getConnection(jdbcUrl);
        new FlywayDatabaseInitializer().migrate(jdbcUrl);

        instruments = new SqliteCanonicalInstrumentRepository(connection);
        listings = new SqliteInstrumentRepository(connection, instruments);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void knowsNothingOfAUidNobodyListed() {
        assertTrue(listings.idOf(UID).isEmpty());
        assertTrue(listings.get(TINKOFF, UID).isEmpty());
    }

    @Test
    void savingAListingCreatesTheInstrumentBehindIt() {
        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));

        long id = listings.idOf(UID).orElseThrow();
        CanonicalInstrument instrument = instruments.get(id).orElseThrow();

        assertEquals("Сбербанк", instrument.getName());
        assertEquals("RU0009029540", instrument.getIsin());
        assertEquals(InstrumentType.SHARE, instrument.getInstrumentType());

        Instrument listing = listings.get(TINKOFF, UID).orElseThrow();
        assertEquals(UID, listing.getUid());
        assertEquals(10, listing.getLot());
        assertEquals("RUB", listing.getCurrency());
        assertEquals("Сбербанк", listing.getName());
    }

    /**
     * Что оставляет после себя миграция уже загруженных свечей: строка, названная по uid, потому что
     * больше в базе ничего о ней не знали. Сохранение того же uid по-настоящему должно заполнить её,
     * а не завести вторую бумагу - иначе свечи останутся висеть на безымянной.
     */
    @Test
    void fillsInAPlaceholderRatherThanCreatingASecondInstrument() {
        listings.save(TINKOFF, new Instrument().setUid(UID).setName(UID).setLot(1).setCurrency("RUB"));
        long placeholder = listings.idOf(UID).orElseThrow();

        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));

        assertEquals(placeholder, listings.idOf(UID).orElseThrow());
        assertEquals("Сбербанк", instruments.get(placeholder).orElseThrow().getName());
        assertEquals(1, instruments.getAll().size());
    }

    /** Обратный путь: по нашему id - то, как бумагу называет конкретный брокер, а не любой. */
    @Test
    void findsWhatEachBrokerCallsTheInstrumentWithOurId() {
        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));
        listings.save(OTHER_BROKER, share("Sberbank", "SBER", "RU0009029540", 1));
        long id = listings.idOf(UID).orElseThrow();

        assertEquals(UID, listings.brokerInstrumentIdOf(TINKOFF, id).orElseThrow());
        assertEquals("SBER", listings.brokerInstrumentIdOf(OTHER_BROKER, id).orElseThrow());
        assertTrue(listings.brokerInstrumentIdOf("unknown-broker", id).isEmpty());
        assertTrue(listings.brokerInstrumentIdOf(TINKOFF, id + 1).isEmpty());
    }

    /** Одна бумага у двух брокеров - один инструмент с двумя листингами, для того ISIN и нужен. */
    @Test
    void recognisesTheSamePaperAtAnotherBrokerByItsIsin() {
        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));
        listings.save(OTHER_BROKER, share("Sberbank", "SBER", "RU0009029540", 1));

        assertEquals(listings.idOf(UID).orElseThrow(), listings.idOf("SBER").orElseThrow());
        assertEquals(1, instruments.getAll().size());

        // Лот - свойство листинга, а не бумаги: у другого брокера он свой.
        assertEquals(10, listings.get(TINKOFF, UID).orElseThrow().getLot());
        assertEquals(1, listings.get(OTHER_BROKER, "SBER").orElseThrow().getLot());
    }

    @Test
    void savingTheSameListingAgainUpdatesItInPlace() {
        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));
        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 100));

        assertEquals(100, listings.get(TINKOFF, UID).orElseThrow().getLot());
        assertEquals(List.of("Сбербанк"), listings.getAll(TINKOFF).stream().map(Instrument::getName).toList());
    }

    @Test
    void listsWhatEachBrokerTradesAndOfWhichKind() {
        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));
        listings.save(TINKOFF, new Instrument()
            .setName("Тинькофф iMOEX")
            .setUid("TMOS")
            .setLot(1)
            .setCurrency("RUB")
            .setInstrumentType(InstrumentType.ETF));
        // Другая бумага, а не та же у другого брокера - иначе имя у них общее, оно у бумаги одно.
        listings.save(OTHER_BROKER, share("Лукойл", "LKOH", "RU0009024277", 1));

        assertEquals(2, listings.getAll(TINKOFF).size());
        assertEquals(1, listings.getAll(OTHER_BROKER).size());
        assertEquals(List.of("Сбербанк"), namesOf(listings.getByType(TINKOFF, InstrumentType.SHARE)));
        assertEquals(List.of("Тинькофф iMOEX"), namesOf(listings.getByType(TINKOFF, InstrumentType.ETF)));
    }

    /** Снятие листинга - не исчезновение бумаги: свечи хранятся против неё, а не против брокера. */
    @Test
    void deletingAListingLeavesTheInstrumentStanding() {
        listings.save(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));
        long id = listings.idOf(UID).orElseThrow();

        listings.delete(TINKOFF, share("Сбербанк", UID, "RU0009029540", 10));

        assertTrue(listings.get(TINKOFF, UID).isEmpty());
        assertTrue(listings.idOf(UID).isEmpty());
        assertTrue(instruments.get(id).isPresent());
    }

    @Test
    void refusesAListingWithNoUidToStandUnder() {
        assertThrows(IllegalArgumentException.class,
            () -> listings.save(TINKOFF, new Instrument().setName("Безымянный")));
        assertThrows(IllegalArgumentException.class, () -> listings.save(null, share("X", UID, null, 1)));
    }

    private List<String> namesOf(Iterable<Instrument> listed) {
        List<String> names = new java.util.ArrayList<>();

        listed.forEach(instrument -> names.add(instrument.getName()));

        return names;
    }

    private Instrument share(String name, String uid, String isin, int lot) {
        return new Instrument()
            .setName(name)
            .setUid(uid)
            .setIsin(isin)
            .setLot(lot)
            .setCurrency("RUB")
            .setInstrumentType(InstrumentType.SHARE);
    }
}
