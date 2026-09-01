package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import damjay.floating.projects.autoclicker.service.ClickerAccessibilityService;
import damjay.floating.projects.bluetooth.BluetoothCallback;
import damjay.floating.projects.bluetooth.BluetoothServerThread;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import damjay.floating.projects.R;
import java.util.UUID;

public class HostActivity extends AppCompatActivity implements BluetoothCallback {
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    
    private AlertDialog alertDialog;
    private BluetoothServerThread serverThread;
    private boolean closed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle(R.string.asHost);

        if (startListening()) {
            startWaiting();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setPositiveButton(R.string.finish, (dialog, id) -> {
                        dialog.dismiss();
                        finish();
                    })
                    .setCancelable(false)
                    .create()
                    .show();
        }
    }
    
    private boolean startListening() {
        try {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            adapter = bluetoothManager.getAdapter();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (adapter != null) {
            serverThread = new BluetoothServerThread(
                            this,
                            adapter,
                            getResources().getString(R.string.app_name),
                            UUID.fromString(getResources().getString(R.string.clicker_uuid)));
            // Opening the listening socket can fail (Bluetooth off, permission revoked). Report
            // that up front instead of showing a "waiting for connection" dialog that can never
            // be satisfied.
            if (!serverThread.isListening()) {
                serverThread = null;
                return false;
            }
            serverThread.start();
            return true;
        }
        return false;
    }

    private void startWaiting() {
        View view = getLayoutInflater().inflate(R.layout.loading_view, null);
        TextView loadingText = view.findViewById(R.id.loading_text);
        loadingText.setText(R.string.waiting_for_connection);

        alertDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setNegativeButton(R.string.cancel, (dialog, id) -> {
                    dialog.dismiss();
                    cancel();
                })
                .setCancelable(false)
                .create();
        alertDialog.show();
    }
    
    private void cancel() {
        closed = true;
    	if (serverThread != null) {
            serverThread.cancel();
        }
        finish();
    }

    @Override
    public void onResult(int resultCode, Object artifact) {
        runOnUiThread(() -> checkResult(resultCode, artifact));
    }
    
    private void startSelectorActivity() {
        Intent intent = new Intent(this, ActionSelectorActivity.class);
        ActionSelectorActivity.bluetoothSocket = socket;
        startActivity(intent);
        // Close this screen so pressing Back does not return to a stale "waiting" dialog
        // backed by an already-accepted (and closed) server socket.
        finish();
    }
    
    public void checkResult(int resultCode, Object artifact) {
        if (closed || isFinishing()) return;
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        if (resultCode == BluetoothCallback.SUCCESS && artifact instanceof BluetoothSocket) {
            // Connected successfully
            socket = (BluetoothSocket) artifact;
            startSelectorActivity();
            return;
        }
        new AlertDialog.Builder(this)
            .setMessage(R.string.bluetooth_error_occurred)
            .setPositiveButton(R.string.finish, (dialog, id) -> {
                dialog.dismiss();
                finish();
            })
            .setCancelable(false)
            .create()
            .show();
    }

    @Override
    protected void onDestroy() {
        // Stop advertising and release the listening socket if we are going away without
        // having handed a live connection over to the selector screen.
        if (socket == null && serverThread != null) {
            closed = true;
            serverThread.cancel();
        }
        serverThread = null;
        if (alertDialog != null) {
            alertDialog.dismiss();
            alertDialog = null;
        }
        super.onDestroy();
    }

}
