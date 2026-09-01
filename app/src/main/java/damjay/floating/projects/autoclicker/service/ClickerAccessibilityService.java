package damjay.floating.projects.autoclicker.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.bluetooth.BluetoothSocket;
import android.graphics.Path;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import damjay.floating.projects.R;
import damjay.floating.projects.autoclicker.ClickPointLayout;
import damjay.floating.projects.bluetooth.BluetoothOperations;
import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsCallback;
import damjay.floating.projects.utils.ViewsUtils;

import java.util.ArrayList;

import static damjay.floating.projects.autoclicker.activity.ClickerActivity.CLICKER_ADD_POINT;
import static damjay.floating.projects.autoclicker.activity.ClickerActivity.CLICKER_DELETE_POINT;
import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;

@RequiresApi(24)
public class ClickerAccessibilityService extends AccessibilityService implements BluetoothOperationsCallback {
    /** Diameter of each floating click point (matches the clicker_point layout size). */
    private static final int POINT_SIZE_DP = 30;
    /** Gap placed between successive click points. */
    private static final int POINT_SPACING_DP = 16;

    private final ArrayList<View> clickPoints = new ArrayList<>();
    private View clickerLayout;
    private WindowManager windowManager;
    private LayoutParams clickerParams;

    public static BluetoothSocket bluetoothSocket;
    private BluetoothOperations btOperation;

    private boolean pendingAddButton = false;
    private boolean pendingRemoveButton = false;

    @Override
    public void onCreate() {
        super.onCreate();

        if (bluetoothSocket == null) {
            // No Bluetooth session was provided; there is nothing to control or be controlled by.
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            stopSelf();
            return;
        }

        clickerLayout = LayoutInflater.from(this).inflate(R.layout.service_clicker, null);
        clickerParams = ViewsUtils.getFloatingLayoutParams();
        try {
            windowManager.addView(clickerLayout, clickerParams);
        } catch (Throwable t) {
            t.printStackTrace();
            stopSelf();
            return;
        }

        btOperation = new BluetoothOperations(bluetoothSocket);
        addClickListeners();
        btOperation.startReading(this);
    }

    private void addClickListeners() {
        clickerLayout
                .findViewById(R.id.addClicker)
                .setOnClickListener((v) -> sendToDevice(CLICKER_ADD_POINT, this));
        clickerLayout
                .findViewById(R.id.removeClicker)
                .setOnClickListener((v) -> sendToDevice(CLICKER_DELETE_POINT, this));
        clickerLayout.findViewById(R.id.closeClicker).setOnClickListener((v) -> notifyAndStop());

        ViewsUtils.addTouchListener(
                clickerLayout,
                ViewsUtils.getViewTouchListener(this, clickerLayout, windowManager, clickerParams),
                true,
                true,
                (Class[]) null);
    }

    public void sendToDevice(byte value, BluetoothOperationsCallback callback) {
        if (btOperation != null) {
            btOperation.write(value, callback);
            pendingAddButton = value == CLICKER_ADD_POINT || pendingAddButton;
            pendingRemoveButton = value == CLICKER_DELETE_POINT || pendingRemoveButton;
        } else {
            stopSelf();
        }
    }

    private int pointSizePx() {
        return ViewsUtils.dpToPx(POINT_SIZE_DP, this);
    }

