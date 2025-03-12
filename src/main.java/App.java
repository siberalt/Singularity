import com.siberalt.singularity.TraderBot;
import com.siberalt.singularity.configuration.YamlConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class App {
    public static void main(String[] args) throws IOException {
        YamlConfig configuration = new YamlConfig(
                Files.newInputStream(Paths.get("trader-bot.yaml"))
        );

        TraderBot traderBot = new TraderBot(configuration);

    }
}
