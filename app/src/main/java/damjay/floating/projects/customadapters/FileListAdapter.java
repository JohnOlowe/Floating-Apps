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
import java.util.Iterator;

public class FileListAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<FileItem> fileItems = new ArrayList<>();
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

    private void loadStorageDrives() {
        try {
            if (internalDrive == null) {
                internalDrive = Environment.getExternalStorageDirectory();
            }
            internalStorageDrives = getInternalStorageDrives();
            folder = null;
            fileItems.clear();
            for (FileItem item : internalStorageDrives) {
                fileItems.add(item);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private ArrayList<FileItem> getInternalStorageDrives() {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null);
        ArrayList<FileItem> storageMedia = new ArrayList<>();
        int driveNo = 0;
        int length = externalFilesDirs.length;
        int i = 0;
        while (i < length) {
            File file = externalFilesDirs[i];
            String filePath = file.getPath();
            int driveNo2 = driveNo + 1;
            storageMedia.add(new FileItem(new File(filePath.substring(0, filePath.indexOf("Android"))),
                    driveNo == 0 ? "Phone Storage" : "SDCard" + (driveNo2 - 1)));
            i++;
            driveNo = driveNo2;
        }
        return storageMedia;
    }

    public void updatePath(File folder) {
        String rootValue = internalDrive.getParentFile().getParent();
        if (folder.getPath().equals(rootValue)) {
            if (folder == null || folder.getPath().equals(internalDrive.getParent())) {
                return;
            }
            Iterator<FileItem> it = internalStorageDrives.iterator();
            while (it.hasNext()) {
                if (it.next().getFile().getPath().equals(folder.getPath())) {
                    loadStorageDrives();
                    return;
                }
            }
            internalStorageDrives.add(new FileItem(folder, "SDCard" + internalStorageDrives.size()));
            loadStorageDrives();
            return;
        }
        fileItems.clear();
        String rootValue2 = internalDrive.getParent();
        if (folder.getPath().equals(rootValue2)) {
            loadStorageDrives();
            return;
        }
        int folders = 0;
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file2 : files) {
            if (file2.isDirectory()) {
                fileItems.add(folders, new FileItem(file2));
                folders++;
            } else {
                fileItems.add(new FileItem(file2));
            }
        }
        this.folder = folder;
    }

    @Override
    public int getCount() {
        if (fileItems == null) {
            return 0;
        }
        return fileItems.size();
    }

    @Override
    public Object getItem(int index) {
        if (fileItems.size() <= index) {
            return null;
        }
        return fileItems.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        if (fileItems == null) {
            return view;
        }
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.file_items, viewGroup, false);
        }
        FileItem item = fileItems.get(position);
        FileItem.ViewLayout layout = item.getLayout();
        layout.setName(view.findViewById(R.id.fileName)).setText(item.getFileName());
        layout.setInfo(view.findViewById(R.id.fileInfo))
                .setText((item.isDirectory() ? "" : FormatUtils.formatSize(item.getFileSize()) + ", ")
                        + FormatUtils.formatDate(item.getLastModified()));
        ImageView icon = layout.setIcon(view.findViewById(R.id.file_icon));
        int resource = item.isDirectory() ? R.drawable.folder : R.drawable.file_icon;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            icon.setImageDrawable(
                    ResourcesCompat.getDrawable(context.getResources(), resource, context.getTheme()));
        } else {
            icon.setImageResource(resource);
        }
        return view;
    }
}
