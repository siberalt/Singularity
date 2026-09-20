package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.utils.entity.CandleMigrationCheckpointRepository;
import com.siberalt.singularity.utils.entity.CandleMigrationResult;
import com.siberalt.singularity.utils.entity.CandleMigrationService;
import com.siberalt.singularity.runtime.progress.NullProgressTrackerFactory;
import com.siberalt.singularity.shared.TimePointRange;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandleMigrationServiceTest {

    @Mock
    private ReadCandleRepository source;

    @Mock
    private WriteCandleRepository target;

    @Mock
    private CandleMigrationCheckpointRepository checkpoint;

    private CandleMigrationService service;

    private static final Instant FIXED_FROM = Instant.parse("2025-01-01T10:00:00Z");
    private static final Instant FIXED_TO = Instant.parse("2025-01-01T10:16:40Z");
    private static final Instant CANDLE_TIME_1 = Instant.parse("2025-01-01T10:00:00Z");
    private static final Instant CANDLE_TIME_2 = Instant.parse("2025-01-01T10:01:00Z");
    private static final Instant LONG_AGO = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant LONG_AGO_PLUS_8_DAYS = Instant.parse("2025-01-09T00:00:00Z");

    @BeforeEach
    void setUp() {
        service = CandleMigrationService.builder(source, target)
            .parallelism(2)
            .chunkSizeDays(7)
            .build();
    }

    @Nested
    class MigrateInstrumentsEmptyOrNoData {

        @Test
        void emptyInstrumentList() {
            // given

            // when
            service.migrateInstruments(Collections.emptyList(), FIXED_FROM, FIXED_TO);

            // then
            verifyNoInteractions(source);
            verifyNoInteractions(target);
        }

        @Test
        void instrumentWithNoCandles() {
            // given
            long instrumentId = 1;

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(CandleRangeMetadata.EMPTY);

            // when
            service.migrateInstruments(List.of(instrumentId), FIXED_FROM, FIXED_TO);

            // then
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verifyNoMoreInteractions(source);
            verifyNoInteractions(target);
        }
    }

    @Nested
    class MigrateInstrumentsSingleInstrument {

        @Test
        void withCandles() {
            // given
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle candle1 = Candle.of(CANDLE_TIME_1, 100, 100.0);
            Candle candle2 = Candle.of(CANDLE_TIME_2, 200, 101.0);
            List<Candle> candles = List.of(candle1, candle2);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), candles.size()));
            when(source.getPeriod(eq(instrumentId), any(), any())).thenReturn(candles);

            // when
            service.migrateInstruments(List.of(instrumentId), from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verify(source).getPeriod(eq(instrumentId), any(), any());
            verify(target).saveBatch(candles);
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void singleChunkSmallRange() {
            // given
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle candle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getPeriod(eq(instrumentId), any(), any())).thenReturn(List.of(candle));

            // when
            service.migrateInstruments(List.of(instrumentId), from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verify(source).getPeriod(eq(instrumentId), any(), any());
            verify(target).saveBatch(List.of(candle));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }
    }

    @Nested
    class MigrateInstrumentsMultipleInstruments {

        @Test
        void twoInstruments() {
            // given
            long instrumentUid1 = 1;
            long instrumentUid2 = 2;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle candle1 = Candle.of(CANDLE_TIME_1, 100, 100.0);
            Candle candle2 = Candle.of(CANDLE_TIME_2, 200, 101.0);

            when(source.getRangeMetadata(eq(instrumentUid1), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getRangeMetadata(eq(instrumentUid2), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getPeriod(eq(instrumentUid1), any(), any())).thenReturn(List.of(candle1));
            when(source.getPeriod(eq(instrumentUid2), any(), any())).thenReturn(List.of(candle2));

            // when
            service.migrateInstruments(List.of(instrumentUid1, instrumentUid2), from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid1), any(), any());
            verify(source).getRangeMetadata(eq(instrumentUid2), any(), any());
            verify(source).getPeriod(eq(instrumentUid1), any(), any());
            verify(source).getPeriod(eq(instrumentUid2), any(), any());
            verify(target).saveBatch(List.of(candle1));
            verify(target).saveBatch(List.of(candle2));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void parallelProcessing() {
            // given
            long inst1 = 1;
            long inst2 = 2;
            long inst3 = 3;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle c1 = Candle.of(CANDLE_TIME_1, 100, 100.0);
            Candle c2 = Candle.of(CANDLE_TIME_1, 200, 101.0);
            Candle c3 = Candle.of(CANDLE_TIME_1, 300, 102.0);

            when(source.getRangeMetadata(eq(inst1), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getRangeMetadata(eq(inst2), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getRangeMetadata(eq(inst3), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getPeriod(eq(inst1), any(), any())).thenReturn(List.of(c1));
            when(source.getPeriod(eq(inst2), any(), any())).thenReturn(List.of(c2));
            when(source.getPeriod(eq(inst3), any(), any())).thenReturn(List.of(c3));

            // when
            service.migrateInstruments(List.of(inst1, inst2, inst3), from, to);

            // then
            verify(source).getRangeMetadata(eq(inst1), any(), any());
            verify(source).getRangeMetadata(eq(inst2), any(), any());
            verify(source).getRangeMetadata(eq(inst3), any(), any());
            verify(source).getPeriod(eq(inst1), any(), any());
            verify(source).getPeriod(eq(inst2), any(), any());
            verify(source).getPeriod(eq(inst3), any(), any());
            verify(target).saveBatch(List.of(c1));
            verify(target).saveBatch(List.of(c2));
            verify(target).saveBatch(List.of(c3));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void mixedSuccessAndFailure() {
            // given
            long okInstrument = 1;
            long failInstrument = 2;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle okCandle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(okInstrument), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getRangeMetadata(eq(failInstrument), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getPeriod(eq(okInstrument), any(), any()))
                .thenReturn(List.of(okCandle));
            when(source.getPeriod(eq(failInstrument), any(), any()))
                .thenThrow(new RuntimeException("Migration failed"));

            // when
            service.migrateInstruments(List.of(okInstrument, failInstrument), from, to);

            // then
            verify(source).getRangeMetadata(eq(okInstrument), any(), any());
            verify(source).getRangeMetadata(eq(failInstrument), any(), any());
            verify(source).getPeriod(eq(okInstrument), any(), any());
            verify(source).getPeriod(eq(failInstrument), any(), any());
            verify(target).saveBatch(List.of(okCandle));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }
    }

    @Nested
    class MigrateInstrumentsErrorHandling {

        @Test
        void chunkProcessingError() {
            // given
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle firstCandle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 2));
            when(source.getPeriod(eq(instrumentId), any(), any()))
                .thenReturn(List.of(firstCandle))
                .thenThrow(new RuntimeException("Test error"));

            // when
            service.migrateInstruments(List.of(instrumentId), from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verify(source).getPeriod(eq(instrumentId), any(), any());
            verify(target).saveBatch(List.of(firstCandle));
        }
    }

    @Nested
    class MigrateInstrumentSingle {

        @Test
        void singleChunk() {
            // given
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle candle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getPeriod(eq(instrumentId), any(), any())).thenReturn(List.of(candle));

            // when
            CandleMigrationResult result = service.migrateInstrument(instrumentId, from, to);

            // then
            Assertions.assertTrue(result.isComplete());
            Assertions.assertEquals(1, result.totalChunks());
            Assertions.assertEquals(1, result.savedCandles());
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verify(source).getPeriod(eq(instrumentId), any(), any());
            verify(target).saveBatch(List.of(candle));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void multipleChunks() {
            // given
            long instrumentId = 1;
            Instant from = LONG_AGO;
            Instant to = LONG_AGO_PLUS_8_DAYS;

            Candle candle1 = Candle.of(LONG_AGO, 100, 100.0);
            Candle candle2 = Candle.of(LONG_AGO_PLUS_8_DAYS, 200, 101.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 2));
            when(source.getPeriod(eq(instrumentId), any(), any()))
                .thenReturn(List.of(candle1))
                .thenReturn(List.of(candle2));

            // when
            service.migrateInstrument(instrumentId, from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verify(source, times(2)).getPeriod(eq(instrumentId), any(), any());
            verify(target).saveBatch(List.of(candle1));
            verify(target).saveBatch(List.of(candle2));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void errorOnSpecificChunk() {
            // given
            long instrumentId = 1;
            Instant from = LONG_AGO;
            Instant to = LONG_AGO_PLUS_8_DAYS;

            Candle candle1 = Candle.of(LONG_AGO_PLUS_8_DAYS, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 2));
            when(source.getPeriod(eq(instrumentId), any(), any()))
                .thenReturn(List.of(candle1))
                .thenThrow(new RuntimeException("Error chunk"));

            // when
            CandleMigrationResult result = service.migrateInstrument(instrumentId, from, to);

            // then
            // The failed chunk is not an exception to the caller - it is reported, so the caller can
            // tell a load with a hole from a complete one and run again for what is missing.
            Assertions.assertFalse(result.isComplete());
            Assertions.assertEquals(2, result.totalChunks());
            Assertions.assertEquals(1, result.failedChunks().size());
            Assertions.assertEquals(1, result.savedCandles());
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verify(source, times(2)).getPeriod(eq(instrumentId), any(), any());
            verify(target).saveBatch(List.of(candle1));
        }
    }

    /**
     * Сеть до брокера пропадает на секунды, а лимит запросов выдаётся на окно времени: и то и другое
     * проходит само, если немного подождать и попросить ещё раз. Без повторов такой чанк доставался
     * следующему запуску целиком - а их бывает по полторы тысячи на инструмент.
     */
    @Nested
    class MigrateInstrumentWithRetries {

        @Test
        void takesTheChunkOnTheSecondTry() {
            long instrumentId = 1;
            Candle candle = Candle.of(LONG_AGO, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(LONG_AGO, LONG_AGO_PLUS_8_DAYS), 1));
            when(source.getPeriod(eq(instrumentId), any(), any()))
                .thenThrow(new RuntimeException("network blinked"))
                .thenReturn(List.of(candle));

            CandleMigrationResult result = retrying(2).migrateInstrument(instrumentId, LONG_AGO, LONG_AGO_PLUS_8_DAYS);

            Assertions.assertTrue(result.isComplete());
            Assertions.assertEquals(1, result.savedCandles());
            verify(source, times(2)).getPeriod(eq(instrumentId), any(), any());
            verify(target).saveBatch(List.of(candle));
        }

        @Test
        void givesUpAfterTheLastAttemptAndSaysSo() {
            long instrumentId = 1;

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(LONG_AGO, LONG_AGO_PLUS_8_DAYS), 1));
            when(source.getPeriod(eq(instrumentId), any(), any()))
                .thenThrow(new RuntimeException("still down"));

            CandleMigrationResult result = retrying(2).migrateInstrument(instrumentId, LONG_AGO, LONG_AGO_PLUS_8_DAYS);

            // Three attempts: the first one and the two retries it was allowed.
            Assertions.assertFalse(result.isComplete());
            Assertions.assertEquals(1, result.failedChunks().size());
            verify(source, times(3)).getPeriod(eq(instrumentId), any(), any());
            verify(target, never()).saveBatch(any());
        }

        @Test
        void withoutRetriesItBehavesAsBefore() {
            long instrumentId = 1;

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(LONG_AGO, LONG_AGO_PLUS_8_DAYS), 1));
            when(source.getPeriod(eq(instrumentId), any(), any()))
                .thenThrow(new RuntimeException("down"));

            CandleMigrationResult result = retrying(0).migrateInstrument(instrumentId, LONG_AGO, LONG_AGO_PLUS_8_DAYS);

            Assertions.assertEquals(1, result.failedChunks().size());
            verify(source, times(1)).getPeriod(eq(instrumentId), any(), any());
        }

        /**
         * A refusal sets a pause, and the next attempt waits it out. What the pause cannot do is call
         * back a request already on its way - the chunks that were in flight when the quota ran out
         * still get their answer - so the guarantee is about attempts that have not started yet.
         */
        @Test
        void waitsOutThePauseBeforeAskingAgain() {
            long instrumentId = 1;
            List<Instant> attempts = Collections.synchronizedList(new java.util.ArrayList<>());

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(LONG_AGO, LONG_AGO_PLUS_8_DAYS), 1));
            when(source.getPeriod(eq(instrumentId), any(), any())).thenAnswer(invocation -> {
                attempts.add(Instant.now());

                if (attempts.size() == 1) {
                    throw new RuntimeException("quota gone");
                }

                return List.of();
            });

            CandleMigrationService service = CandleMigrationService.builder(source, target)
                .progressTrackerFactory(new NullProgressTrackerFactory())
                .chunkSizeDays(8)
                .retries(1)
                .retryBackoff(Duration.ofMillis(300))
                .build();

            Assertions.assertTrue(service.migrateInstrument(instrumentId, LONG_AGO, LONG_AGO_PLUS_8_DAYS).isComplete());
            Assertions.assertEquals(2, attempts.size());
            Assertions.assertFalse(attempts.get(1).isBefore(attempts.get(0).plusMillis(250)),
                "the retry came " + Duration.between(attempts.get(0), attempts.get(1)) + " after the refusal");
        }

        private CandleMigrationService retrying(int retries) {
            return CandleMigrationService.builder(source, target)
                .progressTrackerFactory(new NullProgressTrackerFactory())
                .chunkSizeDays(8)
                .retries(retries)
                .retryBackoff(Duration.ofMillis(1))
                .build();
        }
    }

    @Nested
    class MigrateInstrumentWithCheckpoint {

        @Test
        void skipsChunksAlreadyMarkedDone() {
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(checkpoint.isDone(eq(instrumentId), any())).thenReturn(true);

            CandleMigrationService serviceWithCheckpoint = CandleMigrationService.builder(source, target)
                .progressTrackerFactory(new NullProgressTrackerFactory())
                .chunkSizeDays(7)
                .checkpoint(checkpoint)
                .build();

            CandleMigrationResult result = serviceWithCheckpoint.migrateInstrument(instrumentId, from, to);

            Assertions.assertTrue(result.isComplete());
            Assertions.assertEquals(1, result.skippedChunks());
            Assertions.assertEquals(0, result.savedCandles());
            verify(source).getRangeMetadata(eq(instrumentId), any(), any());
            verify(checkpoint).isDone(eq(instrumentId), any());
            verifyNoMoreInteractions(source);
            verifyNoInteractions(target);
        }

        @Test
        void marksChunkDoneAfterSuccessfulSave() {
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;
            Candle candle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(checkpoint.isDone(eq(instrumentId), any())).thenReturn(false);
            when(source.getPeriod(eq(instrumentId), any(), any())).thenReturn(List.of(candle));

            CandleMigrationService serviceWithCheckpoint = CandleMigrationService.builder(source, target)
                .progressTrackerFactory(new NullProgressTrackerFactory())
                .chunkSizeDays(7)
                .checkpoint(checkpoint)
                .build();

            serviceWithCheckpoint.migrateInstrument(instrumentId, from, to);

            verify(target).saveBatch(List.of(candle));
            verify(checkpoint).markDone(eq(instrumentId), any());
        }

        @Test
        void doesNotMarkChunkDoneOnFailure() {
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(checkpoint.isDone(eq(instrumentId), any())).thenReturn(false);
            when(source.getPeriod(eq(instrumentId), any(), any()))
                .thenThrow(new RuntimeException("boom"));

            CandleMigrationService serviceWithCheckpoint = CandleMigrationService.builder(source, target)
                .progressTrackerFactory(new NullProgressTrackerFactory())
                .chunkSizeDays(7)
                .checkpoint(checkpoint)
                .build();

            serviceWithCheckpoint.migrateInstrument(instrumentId, from, to);

            verify(checkpoint, never()).markDone(eq(instrumentId), any());
        }
    }

    @Nested
    class MigrateInstrumentWriteFailure {

        @Test
        void writeFailurePropagatesInsteadOfBeingSwallowed() {
            // Ошибка при чтении (сеть) - ожидаемая помеха, чанк можно тихо повторить.
            // Ошибка при записи (например, рассинхронизация схемы БД) - структурная
            // проблема, которая будет повторяться на каждом чанке одинаково, поэтому
            // должна прерывать миграцию, а не тихо проглатываться.
            long instrumentId = 1;
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;
            Candle candle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentId), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(checkpoint.isDone(eq(instrumentId), any())).thenReturn(false);
            when(source.getPeriod(eq(instrumentId), any(), any())).thenReturn(List.of(candle));
            doThrow(new RuntimeException("SQLITE_ERROR: constraint mismatch"))
                .when(target).saveBatch(List.of(candle));

            CandleMigrationService serviceWithCheckpoint = CandleMigrationService.builder(source, target)
                .progressTrackerFactory(new NullProgressTrackerFactory())
                .chunkSizeDays(7)
                .checkpoint(checkpoint)
                .build();

            Assertions.assertThrows(CompletionException.class,
                () -> serviceWithCheckpoint.migrateInstrument(instrumentId, from, to));

            verify(checkpoint, never()).markDone(eq(instrumentId), any());
        }
    }
}
