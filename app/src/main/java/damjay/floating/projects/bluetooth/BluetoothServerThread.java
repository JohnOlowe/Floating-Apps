package damjay.floating.projects.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.util.UUID;

public class BluetoothServerThread extends Thread {
    private final BluetoothCallback callback;
    private BluetoothServerSocket serverSocket;

    public BluetoothServerThread(BluetoothCallback callback, BluetoothAdapter adapter, String name, UUID uuid) {
        this.callback = callback;
        try {
            BluetoothServerSocket bluetoothServerSocketListenUsingRfcommWithServiceRecord = adapter.listenUsingRfcommWithServiceRecord(name, uuid);
            serverSocket = bluetoothServerSocketListenUsingRfcommWithServiceRecord;
            if (bluetoothServerSocketListenUsingRfcommWithServiceRecord == null) {
                callback.onResult(0, null);
            }
        } catch (Throwable t) {
            t.printStackTrace();
            callback.onResult(0, t);
        }
    }

    @Override
    public void run() throws IOException {
        if (serverSocket == null) {
            return;
        }
        BluetoothSocket socket = null;
        while (true) {
            try {
                BluetoothServerSocket bluetoothServerSocket = serverSocket;
                if (bluetoothServerSocket == null || socket != null) {
                    break;
                } else {
                    socket = bluetoothServerSocket.accept();
                }
            } catch (Throwable t) {
                t.printStackTrace();
                callback.onResult(0, t);
                try {
                    serverSocket.close();
                    return;
                } catch (Throwable closeError) {
                    closeError.printStackTrace();
                    return;
                }
            }
        }
        if (socket != null) {
            callback.onResult(1, socket);
        }
        try {
            BluetoothServerSocket bluetoothServerSocket2 = serverSocket;
            if (bluetoothServerSocket2 != null) {
                bluetoothServerSocket2.close();
            }
        } catch (Throwable closeError2) {
            closeError2.printStackTrace();
        }
    }

    public void cancel() {
        try {
            serverSocket.close();
            serverSocket = null;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
