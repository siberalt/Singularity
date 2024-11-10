package investtech.shared.stream.input;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipFileInputStream extends InputStream {
    protected ZipFile zipFile;
    protected List<ZipEntry> entries = new ArrayList<>();
    protected Iterator<ZipEntry> entryIterator;
    protected InputStream currentInputStream;

    public ZipFileInputStream(ZipFile zipFile) {
        this.zipFile = zipFile;
        var iterator = zipFile.entries();

        while (iterator.hasMoreElements()) {
            entries.add(iterator.nextElement());
        }
    }

    public ZipFileInputStream processEntries(Consumer<List<ZipEntry>> processFunc) {
        processFunc.accept(entries);
        return this;
    }

    @Override
    public int read() throws IOException {
        if (null == currentInputStream) {
            if (null == entryIterator) {
                entryIterator = entries.iterator();
            }

            if (!entryIterator.hasNext()) {
                return -1;
            }

            currentInputStream = zipFile.getInputStream(entryIterator.next());
        }

        int oneByte = currentInputStream.read();

        if (-1 == oneByte) {
            currentInputStream.close();
            currentInputStream = null;
        }

        return oneByte;
    }

    @Override
    public void close() throws IOException {
        if (null != currentInputStream) {
            currentInputStream.close();
        }
        super.close();
    }
}
