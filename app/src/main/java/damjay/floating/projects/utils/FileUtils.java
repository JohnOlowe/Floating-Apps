package damjay.floating.projects.utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class FileUtils {
    private static void createNewFile(String path) {
        int lastSep = path.lastIndexOf(File.separator);
        if (lastSep > 0) {
            String dirPath = path.substring(0, lastSep);
            makeDir(dirPath);
        }
        File file = new File(path);
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String readFile(String path) {
        createNewFile(path);

        StringBuilder sb = new StringBuilder();
        FileReader fr = null;
        try {
            fr = new FileReader(new File(path));

            char[] buff = new char[1024];
            int length = 0;

            while ((length = fr.read(buff)) > 0) {
                sb.append(new String(buff, 0, length));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fr != null) {
                try {
                    fr.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return sb.toString();
    }

    public static void writeFile(String path, String str) {
        createNewFile(path);
        FileWriter fileWriter = null;

        try {
            fileWriter = new FileWriter(new File(path), false);
            fileWriter.write(str);
            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fileWriter != null)
                    fileWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void copyFile(String sourcePath, String destPath) {
        if (!isExistFile(sourcePath)) return;
        createNewFile(destPath);

        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(sourcePath);
            fos = new FileOutputStream(destPath, false);

            byte[] buff = new byte[1024];
            int length = 0;

            while ((length = fis.read(buff)) > 0) {
                fos.write(buff, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static boolean copyStream(InputStream srcStream, OutputStream destStream) {
    	try {
            byte[] buff = new byte[1024];
            int length = 0;

            while ((length = srcStream.read(buff)) > 0) {
                destStream.write(buff, 0, length);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (srcStream != null) {
                try {
                    srcStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (destStream != null) {
                try {
                    destStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }
        return true;
    }

    public static void moveFile(String sourcePath, String destPath) {
        copyFile(sourcePath, destPath);
        deleteFile(sourcePath);
    }

    public static void deleteFile(String path) {
        File file = new File(path);
        if (file.exists()) {
            if (file.isFile()) {
                file.delete();
                return;
            }
            File[] fileArr = file.listFiles();
            if (fileArr != null) {
                for (File subFile : fileArr) {
                    if (subFile.isDirectory()) {
                        deleteFile(subFile.getAbsolutePath());
                    }
                    if (subFile.isFile()) {
                        subFile.delete();
                    }
                }
            }
            file.delete();
        }
    }

    public static boolean isExistFile(String path) {
        File file = new File(path);
        return file.exists();
    }

    public static void makeDir(String path) {
        if (!isExistFile(path)) {
            File file = new File(path);
            file.mkdirs();
        }
    }

    public static void listDir(String path, ArrayList<String> list) {
        File[] listFiles;
        File dir = new File(path);
        if (!dir.exists() || dir.isFile() || (listFiles = dir.listFiles()) == null || listFiles.length <= 0 || list == null) {
            return;
        }
        list.clear();
        for (File file : listFiles) {
            list.add(file.getAbsolutePath());
        }
    }

    public static boolean isDirectory(String path) {
        if (isExistFile(path)) {
            return new File(path).isDirectory();
        }
        return false;
    }

    public static boolean isFile(String path) {
        if (isExistFile(path)) {
            return new File(path).isFile();
        }
        return false;
    }

    public static long getFileLength(String path) {
        if (isExistFile(path)) {
            return new File(path).length();
        }
        return 0L;
    }

    public static String getExternalStorageDir() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    public static String getPackageDataDir(Context context) {
        return context.getExternalFilesDir(null).getAbsolutePath();
    }

    public static String getPublicDir(String type) {
        return Environment.getExternalStoragePublicDirectory(type).getAbsolutePath();
    }

    public static String convertUriToFilePath(Context context, Uri uri) {
        String path = null;
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] split = docId.split(":");
                if ("primary".equalsIgnoreCase(split[0])) {
                    path = Environment.getExternalStorageDirectory() + "/" + split[1];
                }
            } else if (isDownloadsDocument(uri)) {
                String id = DocumentsContract.getDocumentId(uri);
                if (!TextUtils.isEmpty(id) && id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(id));
                path = getDataColumn(context, contentUri, null, null);
            } else if (isMediaDocument(uri)) {
                String docId2 = DocumentsContract.getDocumentId(uri);
                String[] split2 = docId2.split(":");
                String type = split2[0];
                Uri contentUri2 = null;
                if ("image".equals(type)) {
                    contentUri2 = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri2 = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri2 = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                String[] selectionArgs = {split2[1]};
                path = getDataColumn(context, contentUri2, "_id=?", selectionArgs);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            path = getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            path = uri.getPath();
        }
        if (path == null) {
            return null;
        }
        try {
            return URLDecoder.decode(path, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        String[] projection = {"_data"};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow("_data");
                return cursor.getString(column_index);
            }
            if (cursor == null) {
                return null;
            }
        } catch (Exception e) {
            if (cursor == null) {
                return null;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    private static void saveBitmap(Bitmap bitmap, String destPath) {
        FileOutputStream out = null;
        createNewFile(destPath);
        try {
            try {
                try {
                    out = new FileOutputStream(new File(destPath));
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.close();
                } catch (Throwable th) {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    throw th;
                }
            } catch (Exception e2) {
                e2.printStackTrace();
                if (out != null) {
                    out.close();
                }
            }
        } catch (IOException e3) {
            e3.printStackTrace();
        }
    }

    public static Bitmap getScaledBitmap(String path, int max) {
        int width;
        int height;
        Bitmap src = BitmapFactory.decodeFile(path);
        int width2 = src.getWidth();
        int height2 = src.getHeight();
        if (width2 > height2) {
            float rate = max / width2;
            float rate2 = height2;
            height = (int) (rate2 * rate);
            width = max;
        } else {
            float rate3 = max;
            float rate4 = width2;
            width = (int) (rate4 * (rate3 / height2));
            height = max;
        }
        return Bitmap.createScaledBitmap(src, width, height, true);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int width = options.outWidth;
        int height = options.outHeight;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampleBitmapFromPath(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    public static void resizeBitmapFileRetainRatio(String fromPath, String destPath, int max) {
        if (isExistFile(fromPath)) {
            Bitmap bitmap = getScaledBitmap(fromPath, max);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void resizeBitmapFileToSquare(String fromPath, String destPath, int max) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            Bitmap bitmap = Bitmap.createScaledBitmap(src, max, max, true);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void resizeBitmapFileToCircle(String fromPath, String destPath) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            Bitmap bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            Rect rect = new Rect(0, 0, src.getWidth(), src.getHeight());
            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(-12434878);
            canvas.drawCircle(src.getWidth() / 2, src.getHeight() / 2, src.getWidth() / 2, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(src, rect, rect, paint);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void resizeBitmapFileWithRoundedBorder(String fromPath, String destPath, int pixels) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            Bitmap bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            Rect rect = new Rect(0, 0, src.getWidth(), src.getHeight());
            RectF rectF = new RectF(rect);
            paint.setAntiAlias(true);
            canvas.drawARGB(0, 0, 0, 0);
            paint.setColor(-12434878);
            canvas.drawRoundRect(rectF, pixels, pixels, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(src, rect, rect, paint);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void cropBitmapFileFromCenter(String fromPath, String destPath, int w, int h) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            int width = src.getWidth();
            int height = src.getHeight();
            if (width < w && height < h) {
                return;
            }
            int x = 0;
            int y = 0;
            if (width > w) {
                x = (width - w) / 2;
            }
            if (height > h) {
                y = (height - h) / 2;
            }
            int cw = w;
            int ch = h;
            if (w > width) {
                cw = width;
            }
            if (h > height) {
                ch = height;
            }
            Bitmap bitmap = Bitmap.createBitmap(src, x, y, cw, ch);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void rotateBitmapFile(String fromPath, String destPath, float angle) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            Matrix matrix = new Matrix();
            matrix.postRotate(angle);
            Bitmap bitmap = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, true);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void scaleBitmapFile(String fromPath, String destPath, float x, float y) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            Matrix matrix = new Matrix();
            matrix.postScale(x, y);
            int w = src.getWidth();
            int h = src.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(src, 0, 0, w, h, matrix, true);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void skewBitmapFile(String fromPath, String destPath, float x, float y) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            Matrix matrix = new Matrix();
            matrix.postSkew(x, y);
            int w = src.getWidth();
            int h = src.getHeight();
            Bitmap bitmap = Bitmap.createBitmap(src, 0, 0, w, h, matrix, true);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void setBitmapFileColorFilter(String fromPath, String destPath, int color) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            Bitmap bitmap = Bitmap.createBitmap(src, 0, 0, src.getWidth() - 1, src.getHeight() - 1);
            Paint p = new Paint();
            ColorFilter filter = new LightingColorFilter(color, 1);
            p.setColorFilter(filter);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawBitmap(bitmap, 0.0f, 0.0f, p);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void setBitmapFileBrightness(String fromPath, String destPath, float brightness) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            ColorMatrix cm = new ColorMatrix(new float[]{1.0f, 0.0f, 0.0f, 0.0f, brightness, 0.0f, 1.0f, 0.0f, 0.0f, brightness, 0.0f, 0.0f, 1.0f, 0.0f, brightness, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
            Bitmap bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            canvas.drawBitmap(src, 0.0f, 0.0f, paint);
            saveBitmap(bitmap, destPath);
        }
    }

    public static void setBitmapFileContrast(String fromPath, String destPath, float contrast) {
        if (isExistFile(fromPath)) {
            Bitmap src = BitmapFactory.decodeFile(fromPath);
            ColorMatrix cm = new ColorMatrix(new float[]{contrast, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, contrast, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, contrast, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f});
            Bitmap bitmap = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            canvas.drawBitmap(src, 0.0f, 0.0f, paint);
            saveBitmap(bitmap, destPath);
        }
    }

    public static int getJpegRotate(String filePath) {
        try {
            ExifInterface exif = new ExifInterface(filePath);
            int iOrientation = exif.getAttributeInt("Orientation", -1);
            switch (iOrientation) {
                case 3:
                    return 180;
                case 6:
                    return 90;
                case 8:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException e) {
            return 0;
        }
    }

    public static File createNewPictureFile(Context context) {
        SimpleDateFormat date = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String fileName = date.format(new Date()) + ".jpg";
        return new File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM).getAbsolutePath() + File.separator + fileName);
    }
}
