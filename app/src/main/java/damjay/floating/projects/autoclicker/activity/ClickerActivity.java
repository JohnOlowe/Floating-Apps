package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.bluetooth.BluetoothOperations;

public class ClickerActivity extends AppCompatActivity implements BluetoothOperations.BluetoothOperationsCallback, View.OnClickListener {
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
        this.clickerContainer = (LinearLayout) findViewById(R.id.clickerContainer);
        this.addButton = (Button) findViewById(R.id.addButton);
        this.removeButton = (Button) findViewById(R.id.removeButton);
        BluetoothSocket bluetoothSocket2 = bluetoothSocket;
        if (bluetoothSocket2 != null) {
            BluetoothOperations bluetoothOperations = new BluetoothOperations(bluetoothSocket2);
            btOperation = bluetoothOperations;
            bluetoothOperations.startReading(this);
            this.addButton.setOnClickListener((view) -> this.m137xcb7a9d75(view));
            this.removeButton.setOnClickListener((view) -> this.m138xbf0a21b6(view));
            this.removeButton.setEnabled(false);
            return;
        }
        new AlertDialog.Builder(this).setMessage(R.string.bluetooth_error_occurred).setNegativeButton(R.string.finish, (dialogInterface, i) -> this.m136xd7eb1934(dialogInterface, i)).setCancelable(false).create().show();
    }

    void m136xd7eb1934(DialogInterface dialog, int id) {
        finish();
    }

    void m137xcb7a9d75(View v) {
        sendToDevice((byte) -1, this);
    }

    void m138xbf0a21b6(View v) {
        sendToDevice((byte) -2, this);
    }

    public void sendToDevice(byte value, BluetoothOperations.BluetoothOperationsCallback callback) {
        BluetoothOperations bluetoothOperations = btOperation;
        if (bluetoothOperations != null) {
            bluetoothOperations.write(value, callback);
            this.pendingAddButton = value == -1 || this.pendingAddButton;
            this.pendingRemoveButton = value == -2 || this.pendingRemoveButton;
            return;
        }
        Toast.makeText(this, R.string.null_socket, 0).show();
    }

    private void addNewButton() {
        this.pendingAddButton = false;
        this.curNumOfButtons++;
        this.removeButton.setEnabled(true);
        showButtons();
    }

    private void removeLastButton() {
        this.pendingRemoveButton = false;
        int i = this.curNumOfButtons - 1;
        this.curNumOfButtons = i;
        if (i <= 0) {
            this.curNumOfButtons = 0;
            this.removeButton.setEnabled(false);
        }
        showButtons();
    }

    private void showButtons() {
        int buttonsOnLine = 0;
        int i = 1;
        while (true) {
            int i2 = this.curNumOfButtons;
            if (i > i2 || i > 4) {
                break;
            }
            buttonsOnLine = i;
            if ((i + 1) * i >= i2) {
                break;
            } else {
                i++;
            }
        }
        this.clickerContainer.removeAllViews();
        int curButton = 0;
        while (curButton < this.curNumOfButtons) {
            LinearLayout linearLayout = new LinearLayout(this);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, 0);
            layoutParams.weight = 1.0f;
            linearLayout.setOrientation(0);
            linearLayout.setLayoutParams(layoutParams);
            int i3 = 0;
            while (i3 < buttonsOnLine) {
                Button button = new Button(this);
                LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(0, -1);
                buttonParams.weight = 1.0f;
                int marginPx = (int) TypedValue.applyDimension(1, 3.0f, getResources().getDisplayMetrics());
                buttonParams.setMargins(marginPx, marginPx, marginPx, marginPx);
                button.setLayoutParams(buttonParams);
                button.setText(Integer.toString(curButton + 1));
                button.setOnClickListener(this);
                linearLayout.addView(button);
                i3++;
                curButton++;
            }
            this.clickerContainer.addView(linearLayout);
        }
    }

    @Override
    public void onClick(View view) {
        btOperation.write(Byte.parseByte(((Button) view).getText().toString()), (BluetoothOperations.BluetoothOperationsCallback) this);
    }

    @Override
    public void onSuccess(byte type, Object returnValue) {
        switch (type) {
            case 1:
                byte byteValue = ((Byte) returnValue).byteValue();
                if (byteValue == -1) {
                    addNewButton();
                } else if (byteValue == -2) {
                    removeLastButton();
                } else {
                    System.out.println("Invalid byteValue: " + ((int) byteValue));
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
            case 10:
                btOperation.close();
                new AlertDialog.Builder(this).setTitle(R.string.device_disconnected).setMessage(R.string.device_disconnected_message).setCancelable(false).setNeutralButton(R.string.exit, (dialogInterface, i) -> this.m140xa3044e86(dialogInterface, i)).create().show();
                break;
            default:
                System.out.println("Unrecognized type: " + ((int) type));
                break;
        }
    }

    void m140xa3044e86(DialogInterface dialog, int id) {
        finish();
    }

    @Override
    public void onError(Throwable t) {
        new AlertDialog.Builder(this).setMessage(getResources().getString(R.string.bluetooth_error_occurred) + (t == null ? "" : getResources().getString(R.string.reason, t.getMessage()))).setNegativeButton(R.string.finish, (dialogInterface, i) -> this.m139x19f30dcc(dialogInterface, i)).setCancelable(false).create().show();
    }

    void m139x19f30dcc(DialogInterface dialog, int id) {
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        BluetoothOperations bluetoothOperations = btOperation;
        if (bluetoothOperations != null) {
            bluetoothOperations.close();
        }
    }
}
