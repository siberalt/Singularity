package investtech.shared.stream.input;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SequencedZipInputStream extends InputStream {
    protected ZipInputStream zipInputStream;

    protected ZipEntry currentEntry;

    public SequencedZipInputStream(InputStream inputStream) {
        this.zipInputStream = new ZipInputStream(inputStream);
    }

    public SequencedZipInputStream(ZipInputStream zipInputStream) {
        this.zipInputStream = zipInputStream;
    }

    @Override
    public int read() throws IOException {
        if (null == currentEntry) {
            currentEntry = zipInputStream.getNextEntry();

            if (null == currentEntry) {
                return -1;
            }
        }

        int oneByte = zipInputStream.read();

        if (-1 == oneByte) {
            zipInputStream.closeEntry();
        }

        return oneByte;
    }
}
