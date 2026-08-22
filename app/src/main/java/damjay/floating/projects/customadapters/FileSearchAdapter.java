package damjay.floating.projects.customadapters;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import damjay.floating.projects.FloatingPDFActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.files.FileBrowserActivity;
import damjay.floating.projects.files.FileItem;
import damjay.floating.projects.utils.FormatUtils;
import java.io.File;
import java.util.ArrayList;

public class FileSearchAdapter extends BaseAdapter {
    public static final String[] COMMON_PATH = {"Documents/", "Xender/", "Download/", "Movies/",
            "Android/media/com.whatsapp/WhatsApp/Media/Whatsapp Documents/"};
    public static String SUPPORTED_EXT = FloatingPDFActivity.PDF_EXTENSION;
    private final FileBrowserActivity.FileCallback callback;
    private final Context context;
    private ArrayList<DocumentFile> documentFiles;
    private ArrayList<DocumentFile> matchingFiles;
    public String pendingKeyword;
    private ArrayList<File> storageMedia;

    public FileSearchAdapter(Context context, FileBrowserActivity.FileCallback callback) {
        this.context = context;
        this.callback = callback;
        File[] externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null);
        storageMedia = new ArrayList<>();
        for (File file : externalFilesDirs) {
            String filePath = file.getPath();
            storageMedia.add(new File(filePath.substring(0, filePath.indexOf("Android"))));
            System.out.println(file);
        }
        if (callback != null) {
            SUPPORTED_EXT = callback.extensionAllowed();
        }
        loadDocumentFiles();
    }

    private void loadDocumentFiles() {
        Handler handler = new Handler();
        new Thread(() -> {
            if (documentFiles != null) {
                return;
            }
            for (File storageMediaFile : storageMedia) {
                if (documentFiles == null) {
                    documentFiles = new ArrayList<>();
                }
                getDocumentFiles(getCommonDirectoryFiles(storageMediaFile), documentFiles, true);
                getDocumentFiles(storageMediaFile.listFiles(), documentFiles, false);
            }
            handler.post(() -> {
                if (pendingKeyword == null) {
                    pendingKeyword = "";
                }
                reloadSearchResults(pendingKeyword);
            });
        }).start();
    }

    public void reloadSearchResults(String keyword) {
        String keyword2 = keyword.trim();
        if (documentFiles == null) {
            pendingKeyword = keyword2;
            return;
        }
        if (keyword2.isEmpty()) {
            matchingFiles = documentFiles;
            notifyDataSetChanged();
            return;
        }
        ArrayList<DocumentFile> matchingFiles = new ArrayList<>();
        int originalMatchNumber = 0;
        for (DocumentFile file : documentFiles) {
            if (file.name.contains(keyword2)) {
                matchingFiles.add(originalMatchNumber, file);
                originalMatchNumber++;
            } else if (file.name.toLowerCase().contains(keyword2.toLowerCase())) {
                matchingFiles.add(file);
            }
        }
        this.matchingFiles = matchingFiles;
        notifyDataSetChanged();
    }

    private void getDocumentFiles(File[] paths, ArrayList<DocumentFile> documentFiles, boolean recursive) {
        if (paths == null) {
            return;
        }
        for (File path : paths) {
            if (path.exists()) {
                if (path.isDirectory()) {
                    if (recursive) {
                        getDocumentFiles(path.listFiles(), documentFiles, true);
                    }
                } else {
                    DocumentFile file = new DocumentFile(path);
                    if (file.name.toLowerCase().endsWith(SUPPORTED_EXT)) {
                        documentFiles.add(file);
                    }
                }
            }
        }
    }

    private File[] getCommonDirectoryFiles(File parent) {
        File[] outputFiles = new File[COMMON_PATH.length];
        int i = 0;
        while (true) {
            String[] strArr = COMMON_PATH;
            if (i < strArr.length) {
                outputFiles[i] = new File(parent, strArr[i]);
                i++;
            } else {
                return outputFiles;
            }
        }
    }

    @Override
    public int getCount() {
        if (matchingFiles == null) {
            return 0;
        }
        return matchingFiles.size();
    }

    @Override
    public Object getItem(int position) {
        if (matchingFiles == null) {
            return null;
        }
        return matchingFiles.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (matchingFiles == null) {
            return null;
        }
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.file_items, parent, false);
        }
        FileItem item = matchingFiles.get(position).getFileItem();
        FileItem.ViewLayout layout = item.getLayout();
        layout.setName(convertView.findViewById(R.id.fileName)).setText(item.getFileName());
        layout.setInfo(convertView.findViewById(R.id.fileInfo))
                .setText(FormatUtils.formatSize(item.getFileSize()) + ", "
                        + FormatUtils.formatDate(item.getLastModified()));
        ImageView icon = layout.setIcon(convertView.findViewById(R.id.file_icon));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            icon.setImageDrawable(ResourcesCompat.getDrawable(
                    context.getResources(), R.drawable.pdf_logo, context.getTheme()));
        } else {
            icon.setImageResource(R.drawable.pdf_logo);
        }
        return convertView;
    }

    public static class DocumentFile {
        private final FileItem fileItem;
        String name;
        String path;

        DocumentFile(File file) {
            FileItem fileItem = new FileItem(file);
            this.fileItem = fileItem;
            name = fileItem.getFileName();
            path = fileItem.getDirectoryName();
        }

        public FileItem getFileItem() {
            return fileItem;
        }
    }
}
