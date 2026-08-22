package damjay.floating.projects;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import damjay.floating.projects.utils.ImageScaler;
import damjay.floating.projects.utils.ViewsUtils;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class PDFReaderService extends Service {
    public static File pdfFile;
    private ImageButton closeButton;
    private View expandButton;
    private PdfRenderer inputPdf;
    public int lastWrittenPage;
    private View minimizeButton;
    private View openInApp;
    private PdfRenderer.Page page;
    private Bitmap pageBitmap;
    private EditText pageField;
    private ImageView pageImageView;
    private WindowManager.LayoutParams params;
    private View pdfLayout;
    private View toggleFocus;
    private WindowManager windowManager;
    private ImageScaler zoomView;
    public int currentPage = 0;
    private int pageCount = -1;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        try {
            super.onCreate();
            this.pdfLayout = LayoutInflater.from(this).inflate(R.layout.pdf_reader_layout, (ViewGroup) null);
            this.params = getLayoutParams();
            this.windowManager = windowManager;
            windowManager.addView(this.pdfLayout, this.params);
            View collapsedView = this.pdfLayout.findViewById(R.id.collapsed);
            View expandedView = this.pdfLayout.findViewById(R.id.expanded);
            expandedView.setVisibility(View.GONE);
            View launchApp = this.pdfLayout.findViewById(R.id.launchApp);
            this.closeButton = (ImageButton) this.pdfLayout.findViewById(R.id.collapseButton);
            this.minimizeButton = this.pdfLayout.findViewById(R.id.minimizePDF);
            this.toggleFocus = this.pdfLayout.findViewById(R.id.toggleFocus);
            this.openInApp = this.pdfLayout.findViewById(R.id.openInApp);
            this.expandButton = this.pdfLayout.findViewById(R.id.expand_button);
            setOnClickListener(collapsedView, expandedView, launchApp);
            setTouchListener(this.params, collapsedView, expandedView);
            assignNavButtons();
        } catch (Throwable report) {
            report.printStackTrace();
        }
    }

    private void setOnClickListener(View collapsedView, View expandedView, View launchApp) {
        launchApp.setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        this.closeButton.setOnClickListener(v -> stopSelf());
        this.minimizeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                collapsedView.setVisibility(View.VISIBLE);
                expandedView.setVisibility(View.GONE);
                PDFReaderService.this.params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                PDFReaderService.this.windowManager.updateViewLayout(
                        PDFReaderService.this.pdfLayout, PDFReaderService.this.params);
                if (PDFReaderService.this.lastWrittenPage != PDFReaderService.this.currentPage) {
                    updateSavedPage();
                }
            }

            private void updateSavedPage() {
                try {
                    SavedContent saved = PDFReaderService.this.readHistory();
                    saved.setPage(PDFReaderService.this.currentPage);
                    PDFReaderService.this.saveHistory(saved);
                    PDFReaderService pDFReaderService = PDFReaderService.this;
                    pDFReaderService.lastWrittenPage = pDFReaderService.currentPage;
                } catch (Throwable t) {
                    t.printStackTrace();
                    Toast.makeText(PDFReaderService.this, R.string.save_page_error, Toast.LENGTH_LONG).show();
                }
            }
        });
        this.expandButton.setOnClickListener((view) -> {
            collapsedView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
            this.params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            ((ImageView) this.toggleFocus).setImageResource(R.drawable.focus_on);
            this.windowManager.updateViewLayout(this.pdfLayout, this.params);
            initializePdfPage(new int[0]);
        });
        this.toggleFocus.setOnClickListener((view) -> {
            if (this.params.flags == WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) {
                this.params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                ((ImageView) this.toggleFocus).setImageResource(R.drawable.focus_on);
            } else {
                this.params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                ((ImageView) this.toggleFocus).setImageResource(R.drawable.focus_off);
            }
            this.windowManager.updateViewLayout(this.pdfLayout, this.params);
        });
        this.openInApp.setOnClickListener((view) -> {
            Uri pdfUri = FileProvider.getUriForFile(this, "damjay.floating.projects.provider", pdfFile);
            Intent intent = new Intent();
            intent.setAction("android.intent.action.VIEW");
            intent.addFlags(268435456);
            intent.addFlags(1);
            intent.setDataAndType(pdfUri, "application/pdf");
            Intent chooserIntent = Intent.createChooser(intent, getResources().getString(R.string.choose_app_to_open));
            chooserIntent.addFlags(268435456);
            List<ResolveInfo> resolveInfoList = getPackageManager().queryIntentActivities(intent, 65536);
            for (ResolveInfo resolveInfo : resolveInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                grantUriPermission(packageName, pdfUri, 1);
            }
            try {
                startActivity(chooserIntent);
                this.minimizeButton.callOnClick();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, getResources().getString(R.string.no_app_to_open_pdf), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void assignNavButtons() {
        Button previousPage = (Button) this.pdfLayout.findViewById(R.id.prev_button);
        Button nextPage = (Button) this.pdfLayout.findViewById(R.id.next_button);
        previousPage.setOnClickListener(getNavListener(false));
        nextPage.setOnClickListener(getNavListener(true));
        Button zoomIn = (Button) this.pdfLayout.findViewById(R.id.zoomin_button);
        Button zoomOut = (Button) this.pdfLayout.findViewById(R.id.zoomout_button);
        zoomIn.setOnClickListener(getZoomListener(true));
        zoomOut.setOnClickListener(getZoomListener(false));
        if (this.pageField == null) {
            this.pageField = (EditText) this.pdfLayout.findViewById(R.id.page_number);
        }
        this.pageField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == 2) {
                String text = this.pageField.getText().toString();
                if (!text.isEmpty()) {
                    int pageNumber = Integer.parseInt(text) - 1;
                    if (pageNumber < 0 || pageNumber > this.pageCount) {
                        Toast.makeText(this, R.string.invalid_page_number, Toast.LENGTH_SHORT).show();
                    } else {
                        this.currentPage = pageNumber;
                        initializePdfPage(new int[0]);
                    }
                }
            }
            return false;
        });
    }

    private View.OnClickListener getNavListener(boolean next) {
        return (view) -> {
            int i = this.pageCount;
            if (i > 0) {
                int i2 = this.currentPage + (next ? 1 : -1);
                this.currentPage = i2;
                if (i2 >= i || i2 < 0) {
                    this.currentPage = next ? 0 : i - 1;
                }
            }
            initializePdfPage(new int[0]);
        };
    }

    private View.OnClickListener getZoomListener(boolean zoomIn) {
        return (view) -> {
            ImageScaler imageScaler = this.zoomView;
            if (imageScaler != null) {
                if (zoomIn) {
                    imageScaler.increaseScale();
                } else {
                    imageScaler.decreaseScale();
                }
                initializePdfPage(1);
            }
        };
    }

    private void setTouchListener(WindowManager.LayoutParams params, View collapsedView, View expandedView) {
        View.OnTouchListener touchListener =
                ViewsUtils.getViewTouchListener(this, this.pdfLayout, this.windowManager, params);
        ViewsUtils.addTouchListener(expandedView, touchListener, true, true, ImageButton.class, Button.class,
                RelativeLayout.class, LinearLayout.class);
        ViewsUtils.addTouchListener(collapsedView, touchListener, true, true, new Class[0]);
    }

    private void initializePdfPage(int... zoom) {
        ImageView imageView;
        if (this.inputPdf == null && !initializeInputPdf()) {
            Toast.makeText(this, R.string.null_pdf_error, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = this.pageBitmap;
        if (bitmap != null && (imageView = this.pageImageView) != null && zoom.length > 0) {
            imageView.setImageBitmap(this.zoomView.getScaled(bitmap));
            return;
        }
        if (this.pageCount == -1) {
            this.pageCount = this.inputPdf.getPageCount();
            ((Button) this.pdfLayout.findViewById(R.id.page_count)).setText(" / " + this.pageCount);
        }
        PdfRenderer.Page page = this.page;
        if (page != null) {
            page.close();
            this.page = null;
        }
        int i = this.currentPage;
        int i2 = this.pageCount;
        if (i >= i2) {
            this.currentPage = 0;
        }
        int i3 = this.currentPage;
        if (i3 < i2) {
            PdfRenderer.Page pageOpenPage = this.inputPdf.openPage(i3);
            this.page = pageOpenPage;
            this.pageBitmap =
                    Bitmap.createBitmap(pageOpenPage.getWidth(), this.page.getHeight(), Bitmap.Config.ARGB_8888);
            if (this.zoomView == null) {
                this.zoomView = new ImageScaler();
            }
            this.page.render(this.pageBitmap, null, null, 1);
            if (this.pageImageView == null) {
                this.pageImageView = (ImageView) this.pdfLayout.findViewById(R.id.pdf_page);
            }
            if (this.pageField == null) {
                this.pageField = (EditText) this.pdfLayout.findViewById(R.id.page_number);
            }
            View view = this.pdfLayout.findViewById(R.id.imageScrollView);
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    ViewGroup.LayoutParams params = view.getLayoutParams();
                    params.height = (PDFReaderService.this.pageBitmap.getHeight() * view.getMeasuredWidth())
                            / PDFReaderService.this.pageBitmap.getWidth();
                    if (PDFReaderService.this.zoomView != null && zoom.length == 0) {
                        PDFReaderService.this.zoomView.setDefaultMinScale(
                                params.height / PDFReaderService.this.pageBitmap.getHeight());
                        if (PDFReaderService.this.currentPage == 0) {
                            PDFReaderService.this.zoomView.setScale(
                                    PDFReaderService.this.zoomView.getDefaultMinScale());
                        }
                        PDFReaderService.this.pageImageView.setImageBitmap(
                                PDFReaderService.this.zoomView.getScaled(PDFReaderService.this.pageBitmap));
                    }
                }
            });
            this.pageImageView.setImageBitmap(this.zoomView.getScaled(this.pageBitmap));
            this.pageField.setText("" + (this.currentPage + 1));
            return;
        }
        Toast.makeText(this, R.string.pdf_page_error, Toast.LENGTH_SHORT).show();
    }

    private boolean initializeInputPdf() {
        if (pdfFile == null) {
            try {
                SavedContent savedContent = readHistory();
                pdfFile = new File(getCacheDir(), savedContent.getFileName());
                this.currentPage = savedContent.getPage();
                if (!pdfFile.exists()) {
                    System.out.println("The file \"" + savedContent.getFileName() + "\" does not exist.");
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        try {
            ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdfFile, 268435456);
            this.inputPdf = new PdfRenderer(descriptor);
            return true;
        } catch (Throwable t2) {
            t2.printStackTrace();
            return false;
        }
    }

    public static void savePathHistory(File cacheDir, File newFile, int page) throws IOException {
        File history = new File(cacheDir, FloatingPDFActivity.HISTORY_FILE);
        FileOutputStream fos = new FileOutputStream(history);
        DataOutputStream dos = new DataOutputStream(fos);
        dos.writeShort(page);
        dos.write(newFile.getName().getBytes());
        fos.close();
        dos.close();
    }

    public void saveHistory(SavedContent savedContent) throws IOException {
        savePathHistory(getCacheDir(), new File(savedContent.getFileName()), savedContent.getPage());
    }

    public SavedContent readHistory() throws IOException {
        StringBuilder name = new StringBuilder();
        File history = new File(getCacheDir(), FloatingPDFActivity.HISTORY_FILE);
        FileInputStream historyStream = new FileInputStream(history);
        DataInputStream historyDataStream = new DataInputStream(historyStream);
        byte[] buffer = new byte[1024];
        int page = historyDataStream.readShort();
        while (true) {
            int read = historyDataStream.read(buffer);
            if (read > 0) {
                name.append(new String(buffer, 0, read));
            } else {
                historyStream.close();
                System.out.println("The PDF file name is \"" + ((Object) name) + "\", and we on page " + page + ".");
                return new SavedContent(name.toString(), page);
            }
        }
    }

    private WindowManager.LayoutParams getLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                                                  : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        params.x = 0;
        params.y = 100;
        return params;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        View view = this.pdfLayout;
        if (view != null) {
            this.windowManager.removeView(view);
            this.pdfLayout = null;
        }
        PdfRenderer.Page page = this.page;
        if (page != null) {
            page.close();
            this.page = null;
        }
        PdfRenderer pdfRenderer = this.inputPdf;
        if (pdfRenderer != null) {
            pdfRenderer.close();
            this.inputPdf = null;
        }
    }

    static class SavedContent {
        private String fileName;
        private int page;

        public SavedContent(String fileName, int page) {
            this.fileName = fileName;
            this.page = page;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return this.fileName;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getPage() {
            return this.page;
        }
    }
}
