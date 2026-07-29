import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.db.initialize.FlywayDatabaseInitializer;
import com.siberalt.singularity.service.ConfigFacade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class InitializeDb {
    public static void main(String[] args) throws IOException {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/main/resources/app.yaml"))
        );

        FlywayDatabaseInitializer initializer = new FlywayDatabaseInitializer();
        initializer.migrate(ConfigFacade.of(configuration).getAsString("dbPath"));
    }
}
