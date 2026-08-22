package damjay.floating.projects.autoclicker.activity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import damjay.floating.projects.R;
import damjay.floating.projects.bluetooth.BluetoothCallback;
import damjay.floating.projects.bluetooth.BluetoothServerThread;
import java.util.UUID;

public class HostActivity extends AppCompatActivity implements BluetoothCallback {
    private BluetoothAdapter adapter;
    private AlertDialog alertDialog;
    private boolean closed;
    private BluetoothServerThread serverThread;
    private BluetoothSocket socket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);
        getSupportActionBar().setTitle(R.string.asHost);
        if (startListening()) {
            startWaiting();
        } else {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setPositiveButton(R.string.finish,
                            (dialog, id) -> {
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
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService("bluetooth");
            this.adapter = bluetoothManager.getAdapter();
        } catch (Throwable t) {
            t.printStackTrace();
        }
        if (this.adapter != null) {
            BluetoothServerThread bluetoothServerThread =
                    new BluetoothServerThread(this, this.adapter, getResources().getString(R.string.app_name),
                            UUID.fromString(getResources().getString(R.string.clicker_uuid)));
            this.serverThread = bluetoothServerThread;
            bluetoothServerThread.start();
            return true;
        }
        return false;
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
        this.alertDialog = alertDialogCreate;
        alertDialogCreate.show();
    }

    private void cancel() {
        this.closed = true;
        BluetoothServerThread bluetoothServerThread = this.serverThread;
        if (bluetoothServerThread != null) {
            bluetoothServerThread.cancel();
        }
        finish();
    }

    @Override
    public void onResult(int resultCode, Object artifact) {
        runOnUiThread(() -> {
            AlertDialog alertDialog = this.alertDialog;
            if (alertDialog != null) {
                alertDialog.dismiss();
                this.alertDialog = null;
            }
            if (resultCode == 1) {
                if (artifact != null && (artifact instanceof BluetoothSocket)) {
                    this.socket = (BluetoothSocket) artifact;
                    startSelectorActivity();
                    return;
                }
                return;
            }
            if (this.closed) {
                return;
            }
            new AlertDialog.Builder(this)
                    .setMessage(R.string.bluetooth_error_occurred)
                    .setPositiveButton(R.string.finish,
                            (dialog, id) -> {
                                dialog.dismiss();
                                finish();
                            })
                    .setCancelable(false)
                    .create()
                    .show();
        });
    }

    private void startSelectorActivity() {
        Intent intent = new Intent(this, ActionSelectorActivity.class);
        ActionSelectorActivity.bluetoothSocket = this.socket;
        startActivity(intent);
    }
}
