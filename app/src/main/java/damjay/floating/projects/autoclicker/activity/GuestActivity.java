package damjay.floating.projects.autoclicker.activity;

import android.annotation.SuppressLint;
import android.content.Intent;

import static android.view.ViewGroup.LayoutParams.*;

import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import damjay.floating.projects.R;
import damjay.floating.projects.customadapters.BluetoothDeviceAdapter;

import java.util.UUID;

public class GuestActivity extends AppCompatActivity
        implements AdapterView.OnItemClickListener, Runnable {
    private ListView deviceList;
    private BluetoothDeviceAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private TextView emptyView;

    private AlertDialog waitingDialog;
    /** Guards against starting a second connect thread while one is already running. */
    private boolean connecting;
    /** Set once the socket has been handed to the next screen, so we must not close it. */
    private boolean socketHandedOver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.asGuest);
    }

    private void initView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

        deviceList = new ListView(this);
        deviceList.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        View content = getLayoutInflater().inflate(R.layout.activity_guest, null);
        deviceList.addHeaderView(content, null, false);
        bluetoothAdapter = new BluetoothDeviceAdapter(this, deviceList);
        deviceList.setAdapter(bluetoothAdapter);
        deviceList.setOnItemClickListener(this);

        // Without this the screen is silently blank when nothing is paired yet.
        emptyView = new TextView(this);
        emptyView.setText(R.string.no_paired_devices);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(32, 32, 32, 32);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        root.addView(deviceList);

        setContentView(root);
        updateEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The user may have paired a device in system settings while we were backgrounded.
        if (bluetoothAdapter != null && !connecting) {
            bluetoothAdapter.refresh();
            updateEmptyState();
        }
    }

    private void updateEmptyState() {
        if (emptyView == null || bluetoothAdapter == null) return;
        emptyView.setVisibility(bluetoothAdapter.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onItemClick(AdapterView<?> adapter, View childView, int position, long id) {
        if (connecting) return;
        // getItemAtPosition() accounts for the header view, unlike the raw adapter position,
        // and unlike a view tag it cannot be stale from row recycling.
        Object item = adapter.getItemAtPosition(position);
        if (!(item instanceof BluetoothDevice)) return;
        BluetoothDevice device = (BluetoothDevice) item;
        BluetoothSocket socket = null;
        try {
            socket =
                    device.createRfcommSocketToServiceRecord(
                            UUID.fromString(getResources().getString(R.string.clicker_uuid)));
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (socket == null) {
            onComplete(null);
        } else {
            this.bluetoothSocket = socket;
            connecting = true;
            startWaiting();
            new Thread(this).start();
        }
    }

    private void onComplete(BluetoothSocket connectedSocket) {
        connecting = false;
        // The connect thread outlives the activity, so it can land here after the window is
        // gone; touching dialogs at that point throws BadTokenException.
        if (isFinishing() || isDestroyed()) {
            closeSocket(connectedSocket);
            return;
        }
        if (waitingDialog != null) {
            waitingDialog.dismiss();
            waitingDialog = null;
        }
        if (connectedSocket == null) {
            // Error occurred
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setCancelable(true)
                    .create()
                    .show();
        } else {
            // Connected with other device successfully
            Intent intent = new Intent(this, ActionSelectorActivity.class);
            ActionSelectorActivity.bluetoothSocket = connectedSocket;
            socketHandedOver = true;
            startActivity(intent);
            // Close this screen so Back does not return to a device list whose socket is
            // already in use by the next screen.
            finish();
        }
    }

    private void startWaiting() {
        View view = getLayoutInflater().inflate(R.layout.loading_view, null);
        TextView loadingText = view.findViewById(R.id.loading_text);
        loadingText.setText(R.string.connecting);

        waitingDialog =
                new AlertDialog.Builder(this)
                        .setView(view)
                        .setNegativeButton(R.string.cancel, (dialog, id) -> {
                            dialog.dismiss();
                            cancel();
                        })
                        .setCancelable(false)
                        .create();
        waitingDialog.show();
    }

    private void cancel() {
        // Closing the socket aborts the blocking connect() on the worker thread.
        closeSocket(bluetoothSocket);
        bluetoothSocket = null;
        finish();
    }

    private void closeSocket(BluetoothSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        if (waitingDialog != null) {
            waitingDialog.dismiss();
            waitingDialog = null;
        }
        if (!socketHandedOver) {
            closeSocket(bluetoothSocket);
            bluetoothSocket = null;
        }
        super.onDestroy();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void run() {
        BluetoothSocket socket = bluetoothSocket;
        if (socket == null) {
            runOnUiThread(() -> onComplete(null));
            return;
        }
        boolean connected = false;
        try {
            socket.connect();
            connected = true;
        } catch (Throwable connectException) {
            connectException.printStackTrace();
            closeSocket(socket);
        }
        final BluetoothSocket result = connected ? socket : null;
        if (!connected) bluetoothSocket = null;
        runOnUiThread(() -> onComplete(result));
    }

}
