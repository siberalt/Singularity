package com.siberalt.singularity.broker.impl.tinkoff.shared.translation;

import com.google.protobuf.Timestamp;

import java.time.Instant;

public class TimestampTranslator {
    public static Timestamp toTinkoff(Instant timestamp) {
        if (timestamp == null) {
            return Timestamp.getDefaultInstance();
        }
        return Timestamp.newBuilder()
            .setSeconds(timestamp.getEpochSecond())
            .setNanos(timestamp.getNano())
            .build();
    }

    public static Instant toContract(Timestamp timestamp) {
        if (timestamp == null || timestamp.getSeconds() == 0 && timestamp.getNanos() == 0) {
            return null;
        }
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
