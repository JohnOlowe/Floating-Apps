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
import damjay.floating.projects.bluetooth.BluetoothOperations;
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
        clickerLayout = LayoutInflater.from(this).inflate(R.layout.service_clicker, (ViewGroup) null);
        if (bluetoothSocket == null) {
            System.out.println("Socket is null. Destroying...");
            stopSelf();
            return;
        }
        System.out.println("Socket is not null. Continuing...");
        btOperation = new BluetoothOperations(bluetoothSocket);
        windowManager = (WindowManager) getSystemService("window");
        WindowManager.LayoutParams floatingLayoutParams = ViewsUtils.getFloatingLayoutParams();
        clickerParams = floatingLayoutParams;
        windowManager.addView(clickerLayout, floatingLayoutParams);
        addClickListeners();
        btOperation.startReading(this);
    }

    private void addClickListeners() {
        clickerLayout.findViewById(R.id.addClicker).setOnClickListener(v -> sendToDevice((byte) -1, this));
        clickerLayout.findViewById(R.id.removeClicker).setOnClickListener(v -> sendToDevice((byte) -2, this));
        clickerLayout.findViewById(R.id.closeClicker).setOnClickListener(v -> notifyAndStop());
        View view = clickerLayout;
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, windowManager, clickerParams), true, true, null);
    }

    public void sendToDevice(byte value, BluetoothOperations.BluetoothOperationsCallback callback) {
        BluetoothOperations bluetoothOperations = btOperation;
        if (bluetoothOperations != null) {
            bluetoothOperations.write(value, callback);
            pendingAddButton = value == -1 || pendingAddButton;
            pendingRemoveButton = value == -2 || pendingRemoveButton;
            return;
        }
        stopSelf();
    }

    private void addNewButton() {
        WindowManager.LayoutParams pointParams;
        pendingAddButton = false;
        View clickPoint = LayoutInflater.from(this).inflate(R.layout.clicker_point, (ViewGroup) null);
        ((TextView) clickPoint.findViewById(R.id.clickId)).setText(Integer.toString(clickPoints.size() + 1));
        DisplayMetrics deviceMetrics = getResources().getDisplayMetrics();
        if (clickPoints.isEmpty()) {
            int x = deviceMetrics.widthPixels / 4;
            int y = deviceMetrics.heightPixels / 4;
            pointParams = ViewsUtils.getFloatingLayoutParams(x, y);
        } else {
            ArrayList<View> arrayList = clickPoints;
            if (arrayList.get(arrayList.size() - 1).getTag() == null) {
                int x2 = deviceMetrics.widthPixels / 4;
                int y2 = deviceMetrics.heightPixels / 4;
                pointParams = ViewsUtils.getFloatingLayoutParams(x2, y2);
            } else {
                ArrayList<View> arrayList2 = clickPoints;
                WindowManager.LayoutParams firstPointParams =
                        (WindowManager.LayoutParams) arrayList2.get(arrayList2.size() - 1).getTag();
                if (firstPointParams.x > (deviceMetrics.widthPixels * 3) / 4) {
                    if (firstPointParams.y > (deviceMetrics.heightPixels * 3) / 4) {
                        pointParams = ViewsUtils.getFloatingLayoutParams(
                                deviceMetrics.widthPixels / 8, deviceMetrics.heightPixels / 8);
                    } else {
                        int i = deviceMetrics.widthPixels / 4;
                        int i2 = firstPointParams.y;
                        ArrayList<View> arrayList3 = clickPoints;
                        pointParams = ViewsUtils.getFloatingLayoutParams(
                                i, i2 + arrayList3.get(arrayList3.size() - 1).getMeasuredHeight());
                    }
                } else {
                    int i3 = firstPointParams.x;
                    ArrayList<View> arrayList4 = clickPoints;
                    pointParams = ViewsUtils.getFloatingLayoutParams(
                            i3 + arrayList4.get(arrayList4.size() - 1).getMeasuredWidth(), firstPointParams.y);
                }
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
            WindowManager windowManager = windowManager;
            ArrayList<View> arrayList = clickPoints;
            windowManager.removeView(arrayList.get(arrayList.size() - 1));
            ArrayList<View> arrayList2 = clickPoints;
            arrayList2.remove(arrayList2.size() - 1);
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
            case 1:
                byte byteValue = ((Byte) value).byteValue();
                if (byteValue == -1) {
                    addNewButton();
                } else if (byteValue == -2) {
                    removeLastButton();
                } else if (clickPoints.size() > byteValue) {
                    WindowManager.LayoutParams params =
                            (WindowManager.LayoutParams) clickPoints.get(byteValue).getTag();
                    clickPoint(params.x, params.y);
                }
                break;
            case 9:
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
        BluetoothOperations bluetoothOperations = btOperation;
        if (bluetoothOperations != null) {
            bluetoothOperations.close();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
