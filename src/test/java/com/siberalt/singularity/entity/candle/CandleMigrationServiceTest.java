package com.siberalt.singularity.entity.candle;

import com.siberalt.singularity.utils.entity.CandleMigrationCheckpointRepository;
import com.siberalt.singularity.utils.entity.CandleMigrationService;
import com.siberalt.singularity.runtime.progress.NullProgressTrackerFactory;
import com.siberalt.singularity.shared.TimePointRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

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
        service = new CandleMigrationService(source, target, 2, 7);
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
            String instrumentUid = "TEST_INSTRUMENT";

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(CandleRangeMetadata.EMPTY);

            // when
            service.migrateInstruments(List.of(instrumentUid), FIXED_FROM, FIXED_TO);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verifyNoMoreInteractions(source);
            verifyNoInteractions(target);
        }
    }

    @Nested
    class MigrateInstrumentsSingleInstrument {

        @Test
        void withCandles() {
            // given
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle candle1 = Candle.of(CANDLE_TIME_1, 100, 100.0);
            Candle candle2 = Candle.of(CANDLE_TIME_2, 200, 101.0);
            List<Candle> candles = List.of(candle1, candle2);

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), candles.size()));
            when(source.getPeriod(eq(instrumentUid), any(), any())).thenReturn(candles);

            // when
            service.migrateInstruments(List.of(instrumentUid), from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verify(source).getPeriod(eq(instrumentUid), any(), any());
            verify(target).saveBatch(candles);
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void singleChunkSmallRange() {
            // given
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle candle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getPeriod(eq(instrumentUid), any(), any())).thenReturn(List.of(candle));

            // when
            service.migrateInstruments(List.of(instrumentUid), from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verify(source).getPeriod(eq(instrumentUid), any(), any());
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
            String instrumentUid1 = "TEST_INSTRUMENT_1";
            String instrumentUid2 = "TEST_INSTRUMENT_2";
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
            String inst1 = "INST_1";
            String inst2 = "INST_2";
            String inst3 = "INST_3";
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
            String okInstrument = "OK";
            String failInstrument = "FAIL";
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
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle firstCandle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 2));
            when(source.getPeriod(eq(instrumentUid), any(), any()))
                .thenReturn(List.of(firstCandle))
                .thenThrow(new RuntimeException("Test error"));

            // when
            service.migrateInstruments(List.of(instrumentUid), from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verify(source).getPeriod(eq(instrumentUid), any(), any());
            verify(target).saveBatch(List.of(firstCandle));
        }
    }

    @Nested
    class MigrateInstrumentSingle {

        @Test
        void singleChunk() {
            // given
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            Candle candle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(source.getPeriod(eq(instrumentUid), any(), any())).thenReturn(List.of(candle));

            // when
            service.migrateInstrument(instrumentUid, from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verify(source).getPeriod(eq(instrumentUid), any(), any());
            verify(target).saveBatch(List.of(candle));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void multipleChunks() {
            // given
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = LONG_AGO;
            Instant to = LONG_AGO_PLUS_8_DAYS;

            Candle candle1 = Candle.of(LONG_AGO, 100, 100.0);
            Candle candle2 = Candle.of(LONG_AGO_PLUS_8_DAYS, 200, 101.0);

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 2));
            when(source.getPeriod(eq(instrumentUid), any(), any()))
                .thenReturn(List.of(candle1))
                .thenReturn(List.of(candle2));

            // when
            service.migrateInstrument(instrumentUid, from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verify(source, times(2)).getPeriod(eq(instrumentUid), any(), any());
            verify(target).saveBatch(List.of(candle1));
            verify(target).saveBatch(List.of(candle2));
            verifyNoMoreInteractions(source);
            verifyNoMoreInteractions(target);
        }

        @Test
        void errorOnSpecificChunk() {
            // given
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = LONG_AGO;
            Instant to = LONG_AGO_PLUS_8_DAYS;

            Candle candle1 = Candle.of(LONG_AGO_PLUS_8_DAYS, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 2));
            when(source.getPeriod(eq(instrumentUid), any(), any()))
                .thenReturn(List.of(candle1))
                .thenThrow(new RuntimeException("Error chunk"));

            // when
            service.migrateInstrument(instrumentUid, from, to);

            // then
            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verify(source, times(2)).getPeriod(eq(instrumentUid), any(), any());
            verify(target).saveBatch(List.of(candle1));
        }
    }

    @Nested
    class MigrateInstrumentWithCheckpoint {

        @Test
        void skipsChunksAlreadyMarkedDone() {
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(checkpoint.isDone(eq(instrumentUid), any())).thenReturn(true);

            CandleMigrationService serviceWithCheckpoint = new CandleMigrationService(
                new NullProgressTrackerFactory(), source, target, 1, 7, checkpoint
            );

            serviceWithCheckpoint.migrateInstrument(instrumentUid, from, to);

            verify(source).getRangeMetadata(eq(instrumentUid), any(), any());
            verify(checkpoint).isDone(eq(instrumentUid), any());
            verifyNoMoreInteractions(source);
            verifyNoInteractions(target);
        }

        @Test
        void marksChunkDoneAfterSuccessfulSave() {
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;
            Candle candle = Candle.of(CANDLE_TIME_1, 100, 100.0);

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(checkpoint.isDone(eq(instrumentUid), any())).thenReturn(false);
            when(source.getPeriod(eq(instrumentUid), any(), any())).thenReturn(List.of(candle));

            CandleMigrationService serviceWithCheckpoint = new CandleMigrationService(
                new NullProgressTrackerFactory(), source, target, 1, 7, checkpoint
            );

            serviceWithCheckpoint.migrateInstrument(instrumentUid, from, to);

            verify(target).saveBatch(List.of(candle));
            verify(checkpoint).markDone(eq(instrumentUid), any());
        }

        @Test
        void doesNotMarkChunkDoneOnFailure() {
            String instrumentUid = "TEST_INSTRUMENT";
            Instant from = FIXED_FROM;
            Instant to = FIXED_TO;

            when(source.getRangeMetadata(eq(instrumentUid), any(), any()))
                .thenReturn(new CandleRangeMetadata(new TimePointRange(from, to), 1));
            when(checkpoint.isDone(eq(instrumentUid), any())).thenReturn(false);
            when(source.getPeriod(eq(instrumentUid), any(), any()))
                .thenThrow(new RuntimeException("boom"));

            CandleMigrationService serviceWithCheckpoint = new CandleMigrationService(
                new NullProgressTrackerFactory(), source, target, 1, 7, checkpoint
            );

            serviceWithCheckpoint.migrateInstrument(instrumentUid, from, to);

            verify(checkpoint, never()).markDone(eq(instrumentUid), any());
        }
    }
}
