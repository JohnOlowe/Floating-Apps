package damjay.floating.projects.files;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;

public class FileItem {
    private String directoryName;
    private File file;
    private String fileName;
    private long fileSize;
    private String formattedSize;
    private String fullPath;
    private boolean isDirectory;
    private ViewLayout layout;

    private FileItem(String fileName, String directoryName, String fullPath, long fileSize, boolean isDirectory) {
        this.fileName = fileName;
        this.directoryName = directoryName;
        this.fileSize = fileSize;
        this.fullPath = fullPath;
        this.isDirectory = isDirectory;
    }

    public FileItem(File file) {
        this(file.getName(), file.getParent(), file.getPath(), file.length(), file.isDirectory());
        this.file = file;
    }

    public FileItem(File file, String name) {
        this(file);
        fileName = name;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public long getLastModified() {
        return file.lastModified();
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getDirectoryName() {
        return directoryName;
    }

    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFormattedSize(String formattedSize) {
        this.formattedSize = formattedSize;
    }

    public String getFormattedSize() {
        return formattedSize;
    }

    public void setIsDirectory(boolean isDirectory) {
        this.isDirectory = isDirectory;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setLayout(ViewLayout layout) {
        this.layout = layout;
    }

    public ViewLayout getLayout() {
        ViewLayout viewLayout = layout;
        return viewLayout == null ? new ViewLayout() : viewLayout;
    }

    public static class ViewLayout {
        private ImageView icon;
        private TextView info;
        private TextView name;

        public ImageView setIcon(View icon) {
            ImageView imageView = (ImageView) icon;
            icon = imageView;
            return imageView;
        }

        public ImageView getIcon() {
            return icon;
        }

        public TextView setName(View name) {
            TextView textView = (TextView) name;
            name = textView;
            return textView;
        }

        public TextView getName() {
            return name;
        }

        public TextView setInfo(View info) {
            TextView textView = (TextView) info;
            info = textView;
            return textView;
        }

        public TextView getInfo() {
            return info;
        }
    }
}
