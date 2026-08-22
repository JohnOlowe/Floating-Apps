package damjay.floating.projects.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOUtils {
    public static String getFileName(Context context, Uri uri) {
        int index;
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst() && (index = cursor.getColumnIndex("_display_name")) >= 0) {
                        result = cursor.getString(index);
                    }
                } catch (Throwable th) {
                    if (cursor != null) {
                        try {
                            cursor.close();
                        } catch (Throwable th2) {
                            th.addSuppressed(th2);
                        }
                    }
                    throw th;
                }
            }
            if (cursor != null) {
                cursor.close();
            }
        }
        if (result == null) {
            String result2 = uri.getPath();
            int cut = result2.lastIndexOf('/');
            if (cut != -1) {
                return result2.substring(cut + 1);
            }
            return result2;
        }
        return result;
    }

    public static boolean safeCopy(Context context, Uri uri, File file) {
        try {
            copy(context, uri, file);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    public static void copy(Context context, Uri uri, File file) throws IOException {
        InputStream src = context.getContentResolver().openInputStream(uri);
        OutputStream dest = new FileOutputStream(file);
        copy(src, dest);
    }

    public static void copy(InputStream src, OutputStream dest) throws IOException {
        byte[] buffer = new byte[100 * 1024];
        while (true) {
            int read = src.read(buffer);
            if (read > 0) {
                dest.write(buffer, 0, read);
            } else {
                src.close();
                dest.close();
                return;
            }
        }
    }
}
