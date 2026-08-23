package damjay.floating.projects.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class for safe file copying and file name resolution from content URIs.
 */
public class IOUtils {

    /** Resolve file name from content URI or file path */
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result != null ? result.lastIndexOf('/') : -1;
            if (cut != -1 && result != null) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    /** Copy content from URI to file, returning false on failure */
    public static boolean safeCopy(Context context, Uri uri, File file) {
        try {
            copy(context, uri, file);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /** Copy content from URI to file */
    public static void copy(Context context, Uri uri, File file) throws IOException {
        InputStream src = context.getContentResolver().openInputStream(uri);
        OutputStream dest = new FileOutputStream(file);
        copy(src, dest);
    }

    /** Copy data between input and output streams */
    public static void copy(InputStream src, OutputStream dest) throws IOException {
        int read;
        byte[] buffer = new byte[102400];
        while ((read = src.read(buffer)) > 0) {
            dest.write(buffer, 0, read);
        }
        src.close();
        dest.close();
    }
}
