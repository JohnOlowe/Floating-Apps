package damjay.floating.projects.autoclicker.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

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
    /** Safety net in case a dispatched gesture never reports back. */
    private static final long TOUCH_RESTORE_MS = 1500;

    /**
     * The live instance, published once the system has bound us.
     *
     * <p>An accessibility service is created by the system when the user enables it in Settings,
     * which is almost never the moment the app has a Bluetooth session to hand over. So the
     * session cannot be picked up in onCreate(); the activity calls {@link #attach} on the
     * running instance instead.
     */
    private static ClickerAccessibilityService instance;

    /** Session handed over by ActionSelectorActivity, possibly before we are connected. */
    public static BluetoothSocket bluetoothSocket;

    private final ArrayList<View> clickPoints = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View clickerLayout;
    private WindowManager windowManager;
    private LayoutParams clickerParams;

    private BluetoothOperations btOperation;

    private boolean pendingAddButton = false;
    private boolean pendingRemoveButton = false;

    /**
     * Hands a connected socket to the running service.
     *
     * @return false when the service is not enabled/connected yet, in which case the socket is
     *     remembered and picked up from {@link #onServiceConnected()}.
     */
    public static boolean attach(BluetoothSocket socket) {
        bluetoothSocket = socket;
        ClickerAccessibilityService service = instance;
        if (service == null) return false;
        service.startSession(socket);
        return true;
    }

    /** True when the service is enabled and bound by the system. */
    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        // The user may have enabled us in response to the app asking, with a session already
        // waiting; if so, start it now.
        if (bluetoothSocket != null && btOperation == null) {
            startSession(bluetoothSocket);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Belt and braces: the activity also pings us with startService().
        if (bluetoothSocket != null && btOperation == null) {
            startSession(bluetoothSocket);
        }
        return START_NOT_STICKY;
    }

    /** Shows the floating toolbar and starts reading commands from the peer. */
    private void startSession(BluetoothSocket socket) {
        if (socket == null || btOperation != null) return;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windowManager == null) {
            toast(R.string.clicker_service_failed);
            return;
        }

        clickerLayout = LayoutInflater.from(this).inflate(R.layout.service_clicker, null);
        clickerParams = ViewsUtils.getAccessibilityOverlayParams(0, 100);
        try {
            windowManager.addView(clickerLayout, clickerParams);
        } catch (Throwable t) {
            t.printStackTrace();
            clickerLayout = null;
            toast(R.string.clicker_service_failed);
            return;
        }

        btOperation = new BluetoothOperations(socket);
        addClickListeners();
        btOperation.startReading(this);
    }

    private void toast(int messageRes) {
        try {
            Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
            // Never let a diagnostic take the service down.
        }
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
        }
    }

    private int pointSizePx() {
        return ViewsUtils.dpToPx(POINT_SIZE_DP, this);
    }

    private void addNewButton() {
        pendingAddButton = false;
        if (windowManager == null) return;

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
                        hasPrevious, lastX, lastY, pointSize, spacing,
                        metrics.widthPixels, metrics.heightPixels);

        // An explicit square size: without it the window wraps its content and comes out
        // wider than it is tall, which renders the oval background as an ellipse and puts
        // the real centre somewhere other than where the tap is aimed.
        LayoutParams pointParams =
                ViewsUtils.getAccessibilityOverlayParams(
                        position[0], position[1], pointSize, pointSize);
        clickPoint.setTag(pointParams);
        clickPoint.setOnTouchListener(
                ViewsUtils.getViewTouchListener(this, clickPoint, windowManager, pointParams));
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

    /**
     * Taps the middle of a click point, as it actually sits on screen.
     *
     * <p>Gesture coordinates are absolute screen coordinates, while a window's params are
     * relative to whatever the window manager decided its parent frame is (below the status
     * bar, for instance). Asking the view where it ended up avoids that whole class of
     * off-by-an-inset error.
     */
    private void tapPoint(View point) {
        int width = point.getWidth();
        int height = point.getHeight();
        if (width > 0 && height > 0) {
            int[] location = new int[2];
            point.getLocationOnScreen(location);
            clickPoint(location[0] + width / 2, location[1] + height / 2);
            return;
        }
        // Not laid out yet: fall back to the requested position.
        LayoutParams params = (LayoutParams) point.getTag();
        if (params == null) return;
        int size = pointSizePx();
        clickPoint(ClickPointLayout.centerOf(params.x, size),
                   ClickPointLayout.centerOf(params.y, size));
    }

    /**
     * Lets dispatched gestures reach the app underneath.
     *
     * <p>The points are real windows sitting exactly where we are about to tap, so without this
     * the injected touch lands on our own overlay instead of the app being controlled.
     */
    private void setPointsTouchable(boolean touchable) {
        if (windowManager == null) return;
        for (View point : clickPoints) {
            applyTouchable(point, (LayoutParams) point.getTag(), touchable);
        }
        // The toolbar is a window as well: a point sitting under it would otherwise send the
        // injected tap to our own + / - buttons.
        applyTouchable(clickerLayout, clickerParams, touchable);
    }

    private void applyTouchable(View view, LayoutParams params, boolean touchable) {
        if (view == null || params == null) return;
        if (touchable) {
            params.flags &= ~LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags |= LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        try {
            windowManager.updateViewLayout(view, params);
        } catch (Throwable ignored) {
            // View may have been removed underneath us.
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

        setPointsTouchable(false);
        // Restore even if the callback never arrives, so the points stay draggable.
        handler.postDelayed(() -> setPointsTouchable(true), TOUCH_RESTORE_MS);

        try {
            dispatchGesture(
                    builder.build(),
                    new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            setPointsTouchable(true);
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            setPointsTouchable(true);
                        }
                    },
                    null);
        } catch (Throwable t) {
            t.printStackTrace();
            setPointsTouchable(true);
        }
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
                        tapPoint(clickPoints.get(index));
                    }
                }
                break;
            }
            case TYPE_EXIT:
                endSession();
                break;
            default:
                // Ignore unsupported payload types.
        }
    }

    @Override
    public void onError(Throwable t) {
        endSession();
    }

    private void notifyAndStop() {
        if (btOperation != null) {
            btOperation.writeExit(this);
        }
        endSession();
    }

    /**
     * Tears the session down but leaves the service bound: an accessibility service stays alive
     * until the user disables it, so it must be able to host another session afterwards.
     */
    private void endSession() {
        if (btOperation != null) {
            btOperation.close();
            btOperation = null;
        }
        bluetoothSocket = null;
        removeViews();
        pendingAddButton = false;
        pendingRemoveButton = false;
    }

    private void removeViews() {
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
    }

    @Override
    public boolean onUnbind(Intent intent) {
        endSession();
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        endSession();
        instance = null;
        handler.removeCallbacksAndMessages(null);
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
