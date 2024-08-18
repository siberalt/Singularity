import investtech.TraderBot;
import investtech.configuration.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class App {
    public static void main(String[] args) throws IOException {
        YamlConfiguration configuration = new YamlConfiguration(
                Files.newInputStream(Paths.get("trader-bot.yaml"))
        );

        TraderBot traderBot = new TraderBot(configuration);

    }
}
