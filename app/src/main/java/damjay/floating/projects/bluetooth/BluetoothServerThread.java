package damjay.floating.projects.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import java.io.IOException;
import java.util.UUID;

public class BluetoothServerThread extends Thread {
    // Written by cancel() from the UI thread, read by run() on this thread.
    private volatile BluetoothServerSocket serverSocket;
    private volatile boolean cancelled;
    private final BluetoothCallback callback;
    /**
     * Failure captured while opening the listening socket. It is reported from {@link #run()}
     * rather than from the constructor: reporting during construction re-enters the caller
     * before it has even finished assigning this object to a field, so the caller cannot yet
     * cancel the thread or dismiss the progress dialog it is about to create.
     */
    private final Throwable openError;

    @SuppressLint("MissingPermission")
    public BluetoothServerThread(BluetoothCallback callback, BluetoothAdapter adapter, String name, UUID uuid) {
        this.callback = callback;
        Throwable error = null;
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(name, uuid);
            if (serverSocket == null) {
                error = new IOException("Unable to open a Bluetooth server socket");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            error = t;
        }
        this.openError = error;
    }

    /** True when the listening socket was opened successfully. */
    public boolean isListening() {
        return openError == null && serverSocket != null;
    }

    @Override
    public void run() {
        if (openError != null) {
            callback.onResult(BluetoothCallback.ERROR, openError);
            return;
        }

        BluetoothSocket socket = null;
        try {
            BluetoothServerSocket listening = serverSocket;
            if (listening != null) {
                socket = listening.accept();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            closeServerSocket();
            if (!cancelled) callback.onResult(BluetoothCallback.ERROR, t);
            return;
        }

        // Only one client is accepted, so stop advertising the service either way.
        closeServerSocket();

        if (cancelled) {
            // The user backed out while we were blocked in accept(); don't leak the connection.
            closeClientSocket(socket);
            return;
        }
        if (socket != null) {
            callback.onResult(BluetoothCallback.SUCCESS, socket);
        } else {
            callback.onResult(BluetoothCallback.ERROR, new IOException("No client connected"));
        }
    }

    public void cancel() {
        cancelled = true;
        // Closing the listening socket makes the blocked accept() throw, unblocking run().
        closeServerSocket();
    }

    private void closeServerSocket() {
        BluetoothServerSocket listening = serverSocket;
        serverSocket = null;
        if (listening == null) return;
        try {
            listening.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void closeClientSocket(BluetoothSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
