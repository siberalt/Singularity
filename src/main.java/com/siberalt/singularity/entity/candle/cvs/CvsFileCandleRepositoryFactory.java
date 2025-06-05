package com.siberalt.singularity.entity.candle.cvs;

import com.siberalt.singularity.service.DependencyManager;
import com.siberalt.singularity.service.ServiceDetails;
import com.siberalt.singularity.service.factory.Factory;
import com.siberalt.singularity.shared.stream.input.ZipFileInputStream;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CvsFileCandleRepositoryFactory implements Factory {
    public CvsCandleRepository create(String instrumentUid, String filesDir) {
        File[] files = new File(filesDir).listFiles();

        if (null == files) {
            throw new IllegalArgumentException("No files found in directory: " + filesDir);
        }

        List<InputStream> fileStreams = new ArrayList<>();
        InputStream inputStream = null;

        try {
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

        return new CvsCandleRepository(instrumentUid, inputStream);
    }

    @Override
    public CvsCandleRepository create(ServiceDetails serviceDetails, DependencyManager dependencyManager) {
        String filesDir = (String) serviceDetails.config().get("filesDir");
        var instrumentUid = (String) serviceDetails.config().get("instrumentUid");

        return create(instrumentUid, filesDir);
    }
}
