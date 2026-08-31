package damjay.floating.projects.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility for extracting zip archives to output directories.
 */
public class ZipUtils {

    /** Extract contents of zip file to output directory */
    public static boolean extractZip(File zipFile, File outputDir) {
        try (ZipFile archive = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryPath = entry.getName();
                File outputFile = new File(outputDir, entryPath);
                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    File parentDir = outputFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    try (InputStream input = archive.getInputStream(entry);
                         FileOutputStream output = new FileOutputStream(outputFile)) {
                        if (!FileUtils.copyStream(input, output)) {
                            return false;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
        return true;
    }
}