    private void addNewButton() {
        pendingAddButton = false;

        View clickPoint = LayoutInflater.from(this).inflate(R.layout.clicker_point, null);
        ((TextView) clickPoint.findViewById(R.id.clickId))
                .setText(Integer.toString(clickPoints.size() + 1));

        int pointSize = pointSizePx();
        int spacing = ViewsUtils.dpToPx(POINT_SPACING_DP, this);
        DisplayMetrics metrics = getResources().getDisplayMetrics();

        boolean hasPrevious = !clickPoints.isEmpty();
        int lastX = 0;
        int lastY = 0;
        if (hasPrevious) {
            LayoutParams last = (LayoutParams) clickPoints.get(clickPoints.size() - 1).getTag();
            if (last == null) {
                hasPrevious = false;
            } else {
                lastX = last.x;
                lastY = last.y;
            }
        }
        int[] position =
                ClickPointLayout.nextPosition(
                        hasPrevious,
                        lastX,
                        lastY,
                        pointSize,
                        spacing,
                        metrics.widthPixels,
                        metrics.heightPixels);

        LayoutParams pointParams = ViewsUtils.getFloatingLayoutParams(position[0], position[1]);
        clickPoint.setTag(pointParams);
        clickPoint.setOnTouchListener(ViewsUtils.getViewTouchListener(this, clickPoint, windowManager, pointParams));
        clickPoints.add(clickPoint);
        try {
            windowManager.addView(clickPoint, pointParams);
        } catch (Throwable t) {
            // Roll back if we somehow failed to attach the point.
            clickPoints.remove(clickPoint);
            t.printStackTrace();
        }
    }

    private void removeLastButton() {
        pendingRemoveButton = false;
        if (windowManager == null || clickPoints.isEmpty()) return;
        View last = clickPoints.remove(clickPoints.size() - 1);
        try {
            windowManager.removeView(last);
        } catch (Throwable ignored) {
            // View may already be detached.
        }
    }

    private void clickPoint(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !canPerformGestures()) {
            return;
        }
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        // A single tap: 0 ms delay, short duration.
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 40));
        dispatchGesture(builder.build(), null, null);
    }

    /**
     * The system only honours {@link #dispatchGesture} when the service was bound with the
     * {@code android:canPerformGestures} capability. getServiceInfo() is null until the service
     * is connected, so this doubles as a "am I actually running?" check.
     */
    private boolean canPerformGestures() {
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            return info != null
                    && (info.getCapabilities()
                                    & AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES)
                            != 0;
        } catch (Throwable t) {
            // getServiceInfo() throws if the service is not connected yet.
            return false;
        }
    }

    @Override
    public void onSuccess(byte type, Object value) {
        switch (type) {
            case TYPE_SUCCESS:
                if (pendingAddButton) addNewButton();
                if (pendingRemoveButton) removeLastButton();
                break;
            case TYPE_BYTE: {
                byte byteValue = (byte) value;
                if (byteValue == CLICKER_ADD_POINT) {
                    addNewButton();
                } else if (byteValue == CLICKER_DELETE_POINT) {
                    removeLastButton();
                } else {
                    // Buttons are labelled 1-based on the controller, points are stored 0-based.
                    int index = ClickPointLayout.indexForButton(byteValue);
                    if (ClickPointLayout.isValidIndex(index, clickPoints.size())) {
                        LayoutParams params = (LayoutParams) clickPoints.get(index).getTag();
                        if (params != null) {
                            int pointSize = pointSizePx();
                            clickPoint(
                                    ClickPointLayout.centerOf(params.x, pointSize),
                                    ClickPointLayout.centerOf(params.y, pointSize));
                        }
                    }
                }
                break;
            }
            default:
                // Ignore unsupported payload types.
        }
    }

    @Override
    public void onError(Throwable t) {
        stopSelf();
    }

    private void notifyAndStop() {
        if (btOperation != null) {
            btOperation.writeExit(this);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (btOperation != null) {
            btOperation.close();
            btOperation = null;
        }
        if (windowManager != null) {
            if (clickerLayout != null) {
                try {
                    windowManager.removeView(clickerLayout);
                } catch (Throwable ignored) {
                }
            }
            for (View clickPoint : clickPoints) {
                try {
                    windowManager.removeView(clickPoint);
                } catch (Throwable ignored) {
                }
            }
        }
        clickPoints.clear();
        clickerLayout = null;
        bluetoothSocket = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // No-op: this service only performs gestures, it does not consume accessibility events.
    }

    @Override
    public void onInterrupt() {
        // No-op.
    }
}
