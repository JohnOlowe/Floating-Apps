package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.BluetoothDeviceAdapter;
import java.io.IOException;
import java.util.UUID;

public class GuestActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, Runnable {
    private BluetoothDeviceAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private ListView deviceList;
    private AlertDialog waitingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        setContentView(deviceList);
        getSupportActionBar().setTitle(R.string.asGuest);
    }

    private void initView() {
        ListView listView = new ListView(this);
        deviceList = listView;
        listView.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        View content = getLayoutInflater().inflate(R.layout.activity_guest, (ViewGroup) null);
        deviceList.addHeaderView(content);
        BluetoothDeviceAdapter bluetoothDeviceAdapter = new BluetoothDeviceAdapter(this, deviceList);
        bluetoothAdapter = bluetoothDeviceAdapter;
        deviceList.setAdapter((ListAdapter) bluetoothDeviceAdapter);
        deviceList.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> adapter, View childView, int position, long id) throws IOException {
        Object tag = childView.getTag();
        if (tag instanceof BluetoothDevice) {
            BluetoothDevice device = (BluetoothDevice) tag;
            BluetoothSocket socket = null;
            try {
                socket = device.createRfcommSocketToServiceRecord(
                        UUID.fromString(getResources().getString(R.string.clicker_uuid)));
            } catch (Throwable t) {
                t.printStackTrace();
            }
            if (socket == null) {
                onComplete(null);
                return;
            }
            bluetoothSocket = socket;
            new Thread(this).start();
            startWaiting();
        }
    }

    private void onComplete(BluetoothSocket connectedSocket) {
        if (waitingDialog != null) {
            waitingDialog.dismiss();
            waitingDialog = null;
        }
        if (connectedSocket == null) {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setCancelable(true)
                    .create()
                    .show();
            return;
        }
        Intent intent = new Intent(this, ActionSelectorActivity.class);
        ActionSelectorActivity.bluetoothSocket = connectedSocket;
        startActivity(intent);
    }

    private void startWaiting() {
        View view = getLayoutInflater().inflate(R.layout.loading_view, (ViewGroup) null);
        TextView loadingText = (TextView) view.findViewById(R.id.loading_text);
        loadingText.setText(R.string.waiting_for_connection);
        AlertDialog alertDialogCreate = new AlertDialog.Builder(this)
                                                .setView(view)
                                                .setNegativeButton(R.string.cancel,
                                                        (dialog, id) -> {
                                                            dialog.dismiss();
                                                            cancel();
                                                        })
                                                .setCancelable(false)
                                                .create();
        waitingDialog = alertDialogCreate;
        alertDialogCreate.show();
    }

    private void cancel() {
        finish();
    }

    @Override
    public void run() {
        boolean connected = false;
        try {
            bluetoothSocket.connect();
            connected = true;
        } catch (Throwable connectException) {
            connectException.printStackTrace();
            try {
                bluetoothSocket.close();
                bluetoothSocket = null;
            } catch (Throwable closeException) {
                closeException.printStackTrace();
            }
        }
        boolean connectedFlag = connected;
        runOnUiThread(() -> onComplete(connectedFlag ? bluetoothSocket : null));
    }
}
