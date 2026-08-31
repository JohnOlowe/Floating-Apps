package damjay.floating.projects.customadapters;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import damjay.floating.projects.R;
import damjay.floating.projects.files.FileItem;
import damjay.floating.projects.utils.FormatUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Adapter for displaying file and directory listings in floating file browsers.
 * Shows internal storage drives, directories, and files with icons and metadata.
 */
public class FileListAdapter extends BaseAdapter {

    private final Context context;
    private final ArrayList<FileItem> fileItems = new ArrayList<>();

    public File folder;
    public File internalDrive;
    private ArrayList<FileItem> internalStorageDrives;

    public FileListAdapter(Context context, File folder) {
        this.context = context;
        loadStorageDrives();
        if (folder != null) {
            updatePath(folder);
        }
    }

    /** Load available storage media and initialize root view */
    private void loadStorageDrives() {
        try {
            if (internalDrive == null) {
                internalDrive = Environment.getExternalStorageDirectory();
            }
            internalStorageDrives = getInternalStorageDrives();
            folder = null;
            fileItems.clear();
            fileItems.addAll(internalStorageDrives);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /** Detect internal storage drives from external files directories */
    private ArrayList<FileItem> getInternalStorageDrives() {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null);
        ArrayList<FileItem> storageMedia = new ArrayList<>();
        int driveNo = 0;
        for (File file : externalFilesDirs) {
            if (file == null) continue;
            String filePath = file.getPath();
            int androidIndex = filePath.indexOf("Android");
            String rootPath = (androidIndex >= 0) ? filePath.substring(0, androidIndex) : filePath;
            String label = (driveNo == 0) ? "Phone Storage" : "SDCard" + driveNo;
            storageMedia.add(new FileItem(new File(rootPath), label));
            driveNo++;
        }
        return storageMedia;
    }

    /** Update adapter content for selected directory path */
    public void updatePath(File folder) {
        String rootValue = internalDrive != null ? internalDrive.getParentFile().getParent() : null;
        if (folder == null || rootValue == null) return;

        // Navigate back to root storage
        if (folder.getPath().equals(rootValue)) {
            File currentFolder = this.folder;
            if (currentFolder == null || currentFolder.getPath().equals(internalDrive.getParent())) {
                return;
            }
            // Check if current folder is a storage drive
            Iterator<FileItem> it = internalStorageDrives.iterator();
            while (it.hasNext()) {
                if (it.next().getFile().getPath().equals(this.folder.getPath())) {
                    loadStorageDrives();
                    return;
                }
            }
            internalStorageDrives.add(new FileItem(
                    this.folder, "SDCard" + internalStorageDrives.size()));
            loadStorageDrives();
            return;
        }

        fileItems.clear();
        String parentPath = internalDrive != null ? internalDrive.getParent() : null;
        if (folder.getPath().equals(parentPath)) {
            loadStorageDrives();
            return;
        }

        int directoryCount = 0;
        File[] files = folder.listFiles();
        if (files == null) return;

        // Sort: directories first, then files, both alphabetically
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (File file : files) {
            if (file.isDirectory()) {
                fileItems.add(directoryCount, new FileItem(file));
                directoryCount++;
            } else {
                fileItems.add(new FileItem(file));
            }
        }
        this.folder = folder;
    }

    @Override
    public int getCount() {
        return fileItems == null ? 0 : fileItems.size();
    }

    @Override
    public Object getItem(int index) {
        return (fileItems == null || fileItems.size() <= index) ? null : fileItems.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        if (fileItems == null) return view;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.file_items, parent, false);
        }
        FileItem item = fileItems.get(position);
        FileItem.ViewLayout layout = item.getLayout();
        layout.setName(view.findViewById(R.id.fileName)).setText(item.getFileName());
        layout.setInfo(view.findViewById(R.id.fileInfo)).setText(
                (item.isDirectory() ? "" : FormatUtils.formatSize(item.getFileSize()) + ", ")
                        + FormatUtils.formatDate(item.getLastModified()));
        ImageView icon = layout.setIcon(view.findViewById(R.id.file_icon));
        int resource = item.isDirectory() ? R.drawable.folder : R.drawable.file_icon;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            icon.setImageDrawable(ResourcesCompat.getDrawable(
                    context.getResources(), resource, context.getTheme()));
        } else {
            icon.setImageResource(resource);
        }
        return view;
    }
}
