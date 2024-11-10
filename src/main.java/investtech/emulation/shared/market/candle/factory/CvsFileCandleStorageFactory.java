package investtech.emulation.shared.market.candle.factory;

import investtech.configuration.ConfigurationInterface;
import investtech.emulation.shared.market.candle.storage.cvs.CvsCandleStorage;
import investtech.factory.FactoryInterface;
import investtech.factory.ServiceContainer;
import investtech.shared.stream.input.ZipFileInputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CvsFileCandleStorageFactory implements FactoryInterface {
    protected String instrumentUid;

    public CvsCandleStorage create(String instrumentUid, String filesDir) {
        File[] files = new File(filesDir).listFiles();
        List<InputStream> fileStreams = new ArrayList<>();
        InputStream inputStream = null;

        try {
            if (null != files) {
                for (File file : files) {
                    if (file.getName().endsWith(".zip")) {
                        inputStream = new ZipFileInputStream(new ZipFile(file))
                                .processEntries(
                                        entries -> entries.sort(Comparator.comparing(ZipEntry::getName))
                                );
                        fileStreams.add(inputStream);
                    } else {
                        fileStreams.add(inputStream = new FileInputStream(file));
                    }
                }

                inputStream = new SequenceInputStream(Collections.enumeration(fileStreams));
                inputStream = new BufferedInputStream(inputStream);
            } else {
                inputStream = InputStream.nullInputStream();
            }
        } catch (Throwable e) {
            try {
                if (null != inputStream) {
                    inputStream.close();
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }

            throw new RuntimeException(e);
        }

        return new CvsCandleStorage(instrumentUid, inputStream);
    }

    @Override
    public CvsCandleStorage create(ConfigurationInterface config, ServiceContainer serviceManager) {
        String filesDir = (String) config.get("filesDir");

        if (null == instrumentUid) {
            instrumentUid = (String) config.get("instrumentUid");
        }

        return create(instrumentUid, filesDir);
    }

    public CvsFileCandleStorageFactory setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }
}
