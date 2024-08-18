package investtech.configuration;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;

public class YamlConfiguration extends MapConfiguration {
    public YamlConfiguration(InputStream stream) {
        super (new Yaml().load(stream));
    }

    public YamlConfiguration(String config){
        super(new Yaml().load(config));
    }
}
