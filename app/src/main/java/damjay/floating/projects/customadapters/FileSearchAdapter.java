package damjay.floating.projects.customadapters;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;

import androidx.core.content.res.ResourcesCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import damjay.floating.projects.R;
import damjay.floating.projects.files.FileBrowserActivity;
import damjay.floating.projects.files.FileItem;
import damjay.floating.projects.utils.FormatUtils;

public class FileSearchAdapter extends BaseAdapter {
    public static final String SUPPORTED_EXT = ".pdf";

    private final Context context;
    private final ArrayList<File> storageMedia = new ArrayList<>();
    private ArrayList<DocumentFile> documentFiles;
    private ArrayList<DocumentFile> matchingFiles;
    private String pendingKeyword;

    public FileSearchAdapter(Context context, FileBrowserActivity.FileCallback callback) {
        this.context = context;
        storageMedia.add(Environment.getExternalStorageDirectory());
        loadDocumentFiles();
    }

    private File[] getCommonDirectoryFiles(File root) {
        List<File> files = new ArrayList<>();
        for (String name : Arrays.asList("Download", "Documents", "Books", "WhatsApp")) {
            File file = new File(root, name);
            if (file.exists()) files.add(file);
        }
        return files.toArray(new File[0]);
    }

    private void getDocumentFiles(File[] files, ArrayList<DocumentFile> results, boolean recursive) {
        if (files == null) return;
        for (File file : files) {
            if (!file.exists()) continue;
            if (file.isDirectory()) {
                if (recursive) getDocumentFiles(file.listFiles(), results, true);
            } else if (file.getName().toLowerCase().endsWith(SUPPORTED_EXT)) {
                results.add(new DocumentFile(file));
            }
        }
    }

    private void loadDocumentFiles() {
        Handler handler = new Handler();
        new Thread(() -> {
            if (documentFiles != null) return;
            documentFiles = new ArrayList<>();
            for (File storage : storageMedia) {
                getDocumentFiles(getCommonDirectoryFiles(storage), documentFiles, true);
                getDocumentFiles(storage.listFiles(), documentFiles, false);
            }
            handler.post(() -> reloadSearchResults(pendingKeyword == null ? "" : pendingKeyword));
        }).start();
    }

    public void reloadSearchResults(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        if (documentFiles == null) {
            pendingKeyword = query;
            return;
        }
        if (query.isEmpty()) {
            matchingFiles = documentFiles;
        } else {
            matchingFiles = new ArrayList<>();
            int exactIndex = 0;
            for (DocumentFile file : documentFiles) {
                if (file.name.contains(query)) {
                    matchingFiles.add(exactIndex++, file);
                } else if (file.name.toLowerCase().contains(query.toLowerCase())) {
                    matchingFiles.add(file);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return matchingFiles == null ? 0 : matchingFiles.size();
    }

    @Override
    public Object getItem(int position) {
        return matchingFiles == null ? null : matchingFiles.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (matchingFiles == null) return null;
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.file_items, parent, false);
        }
        FileItem item = matchingFiles.get(position).getFileItem();
        FileItem.ViewLayout layout = item.getLayout();
        layout.setName(convertView.findViewById(R.id.fileName)).setText(item.getFileName());
        layout.setInfo(convertView.findViewById(R.id.fileInfo)).setText(FormatUtils.formatSize(item.getFileSize()) + ", " + FormatUtils.formatDate(item.getLastModified()));
        ImageView icon = layout.setIcon(convertView.findViewById(R.id.file_icon));
        icon.setImageDrawable(ResourcesCompat.getDrawable(context.getResources(), R.drawable.pdf_logo, context.getTheme()));
        return convertView;
    }

    public static class DocumentFile {
        public final String name;
        private final FileItem fileItem;

        public DocumentFile(File file) {
            name = file.getName();
            fileItem = new FileItem(file);
        }

        public FileItem getFileItem() {
            return fileItem;
        }
    }
}
