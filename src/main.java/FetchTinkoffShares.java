import com.siberalt.singularity.broker.contract.service.exception.AbstractException;
import com.siberalt.singularity.broker.contract.service.instrument.InstrumentService;
import com.siberalt.singularity.broker.contract.service.instrument.request.GetTradableRequest;
import com.siberalt.singularity.broker.impl.tinkoff.shared.factory.TinkoffInstrumentServiceFactory;
import com.siberalt.singularity.configuration.ConfigInterface;
import com.siberalt.singularity.configuration.YamlConfig;
import com.siberalt.singularity.entity.instrument.Instrument;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

public class FetchTinkoffShares {
    public static void main(String[] args) throws IOException, AbstractException {
        ConfigInterface configuration = new YamlConfig(
            Files.newInputStream(Paths.get("src/test/resources/broker/tinkoff/test-settings.yaml"))
        );

        Properties properties = new Properties();
        properties.put("token", configuration.get("readonlyToken"));
        ServiceStubFactory serviceStubFactory = ServiceStubFactory.create(
            ConnectorConfiguration.loadFromProperties(properties)
        );

        try {
            InstrumentService instrumentService = new TinkoffInstrumentServiceFactory().create(serviceStubFactory);

            List<Instrument> instruments = instrumentService.getTradable(GetTradableRequest.of("rub"))
                .getInstruments();
            instruments.sort(Comparator.comparing(Instrument::getUid));

            System.out.printf("%-15s %-36s %-40s %6s %8s%n", "ISIN", "UID", "NAME", "LOT", "CURRENCY");
            for (Instrument instrument : instruments) {
                System.out.printf(
                    "%-15s %-36s %-40s %6d %8s%n",
                    instrument.getIsin(),
                    instrument.getUid(),
                    truncate(instrument.getName(), 40),
                    instrument.getLot(),
                    instrument.getCurrency()
                );
            }

            System.out.printf("%nTotal: %d shares available for trading%n", instruments.size());
        } finally {
            serviceStubFactory.getChannel().shutdown();
        }
    }

    private static String truncate(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength - 1) + "…" : value;
    }
}
