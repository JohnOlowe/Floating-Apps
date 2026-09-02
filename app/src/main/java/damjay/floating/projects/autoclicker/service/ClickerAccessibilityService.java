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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import damjay.floating.projects.R;
import damjay.floating.projects.autoclicker.ClickPointLayout;
import damjay.floating.projects.autoclicker.ClickerCommand;
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
     * How long to wait after making our overlays non-touchable before injecting the gesture.
     * WindowManager applies the flag change asynchronously; without this pause the touch is
     * still delivered to our own click point instead of the app underneath.
     */
    private static final long GESTURE_SETTLE_MS = 80;
    /** Long enough that apps register a tap, far below the long-press threshold. */
    private static final long TAP_DURATION_MS = 60;
    /** Default swipe duration when the controller does not specify one. */
    private static final long DEFAULT_SWIPE_MS = 300;
    /** Longest swipe we will honour, so a garbage frame cannot schedule an absurd gesture. */
    private static final long MAX_SWIPE_MS = 60_000;

    private static final String TAG = "FloatingClicker";

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
    private int gesturesInFlight = 0;
    /**
     * Difference between absolute screen coordinates (what dispatchGesture wants) and the
     * coordinates in our window LayoutParams (which are relative to the parent frame the
     * window manager chose, typically below the status bar). Measured from a laid out view.
     */
    private int frameOffsetX;
    private int frameOffsetY;
    private boolean frameOffsetKnown;

    private final Runnable restoreTouch =
            () -> {
                gesturesInFlight = 0;
                setPointsTouchable(true);
            };

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
        announceScreenSize();
    }

    /**
     * Tells the controller the real screen size in pixels, so points it places remotely and
     * swipes it draws line up with what this device actually shows.
     */
    private void announceScreenSize() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        sendCommand(ClickerCommand.size(metrics.widthPixels, metrics.heightPixels));
    }

    private void sendCommand(String command) {
        if (btOperation != null) {
            btOperation.write(command, this);
        }
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
        final int newIndex = clickPoints.size();
        ((TextView) clickPoint.findViewById(R.id.clickId))
                .setText(Integer.toString(newIndex + 1));

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
        // Wrap the drag listener: keep the existing drag behaviour, but once the user lets go
        // of a point they dragged locally, tell the peer exactly where it ended up so a
        // remotely drawn point can be repositioned from the phone too.
        View.OnTouchListener dragListener =
                ViewsUtils.getViewTouchListener(this, clickPoint, windowManager, pointParams);
        clickPoint.setOnTouchListener(
                (view, event) -> {
                    boolean handled = dragListener.onTouch(view, event);
                    if (event.getAction() == MotionEvent.ACTION_UP
                            && damjay.floating.projects.utils.TouchState.getInstance().hasMoved()) {
                        reportPointPosition(view, clickPoints.indexOf(view));
                    }
                    return handled;
                });
        clickPoints.add(clickPoint);
        try {
            windowManager.addView(clickPoint, pointParams);
        } catch (Throwable t) {
            // Roll back if we somehow failed to attach the point.
            clickPoints.remove(clickPoint);
            t.printStackTrace();
            return;
        }
        // Once it has been laid out, tell the peer its true absolute position. Our predicted
        // position ignores whatever inset the window manager applied (the status bar), so this
        // also corrects the peer's copy right after a plain "add".
        clickPoint.post(
                () -> {
                    if (clickPoint.getParent() != null) reportPointPosition(clickPoint, newIndex);
                });
    }

    /** Sends the peer a MOVE command with the point's actual absolute top-left on screen. */
    private void reportPointPosition(View point, int index) {
        if (index < 0) return;
        int[] location = new int[2];
        point.getLocationOnScreen(location);
        if (location[0] == 0 && location[1] == 0 && point.getWidth() == 0) return;
        measureFrameOffset(point, (LayoutParams) point.getTag());
        sendCommand(ClickerCommand.move(index, location[0], location[1]));
    }

    /**
     * Records the constant offset between our window LayoutParams (relative to the parent
     * frame) and absolute screen coordinates, using a view that has actually been laid out.
     */
    private void measureFrameOffset(View view, LayoutParams params) {
        if (view == null || params == null || view.getWidth() == 0) return;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        frameOffsetX = location[0] - params.x;
        frameOffsetY = location[1] - params.y;
        frameOffsetKnown = true;
        Log.d(TAG, "frame offset measured as " + frameOffsetX + "," + frameOffsetY);
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
     * Moves an existing point to an absolute screen position requested by the peer.
     *
     * <p>The peer works in absolute screen coordinates (the same ones dispatchGesture uses);
     * our windows are positioned relative to the parent frame, so the measured frame offset is
     * subtracted before updating the window.
     */
    private void movePoint(int index, int absX, int absY) {
        if (windowManager == null || !ClickPointLayout.isValidIndex(index, clickPoints.size())) {
            Log.w(TAG, "move for point " + index + " ignored (only " + clickPoints.size() + ")");
            return;
        }
        View point = clickPoints.get(index);
        LayoutParams params = (LayoutParams) point.getTag();
        if (params == null) return;
        if (!frameOffsetKnown) measureFrameOffset(point, params);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int size = pointSizePx();
        int targetX = clamp(absX - frameOffsetX, 0, Math.max(0, metrics.widthPixels - size));
        int targetY = clamp(absY - frameOffsetY, 0, Math.max(0, metrics.heightPixels - size));
        params.x = targetX;
        params.y = targetY;
        try {
            windowManager.updateViewLayout(point, params);
        } catch (Throwable t) {
            t.printStackTrace();
        }
        Log.d(TAG, "moved point " + index + " to absolute " + absX + "," + absY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
            measureFrameOffset(point, (LayoutParams) point.getTag());
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
     * <p>The points are real windows sitting exactly where we are about to touch, so without
     * this the injected gesture lands on our own overlay instead of the app being controlled.
     */
    private void setPointsTouchable(boolean touchable) {
        if (windowManager == null) return;
        for (View point : clickPoints) {
            applyTouchable(point, (LayoutParams) point.getTag(), touchable);
        }
        // The toolbar is a window as well: a gesture passing under it would otherwise be sent
        // to our own + / - buttons.
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

    /**
     * Taps the screen at an absolute coordinate.
     *
     * <p>Our own click point is a touchable window sitting exactly there, and its drag listener
     * consumes ACTION_DOWN, so the injected tap would be eaten by us rather than reaching the
     * app. The points are therefore made non-touchable first -- but WindowManager applies that
     * asynchronously, so the gesture is delayed briefly to let the change actually land.
     */
    private void clickPoint(int x, int y) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (!canPerformGestures()) {
            Log.w(TAG, "cannot dispatch gestures; is the service still enabled?");
            toast(R.string.clicker_no_gestures);
            return;
        }

        gesturesInFlight++;
        setPointsTouchable(false);
        handler.removeCallbacks(restoreTouch);
        handler.postDelayed(() -> dispatchTap(x, y), GESTURE_SETTLE_MS);
    }

    /**
     * Swipes between two absolute screen points over {@code durationMs} milliseconds.
     *
     * <p>This is what powers "put two points anywhere, then swipe between them": the controller
     * sends the centres of the two chosen points and the service draws a single stroke through
     * the app underneath.
     */
    private void swipe(int fromX, int fromY, int toX, int toY, long durationMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        if (!canPerformGestures()) {
            Log.w(TAG, "cannot dispatch gestures; is the service still enabled?");
            toast(R.string.clicker_no_gestures);
            return;
        }
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        fromX = clamp(fromX, 0, metrics.widthPixels - 1);
        fromY = clamp(fromY, 0, metrics.heightPixels - 1);
        toX = clamp(toX, 0, metrics.widthPixels - 1);
        toY = clamp(toY, 0, metrics.heightPixels - 1);
        final long duration = clampDuration(durationMs);

        gesturesInFlight++;
        setPointsTouchable(false);
        handler.removeCallbacks(restoreTouch);
        handler.postDelayed(
                () -> dispatchSwipe(fromX, fromY, toX, toY, duration), GESTURE_SETTLE_MS);
    }

    private static long clampDuration(long durationMs) {
        if (durationMs <= 0) return DEFAULT_SWIPE_MS;
        return Math.min(durationMs, MAX_SWIPE_MS);
    }

    private void dispatchTap(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        dispatchStroke(path, TAP_DURATION_MS, "tap at " + x + "," + y);
    }

    private void dispatchSwipe(int fromX, int fromY, int toX, int toY, long duration) {
        Path path = new Path();
        path.moveTo(fromX, fromY);
        path.lineTo(toX, toY);
        dispatchStroke(path, duration,
                "swipe " + fromX + "," + fromY + " -> " + toX + "," + toY + " over " + duration + "ms");
    }

    private void dispatchStroke(Path path, long duration, String description) {
        boolean dispatched = false;
        try {
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));

            dispatched =
                    dispatchGesture(
                            builder.build(),
                            new GestureResultCallback() {
                                @Override
                                public void onCompleted(GestureDescription gestureDescription) {
                                    Log.d(TAG, "gesture completed");
                                    finishGesture();
                                }

                                @Override
                                public void onCancelled(GestureDescription gestureDescription) {
                                    Log.w(TAG, "gesture cancelled");
                                    finishGesture();
                                }
                            },
                            null);
        } catch (Throwable t) {
            // Coordinates outside the display make StrokeDescription throw.
            Log.e(TAG, "could not build the gesture", t);
        }

        Log.d(TAG, description + " dispatched=" + dispatched);
        if (!dispatched) {
            finishGesture();
        } else {
            // Safety net: restore touchability even if the callback never arrives.
            handler.postDelayed(restoreTouch, TOUCH_RESTORE_MS);
        }
    }

    private void finishGesture() {
        if (gesturesInFlight > 0) gesturesInFlight--;
        if (gesturesInFlight == 0) {
            handler.removeCallbacks(restoreTouch);
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
            case TYPE_TEXT: {
                handleCommand((String) value);
                break;
            }
            case TYPE_EXIT:
                endSession();
                break;
            default:
                // Ignore unsupported payload types.
        }
    }

    /** Handles the extended text commands (MOVE / SWIPE / SIZE). */
    private void handleCommand(String text) {
        if (!ClickerCommand.isCommand(text)) return;
        try {
            ClickerCommand.Parsed parsed = ClickerCommand.parse(text);
            long[] a = parsed.args;
            if (parsed.isMove()) {
                movePoint((int) a[0], (int) a[1], (int) a[2]);
            } else if (parsed.isSwipe()) {
                swipe((int) a[0], (int) a[1], (int) a[2], (int) a[3], a[4]);
            }
            // SIZE is only announced BY this side; a SIZE frame arriving here is ignored.
        } catch (Throwable t) {
            // A malformed command must never kill the reader thread.
            Log.w(TAG, "ignoring bad command: " + text, t);
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

    /** Tears the session down but leaves the service bound: an accessibility service stays alive
     * until the user disables it, so it must be able to host another session afterwards. */
    private void endSession() {
        if (btOperation != null) {
            btOperation.close();
            btOperation = null;
        }
        bluetoothSocket = null;
        removeViews();
        pendingAddButton = false;
        pendingRemoveButton = false;
        frameOffsetKnown = false;
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
