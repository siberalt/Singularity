package investtech.emulation.shared.market.candle.factory;

import investtech.configuration.ConfigurationInterface;
import investtech.emulation.shared.market.candle.storage.cvs.CvsCandleStorage;
import investtech.factory.FactoryInterface;
import investtech.factory.ServiceContainer;
import investtech.shared.stream.input.SequencedZipInputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CvsFileCandleStorageFactory implements FactoryInterface {
    protected String instrumentUid;

    @Override
    public CvsCandleStorage create(ConfigurationInterface config, ServiceContainer serviceManager) throws FileNotFoundException {
        String filesDir = (String) config.get("filesDir");

        if (null == instrumentUid) {
            instrumentUid = (String) config.get("instrumentUid");
        }

        File[] files = new File(filesDir).listFiles();
        List<InputStream> fileStreams = new ArrayList<>();
        InputStream inputStream;

        if (null != files) {
            for (File file : files) {
                var fileStream = new FileInputStream(file);

                if (file.getName().endsWith(".zip")) {
                    fileStreams.add(new SequencedZipInputStream(fileStream));
                } else {
                    fileStreams.add(fileStream);
                }
            }

            inputStream = new SequenceInputStream(Collections.enumeration(fileStreams));
            inputStream = new BufferedInputStream(inputStream);
        } else {
            inputStream = InputStream.nullInputStream();
        }

        return new CvsCandleStorage(instrumentUid, inputStream);
    }

    public CvsFileCandleStorageFactory setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }
}
