package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.autoclicker.ClickerCommand;
import damjay.floating.projects.bluetooth.BluetoothOperations;
import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsCallback;
import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;

import java.util.ArrayList;
import java.util.List;

public class ClickerActivity extends AppCompatActivity implements BluetoothOperationsCallback, View.OnClickListener {
    public static BluetoothSocket bluetoothSocket;
    private static BluetoothOperations btOperation;
    private LinearLayout clickerContainer;
    private Button addButton;
    private Button removeButton;
    private Button swipeButton;

    public static final byte CLICKER_ADD_POINT = -1;
    public static final byte CLICKER_DELETE_POINT = -2;

    private static final int POINT_SIZE_DP = 30;
    private static final int POINT_SPACING_DP = 16;
    private static final long DEFAULT_SWIPE_MS = 300;

    private int curNumOfButtons = 0;

    private boolean pendingAddButton = false;
    private boolean pendingRemoveButton = false;

    /** True while the user is picking the two endpoints of a swipe rather than tapping. */
    private boolean swipeMode = false;
    /** First endpoint (1-based point number), or -1 when the swipe still needs its start. */
    private int swipeFrom = -1;

    // Best-known absolute positions (top-left) of each point on the service device. The
    // service sends @MOVE whenever a point is added or dragged, so these track where the
    // points really are even though this screen never shows them.
    private final List<int[]> pointPositions = new ArrayList<>();
    private int screenWidth = 1080;
    private int screenHeight = 1920;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clicker);

        clickerContainer = findViewById(R.id.clickerContainer);
        addButton = findViewById(R.id.addButton);
        removeButton = findViewById(R.id.removeButton);
        swipeButton = findViewById(R.id.swipeButton);

        if (bluetoothSocket != null) {
            btOperation = new BluetoothOperations(bluetoothSocket);
            btOperation.startReading(this);
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setNegativeButton(R.string.finish, (dialog, id) -> finish())
                    .setCancelable(false)
                    .create()
                    .show();
            return;
        }
        // Set on click listener for "Add" and "Remove"
        addButton.setOnClickListener((v) -> sendToDevice(CLICKER_ADD_POINT, this));
        removeButton.setOnClickListener((v) -> sendToDevice(CLICKER_DELETE_POINT, this));
        swipeButton.setOnClickListener((v) -> toggleSwipeMode());
        // Disable the "Remove" and swipe buttons until there are points to act on.
        removeButton.setEnabled(false);
        swipeButton.setEnabled(false);
    }

    public void sendToDevice(byte value, BluetoothOperationsCallback callback) {
        if (btOperation != null) {
            btOperation.write(value, callback);
            pendingAddButton = value == CLICKER_ADD_POINT || pendingAddButton;
            pendingRemoveButton = value == CLICKER_DELETE_POINT || pendingRemoveButton;
        }
        else Toast.makeText(this, R.string.null_socket, Toast.LENGTH_SHORT).show();
    }

    private void sendCommand(String command) {
        if (btOperation != null) {
            btOperation.write(command, this);
        }
    }

    private void toggleSwipeMode() {
        swipeMode = !swipeMode;
        swipeFrom = -1;
        swipeButton.setText(swipeMode ? R.string.swipe_cancel : R.string.swipe);
        if (swipeMode) {
            Toast.makeText(this, R.string.swipe_pick_start, Toast.LENGTH_SHORT).show();
        }
    }

    private void addNewButton() {
        pendingAddButton = false;
        curNumOfButtons++;
        removeButton.setEnabled(true);
        swipeButton.setEnabled(true);
        // Track a predicted position; the service corrects it with @MOVE once laid out.
        pointPositions.add(predictNextPosition());
        showButtons();
    }

    private void removeLastButton() {
        pendingRemoveButton = false;
        if (!pointPositions.isEmpty()) pointPositions.remove(pointPositions.size() - 1);
        if (--curNumOfButtons <= 0) {
            curNumOfButtons = 0;
            removeButton.setEnabled(false);
            swipeButton.setEnabled(false);
            if (swipeMode) toggleSwipeMode();
        }
        showButtons();
    }

    private int[] predictNextPosition() {
        int pointSize = dp(POINT_SIZE_DP);
        int spacing = dp(POINT_SPACING_DP);
        if (pointPositions.isEmpty()) {
            return new int[] {spacing, spacing};
        }
        int[] last = pointPositions.get(pointPositions.size() - 1);
        int maxX = screenWidth - pointSize;
        int nextX = last[0] + pointSize + spacing;
        if (nextX <= maxX) {
            return new int[] {nextX, last[1]};
        }
        int y = last[1] + pointSize + spacing;
        if (y > screenHeight - pointSize) y = spacing;
        return new int[] {spacing, y};
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void showButtons() {
        final int maxButtonOnLine = 4;
        int buttonsOnLine = 0;

        // Get the number of buttons to be on a line
        for (int i = 1; i <= curNumOfButtons && i <= maxButtonOnLine; i++) {
            buttonsOnLine = i;
            if (i * (i + 1) >= curNumOfButtons) {
                break;
            }
        }

        clickerContainer.removeAllViews();

        int curButton = 0;
        while (curButton < curNumOfButtons) {
            LinearLayout horizontalLine = new LinearLayout(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0);
            layoutParams.weight = 1;
            horizontalLine.setOrientation(LinearLayout.HORIZONTAL);
            horizontalLine.setLayoutParams(layoutParams);

            for (int i = 0; i < buttonsOnLine && curButton < curNumOfButtons; i++, curButton++) {
                Button button = new Button(this);
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT);
                buttonParams.weight = 1;
                int marginPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3.0f, getResources().getDisplayMetrics());
                buttonParams.setMargins(marginPx, marginPx, marginPx, marginPx);

                button.setLayoutParams(buttonParams);
                button.setText(Integer.toString(curButton + 1));
                button.setOnClickListener(this);
                horizontalLine.addView(button);
            }
            clickerContainer.addView(horizontalLine);
        }
    }

    public void onClick(View view) {
        if (btOperation == null) {
            Toast.makeText(this, R.string.null_socket, Toast.LENGTH_SHORT).show();
            return;
        }
        int number = Byte.parseByte(((Button) view).getText().toString());
        if (swipeMode) {
            handleSwipePick(number);
            return;
        }
        btOperation.write((byte) number, this);
    }

    private void handleSwipePick(int number) {
        if (swipeFrom < 0) {
            swipeFrom = number;
            Toast.makeText(this, R.string.swipe_pick_end, Toast.LENGTH_SHORT).show();
            return;
        }
        if (swipeFrom == number) {
            // Same point twice is just a tap; nothing to swipe.
            swipeFrom = -1;
            Toast.makeText(this, R.string.swipe_need_two, Toast.LENGTH_SHORT).show();
            return;
        }
        int[] from = centerOf(swipeFrom);
        int[] to = centerOf(number);
        if (from != null && to != null) {
            sendCommand(ClickerCommand.swipe(from[0], from[1], to[0], to[1], DEFAULT_SWIPE_MS));
        }
        swipeFrom = -1;
        toggleSwipeMode();
    }

    /** Centre of point {@code number} (1-based) in absolute screen pixels, or null. */
    private int[] centerOf(int number) {
        int index = number - 1;
        if (index < 0 || index >= pointPositions.size()) return null;
        int[] topLeft = pointPositions.get(index);
        int half = dp(POINT_SIZE_DP) / 2;
        return new int[] {topLeft[0] + half, topLeft[1] + half};
    }

    @Override
    public void onSuccess(byte type, Object returnValue) {
        switch (type) {
            case TYPE_SUCCESS:
                if (pendingAddButton)
                    addNewButton();
                if (pendingRemoveButton)
                    removeLastButton();
                break;
            case TYPE_BYTE:
                byte byteValue = (byte) returnValue;
                if (byteValue == CLICKER_ADD_POINT) {
                    addNewButton();
                } else if (byteValue == CLICKER_DELETE_POINT) {
                    removeLastButton();
                } else {
                    // Invalid command sent
                    System.out.println("Invalid byteValue: " + byteValue);
                }
                break;
            case TYPE_TEXT:
                handleCommand((String) returnValue);
                break;
            case TYPE_EXIT:
                btOperation.close();
                new AlertDialog.Builder(this)
                    .setTitle(R.string.device_disconnected)
                    .setMessage(R.string.device_disconnected_message)
                    .setCancelable(false)
                    .setNeutralButton(R.string.exit, (dialog, id) -> finish())
                    .create()
                    .show();
                break;
            default:
                // Should we close?
                System.out.println("Unrecognized type: " + type);
        }
    }

    /** Keeps this side's point positions in step with the service's @MOVE / @SIZE messages. */
    private void handleCommand(String text) {
        if (!ClickerCommand.isCommand(text)) return;
        try {
            ClickerCommand.Parsed parsed = ClickerCommand.parse(text);
            long[] a = parsed.args;
            if (parsed.isMove() && a[0] >= 0) {
                int index = (int) a[0];
                while (pointPositions.size() < index + 1) pointPositions.add(predictNextPosition());
                pointPositions.set(index, new int[] {(int) a[1], (int) a[2]});
            } else if (parsed.isSize()) {
                screenWidth = (int) a[0];
                screenHeight = (int) a[1];
            }
            // SWIPE received here is harmless: this side is the controller, it sends swipes.
        } catch (Throwable t) {
            System.out.println("Ignoring bad command: " + text);
        }
    }

    @Override
    public void onError(Throwable t) {
        new AlertDialog.Builder(this)
            .setMessage(getResources().getString(R.string.bluetooth_error_occurred) + (t == null ? "" : getResources().getString(R.string.reason, t.getMessage())))
            .setNegativeButton(R.string.finish, (dialog, id) -> finish())
            .setCancelable(false)
            .create()
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btOperation != null) btOperation.close();
    }

}
