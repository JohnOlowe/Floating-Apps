package damjay.floating.projects.autoclicker.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.bluetooth.BluetoothSocket;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import damjay.floating.projects.R;
import damjay.floating.projects.autoclicker.activity.ClickerActivity;
import damjay.floating.projects.bluetooth.BluetoothOperations;
import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants;
import damjay.floating.projects.utils.ViewsUtils;
import java.util.ArrayList;

public class ClickerAccessibilityService
        extends AccessibilityService implements BluetoothOperations.BluetoothOperationsCallback {
    public static BluetoothSocket bluetoothSocket;
    private BluetoothOperations btOperation;
    private View clickerLayout;
    private WindowManager.LayoutParams clickerParams;
    private WindowManager windowManager;
    private final ArrayList<View> clickPoints = new ArrayList<>();
    private boolean pendingAddButton = false;
    private boolean pendingRemoveButton = false;

    @Override
    public void onCreate() {
        super.onCreate();
        clickerLayout = LayoutInflater.from(this).inflate(R.layout.service_clicker, null);
        if (bluetoothSocket == null) {
            System.out.println("Socket is null. Destroying...");
            stopSelf();
            return;
        }
        System.out.println("Socket is not null. Continuing...");
        btOperation = new BluetoothOperations(bluetoothSocket);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        clickerParams = ViewsUtils.getFloatingLayoutParams();
        windowManager.addView(clickerLayout, clickerParams);
        addClickListeners();
        btOperation.startReading(this);
    }

    private void addClickListeners() {
        clickerLayout.findViewById(R.id.addClicker)
                .setOnClickListener(v -> sendToDevice(ClickerActivity.CLICKER_ADD_POINT, this));
        clickerLayout.findViewById(R.id.removeClicker)
                .setOnClickListener(v -> sendToDevice(ClickerActivity.CLICKER_DELETE_POINT, this));
        clickerLayout.findViewById(R.id.closeClicker)
                .setOnClickListener(v -> notifyAndStop());
        ViewsUtils.addTouchListener(clickerLayout,
                ViewsUtils.getViewTouchListener(this, clickerLayout, windowManager, clickerParams),
                true, true, null);
    }

    public void sendToDevice(byte value, BluetoothOperationsCallback callback) {
        if (btOperation != null) {
            btOperation.write(value, callback);
            pendingAddButton = (value == ClickerActivity.CLICKER_ADD_POINT) || pendingAddButton;
            pendingRemoveButton = (value == ClickerActivity.CLICKER_DELETE_POINT) || pendingRemoveButton;
        } else {
            stopSelf();
        }
    }

    private void addNewButton() {
        pendingAddButton = false;
        View clickPoint = LayoutInflater.from(this).inflate(R.layout.clicker_point, null);
        ((TextView) clickPoint.findViewById(R.id.clickId))
                .setText(String.valueOf(clickPoints.size() + 1));

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        WindowManager.LayoutParams pointParams;

        if (clickPoints.isEmpty()) {
            pointParams = ViewsUtils.getFloatingLayoutParams(metrics.widthPixels / 4, metrics.heightPixels / 4);
        } else {
            View lastPoint = clickPoints.get(clickPoints.size() - 1);
            WindowManager.LayoutParams lastParams = (WindowManager.LayoutParams) lastPoint.getTag();

            if (lastParams == null) {
                pointParams = ViewsUtils.getFloatingLayoutParams(metrics.widthPixels / 4, metrics.heightPixels / 4);
            } else if (lastParams.x > (metrics.widthPixels * 3) / 4) {
                if (lastParams.y > (metrics.heightPixels * 3) / 4) {
                    pointParams = ViewsUtils.getFloatingLayoutParams(
                            metrics.widthPixels / 8, metrics.heightPixels / 8);
                } else {
                    pointParams = ViewsUtils.getFloatingLayoutParams(
                            metrics.widthPixels / 4, lastParams.y + lastPoint.getMeasuredHeight());
                }
            } else {
                pointParams = ViewsUtils.getFloatingLayoutParams(
                        lastParams.x + lastPoint.getMeasuredWidth(), lastParams.y);
            }
        }

        clickPoint.setTag(pointParams);
        clickPoint.setOnTouchListener(
                ViewsUtils.getViewTouchListener(this, clickPoint, windowManager, pointParams));
        clickPoints.add(clickPoint);
        windowManager.addView(clickPoint, pointParams);
    }

    private void removeLastButton() {
        pendingRemoveButton = false;
        if (!clickPoints.isEmpty()) {
            View lastPoint = clickPoints.get(clickPoints.size() - 1);
            windowManager.removeView(lastPoint);
            clickPoints.remove(clickPoints.size() - 1);
        }
    }

    private void clickPoint(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0L, 20L));
        dispatchGesture(builder.build(), null, null);
    }

    @Override
    public void onSuccess(byte type, Object value) {
        switch (type) {
            case BluetoothOperationsConstants.TYPE_BYTE:
                byte byteValue = (Byte) value;
                if (byteValue == ClickerActivity.CLICKER_ADD_POINT) {
                    addNewButton();
                } else if (byteValue == ClickerActivity.CLICKER_DELETE_POINT) {
                    removeLastButton();
                } else if (clickPoints.size() > byteValue) {
                    WindowManager.LayoutParams params =
                            (WindowManager.LayoutParams) clickPoints.get(byteValue).getTag();
                    clickPoint(params.x, params.y);
                }
                break;
            case BluetoothOperationsConstants.TYPE_SUCCESS:
                if (pendingAddButton) {
                    addNewButton();
                }
                if (pendingRemoveButton) {
                    removeLastButton();
                }
                break;
        }
    }

    @Override
    public void onError(Throwable t) {
        stopSelf();
    }

    private void notifyAndStop() {
        btOperation.writeExit(this);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        windowManager.removeView(clickerLayout);
        for (View clickPoint : clickPoints) {
            windowManager.removeView(clickPoint);
        }
        clickPoints.clear();
        if (btOperation != null) {
            btOperation.close();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
