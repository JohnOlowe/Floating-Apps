package damjay.floating.projects.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipUtils {
    public static boolean extractZip(File file, File outputDir) {
        try {
            try {
                Enumeration<? extends ZipEntry> enumerationEntries = zipFile.entries();
                while (enumerationEntries.hasMoreElements()) {
                    ZipEntry curEntry = enumerationEntries.nextElement();
                    String path = curEntry.getName();
                    File outputFile = new File(outputDir, path);
                    if (curEntry.isDirectory()) {
                        outputFile.mkdirs();
                    } else {
                        outputFile.getParentFile().mkdirs();
                        InputStream stream = zipFile.getInputStream(curEntry);
                        if (!FileUtils.copyStream(stream, new FileOutputStream(outputFile))) {
                            zipFile.close();
                            return false;
                        }
                    }
                }
                zipFile.close();
                return true;
            } catch (Throwable th) {
                try {
                    zipFile.close();
                } catch (Throwable th2) {
                    th.addSuppressed(th2);
                }
                throw th;
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }
}
