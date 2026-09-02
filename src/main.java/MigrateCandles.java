import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.candle.SqliteCandleRepository;
import com.siberalt.singularity.entity.candle.SqliteCandleRepositoryFactory;
import com.siberalt.singularity.entity.candle.cvs.CvsCandleRepository;
import com.siberalt.singularity.entity.candle.cvs.CvsFileCandleRepositoryFactory;
import com.siberalt.singularity.runtime.progress.ConsoleProgressTrackerFactory;
import com.siberalt.singularity.service.ConfigFacade;
import com.siberalt.singularity.utils.entity.CandleMigrationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;

public class MigrateCandles {
    public static void main(String[] args) throws IOException, SQLException {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        CvsFileCandleRepositoryFactory cvsCandleRepositoryFactory = new CvsFileCandleRepositoryFactory();
        SqliteCandleRepositoryFactory sqliteCandleRepositoryFactory = new SqliteCandleRepositoryFactory();

        try (
            CvsCandleRepository cvsCandleRepository = cvsCandleRepositoryFactory.create(
                "TMOS",
                "src/test/resources/entity.candle.cvs/TMOS"
            );
            SqliteCandleRepository sqliteCandleRepository = sqliteCandleRepositoryFactory.create(
                ConfigFacade.of(configuration).getAsString("dbPath")
            )
        ) {
            CandleMigrationService candleMigrationService = CandleMigrationService.builder(cvsCandleRepository, sqliteCandleRepository)
                .progressTrackerFactory(new ConsoleProgressTrackerFactory())
                .parallelism(5)
                .chunkSizeDays(360)
                .build();
            candleMigrationService.migrateInstrument("TMOS", Instant.MIN, Instant.MAX);
        }
    }
}
