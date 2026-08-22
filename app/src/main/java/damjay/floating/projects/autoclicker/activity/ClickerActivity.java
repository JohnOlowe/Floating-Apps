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
import damjay.floating.projects.bluetooth.BluetoothOperations;
import damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants;

public class ClickerActivity
        extends AppCompatActivity implements BluetoothOperations.BluetoothOperationsCallback, View.OnClickListener {
    public static final byte CLICKER_ADD_POINT = -1;
    public static final byte CLICKER_DELETE_POINT = -2;
    public static BluetoothSocket bluetoothSocket;
    private static BluetoothOperations btOperation;
    private Button addButton;
    private LinearLayout clickerContainer;
    private int curNumOfButtons = 0;
    private boolean pendingAddButton = false;
    private boolean pendingRemoveButton = false;
    private Button removeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_clicker);
        clickerContainer = (LinearLayout) findViewById(R.id.clickerContainer);
        addButton = (Button) findViewById(R.id.addButton);
        removeButton = (Button) findViewById(R.id.removeButton);

        if (bluetoothSocket != null) {
            btOperation = new BluetoothOperations(bluetoothSocket);
            btOperation.startReading(this);
            addButton.setOnClickListener(v -> sendToDevice(CLICKER_ADD_POINT, this));
            removeButton.setOnClickListener(v -> sendToDevice(CLICKER_DELETE_POINT, this));
            removeButton.setEnabled(false);
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setNegativeButton(R.string.finish, (dialog, id) -> finish())
                    .setCancelable(false)
                    .create()
                    .show();
        }
    }

    public void sendToDevice(byte value, BluetoothOperationsCallback callback) {
        if (btOperation != null) {
            btOperation.write(value, callback);
            pendingAddButton = (value == CLICKER_ADD_POINT) || pendingAddButton;
            pendingRemoveButton = (value == CLICKER_DELETE_POINT) || pendingRemoveButton;
        } else {
            Toast.makeText(this, R.string.null_socket, Toast.LENGTH_SHORT).show();
        }
    }

    private void addNewButton() {
        pendingAddButton = false;
        curNumOfButtons++;
        removeButton.setEnabled(true);
        showButtons();
    }

    private void removeLastButton() {
        pendingRemoveButton = false;
        curNumOfButtons--;
        if (curNumOfButtons <= 0) {
            curNumOfButtons = 0;
            removeButton.setEnabled(false);
        }
        showButtons();
    }

    private void showButtons() {
        int buttonsOnLine = 0;
        for (int i = 1; i <= curNumOfButtons && i <= 4; i++) {
            buttonsOnLine = i;
            if ((i + 1) * i >= curNumOfButtons) {
                break;
            }
        }

        clickerContainer.removeAllViews();
        int curButton = 0;
        while (curButton < curNumOfButtons) {
            LinearLayout row = new LinearLayout(this);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0);
            rowParams.weight = 1.0f;
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(rowParams);

            for (int col = 0; col < buttonsOnLine && curButton < curNumOfButtons; col++) {
                Button button = new Button(this);
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.MATCH_PARENT);
                buttonParams.weight = 1.0f;
                int marginPx = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 3.0f, getResources().getDisplayMetrics());
                buttonParams.setMargins(marginPx, marginPx, marginPx, marginPx);
                button.setLayoutParams(buttonParams);
                button.setText(String.valueOf(curButton + 1));
                button.setOnClickListener(this);
                row.addView(button);
                curButton++;
            }
            clickerContainer.addView(row);
        }
    }

    @Override
    public void onClick(View view) {
        btOperation.write(Byte.parseByte(((Button) view).getText().toString()), this);
    }

    @Override
    public void onSuccess(byte type, Object returnValue) {
        switch (type) {
            case BluetoothOperationsConstants.TYPE_BYTE:
                byte byteValue = (Byte) returnValue;
                if (byteValue == CLICKER_ADD_POINT) {
                    addNewButton();
                } else if (byteValue == CLICKER_DELETE_POINT) {
                    removeLastButton();
                } else {
                    System.out.println("Invalid byteValue: " + (int) byteValue);
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
            case BluetoothOperationsConstants.TYPE_EXIT:
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
                System.out.println("Unrecognized type: " + (int) type);
                break;
        }
    }

    @Override
    public void onError(Throwable t) {
        new AlertDialog.Builder(this)
                .setMessage(getResources().getString(R.string.bluetooth_error_occurred)
                        + (t == null ? "" : getResources().getString(R.string.reason, t.getMessage())))
                .setNegativeButton(R.string.finish, (dialog, id) -> finish())
                .setCancelable(false)
                .create()
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btOperation != null) {
            btOperation.close();
        }
    }
}
