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
        this.clickerLayout = LayoutInflater.from(this).inflate(R.layout.service_clicker, (ViewGroup) null);
        if (bluetoothSocket == null) {
            System.out.println("Socket is null. Destroying...");
            stopSelf();
            return;
        }
        System.out.println("Socket is not null. Continuing...");
        this.btOperation = new BluetoothOperations(bluetoothSocket);
        this.windowManager = (WindowManager) getSystemService("window");
        WindowManager.LayoutParams floatingLayoutParams = ViewsUtils.getFloatingLayoutParams();
        this.clickerParams = floatingLayoutParams;
        this.windowManager.addView(this.clickerLayout, floatingLayoutParams);
        addClickListeners();
        this.btOperation.startReading(this);
    }

    private void addClickListeners() {
        this.clickerLayout.findViewById(R.id.addClicker).setOnClickListener(v -> sendToDevice((byte) -1, this));
        this.clickerLayout.findViewById(R.id.removeClicker).setOnClickListener(v -> sendToDevice((byte) -2, this));
        this.clickerLayout.findViewById(R.id.closeClicker).setOnClickListener(v -> notifyAndStop());
        View view = this.clickerLayout;
        ViewsUtils.addTouchListener(view,
                ViewsUtils.getViewTouchListener(this, view, this.windowManager, this.clickerParams), true, true, null);
    }

    public void sendToDevice(byte value, BluetoothOperations.BluetoothOperationsCallback callback) {
        BluetoothOperations bluetoothOperations = this.btOperation;
        if (bluetoothOperations != null) {
            bluetoothOperations.write(value, callback);
            this.pendingAddButton = value == -1 || this.pendingAddButton;
            this.pendingRemoveButton = value == -2 || this.pendingRemoveButton;
            return;
        }
        stopSelf();
    }

    private void addNewButton() {
        WindowManager.LayoutParams pointParams;
        this.pendingAddButton = false;
        View clickPoint = LayoutInflater.from(this).inflate(R.layout.clicker_point, (ViewGroup) null);
        ((TextView) clickPoint.findViewById(R.id.clickId)).setText(Integer.toString(this.clickPoints.size() + 1));
        DisplayMetrics deviceMetrics = getResources().getDisplayMetrics();
        if (this.clickPoints.isEmpty()) {
            int x = deviceMetrics.widthPixels / 4;
            int y = deviceMetrics.heightPixels / 4;
            pointParams = ViewsUtils.getFloatingLayoutParams(x, y);
        } else {
            ArrayList<View> arrayList = this.clickPoints;
            if (arrayList.get(arrayList.size() - 1).getTag() == null) {
                int x2 = deviceMetrics.widthPixels / 4;
                int y2 = deviceMetrics.heightPixels / 4;
                pointParams = ViewsUtils.getFloatingLayoutParams(x2, y2);
            } else {
                ArrayList<View> arrayList2 = this.clickPoints;
                WindowManager.LayoutParams firstPointParams =
                        (WindowManager.LayoutParams) arrayList2.get(arrayList2.size() - 1).getTag();
                if (firstPointParams.x > (deviceMetrics.widthPixels * 3) / 4) {
                    if (firstPointParams.y > (deviceMetrics.heightPixels * 3) / 4) {
                        pointParams = ViewsUtils.getFloatingLayoutParams(
                                deviceMetrics.widthPixels / 8, deviceMetrics.heightPixels / 8);
                    } else {
                        int i = deviceMetrics.widthPixels / 4;
                        int i2 = firstPointParams.y;
                        ArrayList<View> arrayList3 = this.clickPoints;
                        pointParams = ViewsUtils.getFloatingLayoutParams(
                                i, i2 + arrayList3.get(arrayList3.size() - 1).getMeasuredHeight());
                    }
                } else {
                    int i3 = firstPointParams.x;
                    ArrayList<View> arrayList4 = this.clickPoints;
                    pointParams = ViewsUtils.getFloatingLayoutParams(
                            i3 + arrayList4.get(arrayList4.size() - 1).getMeasuredWidth(), firstPointParams.y);
                }
            }
        }
        clickPoint.setTag(pointParams);
        clickPoint.setOnTouchListener(
                ViewsUtils.getViewTouchListener(this, clickPoint, this.windowManager, pointParams));
        this.clickPoints.add(clickPoint);
        this.windowManager.addView(clickPoint, pointParams);
    }

    private void removeLastButton() {
        this.pendingRemoveButton = false;
        if (!this.clickPoints.isEmpty()) {
            WindowManager windowManager = this.windowManager;
            ArrayList<View> arrayList = this.clickPoints;
            windowManager.removeView(arrayList.get(arrayList.size() - 1));
            ArrayList<View> arrayList2 = this.clickPoints;
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
                } else if (this.clickPoints.size() > byteValue) {
                    WindowManager.LayoutParams params =
                            (WindowManager.LayoutParams) this.clickPoints.get(byteValue).getTag();
                    clickPoint(params.x, params.y);
                }
                break;
            case 9:
                if (this.pendingAddButton) {
                    addNewButton();
                }
                if (this.pendingRemoveButton) {
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
        this.btOperation.writeExit(this);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        this.windowManager.removeView(this.clickerLayout);
        for (View clickPoint : this.clickPoints) {
            this.windowManager.removeView(clickPoint);
        }
        this.clickPoints.clear();
        BluetoothOperations bluetoothOperations = this.btOperation;
        if (bluetoothOperations != null) {
            bluetoothOperations.close();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
