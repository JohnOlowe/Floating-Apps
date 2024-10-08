package damjay.floating.projects;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import damjay.floating.projects.utils.ImageScaler;
import damjay.floating.projects.utils.ViewsUtils;

@RequiresApi(21)
public class PDFReaderService extends Service {
    private View pdfLayout;
    private WindowManager windowManager;
    private LayoutParams params;

    public static File pdfFile;

    private PdfRenderer inputPdf;
    private PdfRenderer.Page page;
    private Bitmap pageBitmap;
    private ImageScaler zoomView;

    public int currentPage = 0;
    private int pageCount = -1;
    public int lastWrittenPage;

    private EditText pageField;
    private ImageButton closeButton;
    private View expandButton;
    private ImageView pageImageView;
    private View minimizeButton;
    private View toggleFocus;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @SuppressLint("InflateParams")
    @Override
    public void onCreate() {
        try {
            super.onCreate();

            pdfLayout = LayoutInflater.from(this).inflate(R.layout.pdf_reader_layout, null);

            params = getLayoutParams();

            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.addView(pdfLayout, params);

            View collapsedView = pdfLayout.findViewById(R.id.collapsed);
            View expandedView = pdfLayout.findViewById(R.id.expanded);
            expandedView.setVisibility(View.GONE);

            View launchApp = pdfLayout.findViewById(R.id.launchApp);
            closeButton = pdfLayout.findViewById(R.id.collapseButton);
            minimizeButton = pdfLayout.findViewById(R.id.minimizePDF);
            toggleFocus = pdfLayout.findViewById(R.id.toggleFocus);
            expandButton = pdfLayout.findViewById(R.id.expand_button);

            setOnClickListener(collapsedView, expandedView, launchApp);
            setTouchListener(params, collapsedView, expandedView);

            assignNavButtons();
            // initializePdfPage();
        } catch (Throwable report) {
            report.printStackTrace();
        }

    }

