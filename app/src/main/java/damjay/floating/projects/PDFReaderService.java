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
            pdfLayout = LayoutInflater.from(this).inflate(R.layout.pdf_reader_layout, (ViewGroup) null);
            params = getLayoutParams();
            WindowManager windowManager = (WindowManager) getSystemService("window");
            this.windowManager = windowManager;
            windowManager.addView(pdfLayout, params);
            View collapsedView = pdfLayout.findViewById(R.id.collapsed);
            View expandedView = pdfLayout.findViewById(R.id.expanded);
            expandedView.setVisibility(View.GONE);
            View launchApp = pdfLayout.findViewById(R.id.launchApp);
            closeButton = (ImageButton) pdfLayout.findViewById(R.id.collapseButton);
            minimizeButton = pdfLayout.findViewById(R.id.minimizePDF);
            toggleFocus = pdfLayout.findViewById(R.id.toggleFocus);
            openInApp = pdfLayout.findViewById(R.id.openInApp);
            expandButton = pdfLayout.findViewById(R.id.expand_button);
            setOnClickListener(collapsedView, expandedView, launchApp);
            setTouchListener(params, collapsedView, expandedView);
            assignNavButtons();
        } catch (Throwable report) {
            report.printStackTrace();
        }
    }

    private void setOnClickListener(View collapsedView, View expandedView, View launchApp) {
        launchApp.setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        closeButton.setOnClickListener(v -> stopSelf());
        minimizeButton.setOnClickListener(new View.OnClickListener() {
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
        expandButton.setOnClickListener((view) -> {
            collapsedView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            ((ImageView) toggleFocus).setImageResource(R.drawable.focus_on);
            windowManager.updateViewLayout(pdfLayout, params);
            initializePdfPage(new int[0]);
        });
        toggleFocus.setOnClickListener((view) -> {
            if (params.flags == WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
                ((ImageView) toggleFocus).setImageResource(R.drawable.focus_on);
            } else {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                ((ImageView) toggleFocus).setImageResource(R.drawable.focus_off);
            }
            windowManager.updateViewLayout(pdfLayout, params);
        });
        openInApp.setOnClickListener((view) -> {
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
                minimizeButton.callOnClick();
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, getResources().getString(R.string.no_app_to_open_pdf), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void assignNavButtons() {
        Button previousPage = (Button) pdfLayout.findViewById(R.id.prev_button);
        Button nextPage = (Button) pdfLayout.findViewById(R.id.next_button);
        previousPage.setOnClickListener(getNavListener(false));
        nextPage.setOnClickListener(getNavListener(true));
        Button zoomIn = (Button) pdfLayout.findViewById(R.id.zoomin_button);
        Button zoomOut = (Button) pdfLayout.findViewById(R.id.zoomout_button);
        zoomIn.setOnClickListener(getZoomListener(true));
        zoomOut.setOnClickListener(getZoomListener(false));
        if (pageField == null) {
            pageField = (EditText) pdfLayout.findViewById(R.id.page_number);
        }
        pageField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == 2) {
                String text = pageField.getText().toString();
                if (!text.isEmpty()) {
                    int pageNumber = Integer.parseInt(text) - 1;
                    if (pageNumber < 0 || pageNumber > pageCount) {
                        Toast.makeText(this, R.string.invalid_page_number, Toast.LENGTH_SHORT).show();
                    } else {
                        currentPage = pageNumber;
                        initializePdfPage(new int[0]);
                    }
                }
            }
            return false;
        });
    }

    private View.OnClickListener getNavListener(boolean next) {
        return (view) -> {
            int i = pageCount;
            if (i > 0) {
                int i2 = currentPage + (next ? 1 : -1);
                currentPage = i2;
                if (i2 >= i || i2 < 0) {
                    currentPage = next ? 0 : i - 1;
                }
            }
            initializePdfPage(new int[0]);
        };
    }

    private View.OnClickListener getZoomListener(boolean zoomIn) {
        return (view) -> {
            ImageScaler imageScaler = zoomView;
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
                ViewsUtils.getViewTouchListener(this, pdfLayout, windowManager, params);
        ViewsUtils.addTouchListener(expandedView, touchListener, true, true, ImageButton.class, Button.class,
                RelativeLayout.class, LinearLayout.class);
        ViewsUtils.addTouchListener(collapsedView, touchListener, true, true, new Class[0]);
    }

    private void initializePdfPage(int... zoom) {
        ImageView imageView;
        if (inputPdf == null && !initializeInputPdf()) {
            Toast.makeText(this, R.string.null_pdf_error, Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = pageBitmap;
        if (bitmap != null && (imageView = pageImageView) != null && zoom.length > 0) {
            imageView.setImageBitmap(zoomView.getScaled(bitmap));
            return;
        }
        if (pageCount == -1) {
            pageCount = inputPdf.getPageCount();
            ((Button) pdfLayout.findViewById(R.id.page_count)).setText(" / " + pageCount);
        }
        PdfRenderer.Page page = page;
        if (page != null) {
            page.close();
            page = null;
        }
        int i = currentPage;
        int i2 = pageCount;
        if (i >= i2) {
            currentPage = 0;
        }
        int i3 = currentPage;
        if (i3 < i2) {
            PdfRenderer.Page pageOpenPage = inputPdf.openPage(i3);
            page = pageOpenPage;
            pageBitmap =
                    Bitmap.createBitmap(pageOpenPage.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
            if (zoomView == null) {
                zoomView = new ImageScaler();
            }
            page.render(pageBitmap, null, null, 1);
            if (pageImageView == null) {
                pageImageView = (ImageView) pdfLayout.findViewById(R.id.pdf_page);
            }
            if (pageField == null) {
                pageField = (EditText) pdfLayout.findViewById(R.id.page_number);
            }
            View view = pdfLayout.findViewById(R.id.imageScrollView);
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
            pageImageView.setImageBitmap(zoomView.getScaled(pageBitmap));
            pageField.setText("" + (currentPage + 1));
            return;
        }
        Toast.makeText(this, R.string.pdf_page_error, Toast.LENGTH_SHORT).show();
    }

    private boolean initializeInputPdf() {
        if (pdfFile == null) {
            try {
                SavedContent savedContent = readHistory();
                pdfFile = new File(getCacheDir(), savedContent.getFileName());
                currentPage = savedContent.getPage();
                if (!pdfFile.exists()) {
                    System.out.println("The file \"" + savedContent.getFileName() + "\" does not exist.");
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        try {
            ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdfFile, 268435456);
            inputPdf = new PdfRenderer(descriptor);
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
        View view = pdfLayout;
        if (view != null) {
            windowManager.removeView(view);
            pdfLayout = null;
        }
        PdfRenderer.Page page = page;
        if (page != null) {
            page.close();
            page = null;
        }
        PdfRenderer pdfRenderer = inputPdf;
        if (pdfRenderer != null) {
            pdfRenderer.close();
            inputPdf = null;
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
            return fileName;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public int getPage() {
            return page;
        }
    }
}
