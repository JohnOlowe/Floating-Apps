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
            if (this.internalDrive == null) {
                this.internalDrive = Environment.getExternalStorageDirectory();
            }
            this.internalStorageDrives = getInternalStorageDrives();
            this.folder = null;
            this.fileItems.clear();
            for (FileItem item : this.internalStorageDrives) {
                this.fileItems.add(item);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private ArrayList<FileItem> getInternalStorageDrives() {
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(this.context, null);
        ArrayList<FileItem> storageMedia = new ArrayList<>();
        int driveNo = 0;
        int length = externalFilesDirs.length;
        int i = 0;
        while (i < length) {
            File file = externalFilesDirs[i];
            String filePath = file.getPath();
            int driveNo2 = driveNo + 1;
            storageMedia.add(new FileItem(new File(filePath.substring(0, filePath.indexOf("Android"))), driveNo == 0 ? "Phone Storage" : "SDCard" + (driveNo2 - 1)));
            i++;
            driveNo = driveNo2;
        }
        return storageMedia;
    }

    public void updatePath(File folder) {
        String rootValue = this.internalDrive.getParentFile().getParent();
        if (folder.getPath().equals(rootValue)) {
            File file = this.folder;
            if (file == null || file.getPath().equals(this.internalDrive.getParent())) {
                return;
            }
            Iterator<FileItem> it = this.internalStorageDrives.iterator();
            while (it.hasNext()) {
                if (it.next().getFile().getPath().equals(this.folder.getPath())) {
                    loadStorageDrives();
                    return;
                }
            }
            this.internalStorageDrives.add(new FileItem(this.folder, "SDCard" + this.internalStorageDrives.size()));
            loadStorageDrives();
            return;
        }
        this.fileItems.clear();
        String rootValue2 = this.internalDrive.getParent();
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
                this.fileItems.add(folders, new FileItem(file2));
                folders++;
            } else {
                this.fileItems.add(new FileItem(file2));
            }
        }
        this.folder = folder;
    }

    @Override
    public int getCount() {
        ArrayList<FileItem> arrayList = this.fileItems;
        if (arrayList == null) {
            return 0;
        }
        return arrayList.size();
    }

    @Override
    public Object getItem(int index) {
        if (this.fileItems.size() <= index) {
            return null;
        }
        return this.fileItems.get(index);
    }

    @Override
    public long getItemId(int index) {
        return index;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        if (this.fileItems == null) {
            return view;
        }
        if (view == null) {
            view = LayoutInflater.from(this.context).inflate(R.layout.file_items, viewGroup, false);
        }
        FileItem item = this.fileItems.get(position);
        FileItem.ViewLayout layout = item.getLayout();
        layout.setName(view.findViewById(R.id.fileName)).setText(item.getFileName());
        layout.setInfo(view.findViewById(R.id.fileInfo)).setText((item.isDirectory() ? "" : FormatUtils.formatSize(item.getFileSize()) + ", ") + FormatUtils.formatDate(item.getLastModified()));
        ImageView icon = layout.setIcon(view.findViewById(R.id.file_icon));
        int resource = item.isDirectory() ? R.drawable.folder : R.drawable.file_icon;
        if (Build.VERSION.SDK_INT >= 21) {
            icon.setImageDrawable(ResourcesCompat.getDrawable(this.context.getResources(), resource, this.context.getTheme()));
        } else {
            icon.setImageResource(resource);
        }
        return view;
    }
}