    private void setOnClickListener(final View collapsedView, final View expandedView, View launchApp) {
        launchApp.setOnClickListener(v -> ViewsUtils.launchApp(this, MainActivity.class));
        closeButton.setOnClickListener(v -> stopSelf());
        
        minimizeButton.setOnClickListener(
            new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    collapsedView.setVisibility(View.VISIBLE);
                    expandedView.setVisibility(View.GONE);
                    params.flags = LayoutParams.FLAG_NOT_FOCUSABLE;
                    windowManager.updateViewLayout(pdfLayout, params);
                    if (lastWrittenPage != currentPage)
                        updateSavedPage();
                }

                private void updateSavedPage() {
                    try {
                        SavedContent saved = readHistory();
                        saved.setPage(currentPage);
                        saveHistory(saved);
                        // If saved successfully, update the last written page.
                        lastWrittenPage = currentPage;
                    } catch (Throwable t) {
                        t.printStackTrace();
                        Toast.makeText(PDFReaderService.this, R.string.save_page_error, Toast.LENGTH_LONG).show();
                    }
                }
            });

        expandButton.setOnClickListener(view -> {
            collapsedView.setVisibility(View.GONE);
            expandedView.setVisibility(View.VISIBLE);
            params.flags = LayoutParams.FLAG_NOT_TOUCH_MODAL;
            windowManager.updateViewLayout(pdfLayout, params);
            initializePdfPage();
        });

        toggleFocus.setOnClickListener(view -> {
            params.flags = params.flags == LayoutParams.FLAG_NOT_FOCUSABLE ? LayoutParams.FLAG_NOT_TOUCH_MODAL : LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(pdfLayout, params);
        });

    }

    private void assignNavButtons() {
        Button previousPage = pdfLayout.findViewById(R.id.prev_button);
        Button nextPage = pdfLayout.findViewById(R.id.next_button);
        previousPage.setOnClickListener(getNavListener(false));
        nextPage.setOnClickListener(getNavListener(true));

        Button zoomIn = pdfLayout.findViewById(R.id.zoomin_button);
        Button zoomOut = pdfLayout.findViewById(R.id.zoomout_button);
        zoomIn.setOnClickListener(getZoomListener(true));
        zoomOut.setOnClickListener(getZoomListener(false));

        if (pageField == null) {
            pageField = pdfLayout.findViewById(R.id.page_number);
        }
        pageField.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                String text = pageField.getText().toString();
                if (!text.isEmpty()) {
                    int pageNumber = Integer.parseInt(text) - 1;
                    if (pageNumber < 0 || pageNumber > pageCount) {
                        Toast.makeText(PDFReaderService.this, R.string.invalid_page_number, Toast.LENGTH_SHORT).show();
                    } else {
                        currentPage = pageNumber;
                        initializePdfPage();
                    }
                }
            }
            return false;
        });
    }

    private View.OnClickListener getNavListener(final boolean next) {
        return view -> {
            if (pageCount > 0) {
                currentPage += next ? 1 : -1;
                if (currentPage >= pageCount || currentPage < 0) {
                    currentPage = next ? 0 : pageCount - 1;
                }
            }
            initializePdfPage();
        };
    }

    private View.OnClickListener getZoomListener(final boolean zoomIn) {
        return view -> {
            if (zoomView != null) {
                if (zoomIn) zoomView.increaseScale();
                else zoomView.decreaseScale();
                initializePdfPage(1);
            }
        };
    }

    private void setTouchListener(WindowManager.LayoutParams params, View collapsedView, View expandedView) {
        View.OnTouchListener touchListener = ViewsUtils.getViewTouchListener(this, pdfLayout, windowManager, params);
        ViewsUtils.addTouchListener(expandedView, touchListener, true, true, ImageButton.class, Button.class, RelativeLayout.class, LinearLayout.class);
        ViewsUtils.addTouchListener(collapsedView, touchListener, true, true);
    }

    private void initializePdfPage(final int... zoom) {
        if (inputPdf == null && !initializeInputPdf()) {
            Toast.makeText(this, R.string.null_pdf_error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (pageBitmap != null && pageImageView != null && zoom.length > 0) {
            pageImageView.setImageBitmap(zoomView.getScaled(pageBitmap));
            return;
        }
        if (pageCount == -1) {
            pageCount = inputPdf.getPageCount();
            ((Button) pdfLayout.findViewById(R.id.page_count)).setText(" / " + pageCount);
        }

        if (page != null) {
            page.close();
            page = null;
        }
        if (currentPage >= pageCount) currentPage = 0;
        if (currentPage < pageCount) {
            page = inputPdf.openPage(currentPage);
            pageBitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);

            if (zoomView == null) {
                zoomView = new ImageScaler();
            }
            page.render(pageBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

            if (pageImageView == null) {
                pageImageView = pdfLayout.findViewById(R.id.pdf_page);
            }
            if (pageField == null) {
                pageField = pdfLayout.findViewById(R.id.page_number);
            }

            final View view = pdfLayout.findViewById(R.id.imageScrollView);
            view.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        ViewGroup.LayoutParams params = view.getLayoutParams();
                        params.height = (pageBitmap.getHeight() * view.getMeasuredWidth()) / pageBitmap.getWidth();

                        if (zoomView != null && zoom.length == 0) {
                            zoomView.setDefaultMinScale((float) params.height / (float)pageBitmap.getHeight());
                            if (currentPage == 0) zoomView.setScale(zoomView.getDefaultMinScale());
                            pageImageView.setImageBitmap(zoomView.getScaled(pageBitmap));
                        }
                    }
                });
            pageImageView.setImageBitmap(zoomView.getScaled(pageBitmap));
            pageField.setText("" + (currentPage + 1));
        } else {
            Toast.makeText(this, R.string.pdf_page_error, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean initializeInputPdf() {
        if (pdfFile == null) {
            try {
                SavedContent savedContent = readHistory();
                pdfFile = new File(getCacheDir(), savedContent.getFileName());
                currentPage = savedContent.getPage();
                if (!pdfFile.exists()) System.out.println("The file \"" + savedContent.getFileName() + "\" does not exist.");
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        try {
            ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            inputPdf = new PdfRenderer(descriptor);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return false;
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

    private void saveHistory(SavedContent savedContent) throws IOException {
        savePathHistory(getCacheDir(), new File(savedContent.getFileName()), savedContent.getPage());
    }

    private SavedContent readHistory() throws IOException {
        StringBuilder name = new StringBuilder();

        File history = new File(getCacheDir(), FloatingPDFActivity.HISTORY_FILE);
        FileInputStream historyStream = new FileInputStream(history);
        DataInputStream historyDataStream = new DataInputStream(historyStream);

        byte[] buffer = new byte[1024];
        int read;

        // The current page
        int page = historyDataStream.readShort();

        while ((read = historyDataStream.read(buffer)) > 0) {
            name.append(new String(buffer, 0, read));
        }
        // Close the stream
        historyStream.close();

        System.out.println("The PDF file name is \"" + name + "\", and we on page " + page + ".");

        return new SavedContent(name.toString(), page);
    }

    private WindowManager.LayoutParams getLayoutParams() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? LayoutParams.TYPE_APPLICATION_OVERLAY : LayoutParams.TYPE_PHONE;
        LayoutParams params = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            type,
            LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;
        return params;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (pdfLayout != null) {
            windowManager.removeView(pdfLayout);
            pdfLayout = null;
        }
        if (page != null) {
            page.close();
            page = null;
        }
        if (inputPdf != null) {
            inputPdf.close();
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
