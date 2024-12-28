package investtech.broker.impl.tinkoff.shared.translation;

import com.google.protobuf.Timestamp;
import ru.tinkoff.piapi.core.utils.DateUtils;

import java.time.Instant;

public class TimestampTranslator {
    public static Timestamp toTinkoff(Instant timestamp) {
        return DateUtils.instantToTimestamp(timestamp);
    }

    public static Instant toContract(com.google.protobuf.Timestamp timestamp) {
        return DateUtils.timestampToInstant(timestamp);
    }
}
