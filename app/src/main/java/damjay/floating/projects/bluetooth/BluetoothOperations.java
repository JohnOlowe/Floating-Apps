package damjay.floating.projects.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import java.io.DataOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;

import static damjay.floating.projects.bluetooth.BluetoothOperations.BluetoothOperationsConstants.*;

public class BluetoothOperations implements Runnable {
    private BluetoothSocket socket;
    private BluetoothOperationsCallback bluetoothCallback;
    private Throwable openError;

    private DataInputStream inputStream;
    private DataOutputStream outputStream;
    
    private volatile boolean closed = false;

    /** Where callbacks are delivered. On a device this is the main thread. */
    private final CallbackDispatcher dispatcher;
    private Handler mainHandler;
    private Thread readerThread;

    public BluetoothOperations(BluetoothSocket socket) {
        this(socket, null);
    }

    /**
     * @param dispatcher where callbacks are delivered, or null for the main thread.
     */
    public BluetoothOperations(BluetoothSocket socket, CallbackDispatcher dispatcher) {
        this.socket = socket;
        this.dispatcher = dispatcher;

        try {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (Throwable openError) {
            this.openError = openError;
            openError.printStackTrace();
        }
    }

    /**
     * Stream-based constructor. The wire protocol does not actually care that the bytes came
     * from Bluetooth, so this lets the protocol be exercised over any pair of streams (which is
     * what the JVM unit tests do -- no device, no emulator required).
     */
    public BluetoothOperations(InputStream in, OutputStream out, CallbackDispatcher dispatcher) {
        this.socket = null;
        this.dispatcher = dispatcher;
        this.inputStream = new DataInputStream(in);
        this.outputStream = new DataOutputStream(out);
    }
    
    public BluetoothSocket getSocket() {
        return this.socket;
    }
    
    public void startReading(BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        
        this.bluetoothCallback = bluetoothOperationsCallback;
        if (openError != null) {
            handle(openError, bluetoothCallback);
            return;
        }
        readerThread = new Thread(this, "bluetooth-reader");
        readerThread.start();
    }

    /** Visible for testing: runs the read loop on the calling thread until the stream ends. */
    void readSynchronously(BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        this.bluetoothCallback = bluetoothOperationsCallback;
        run();
    }

    /** Visible for testing: waits for the reader thread to finish. */
    void awaitReader(long millis) throws InterruptedException {
        if (readerThread != null) readerThread.join(millis);
    }

    @Override
    public void run() {
        if (openError != null) {
            onError(bluetoothCallback, openError);
            return;
        }
        
        while (!closed) {
            try {
                int type = inputStream.read();
                // -1 means the remote device closed the stream cleanly; stop reading.
                if (type == -1) {
                    if (!closed) onError(bluetoothCallback, new EOFException("Remote device disconnected"));
                    break;
                }
                switch (type) {
                    case TYPE_TEXT:
                        onSuccess(bluetoothCallback, TYPE_TEXT, inputStream.readUTF());
                        break;
                    case TYPE_BYTE:
                        onSuccess(bluetoothCallback, TYPE_BYTE, inputStream.readByte());
                        break;
                    case TYPE_SHORT:
                         onSuccess(bluetoothCallback, TYPE_SHORT, inputStream.readShort());
                        break;
                    case TYPE_CHAR:
                        onSuccess(bluetoothCallback, TYPE_CHAR, inputStream.readChar());
                        break;
                    case TYPE_INT:
                        onSuccess(bluetoothCallback, TYPE_INT, inputStream.readInt());
                        break;
                    case TYPE_LONG:
                        onSuccess(bluetoothCallback, TYPE_LONG, inputStream.readLong());
                        break;
                    case TYPE_FLOAT:
                        onSuccess(bluetoothCallback, TYPE_FLOAT, inputStream.readFloat());
                        break;
                    case TYPE_DOUBLE:
                        onSuccess(bluetoothCallback, TYPE_DOUBLE, inputStream.readDouble());
                        break;
                    case TYPE_RAW_C0NTENT:
                        byte[] bytes = new byte[inputStream.readUnsignedShort()];
                        inputStream.readFully(bytes);
                        onSuccess(bluetoothCallback, TYPE_RAW_C0NTENT, bytes);
                        break;
                    case TYPE_EXIT:
                        onSuccess(bluetoothCallback, TYPE_EXIT, null);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown type " + type);
                }
            } catch (Throwable t) {
                t.printStackTrace();
                if (closed) return;
                onError(bluetoothCallback, t);
                break;
            }
        }
    }
    
    public void write(String content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_TEXT);
            outputStream.writeUTF(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(byte content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_BYTE);
            outputStream.writeByte(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(short content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_SHORT);
            outputStream.writeShort(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(char content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_CHAR);
            outputStream.writeChar(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(int content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_INT);
            outputStream.writeInt(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(long content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_LONG);
            outputStream.writeLong(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void write(float content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_FLOAT);
            outputStream.writeFloat(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(double content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_DOUBLE);
            outputStream.writeDouble(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(byte[] content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(TYPE_RAW_C0NTENT);
            // Length is a 2-byte unsigned short, matching the read side (readUnsignedShort()).
            outputStream.writeShort(content.length);
            outputStream.write(content);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }
    
    public void writeExit(BluetoothOperationsCallback bluetoothOperationsCallback) {
        try {
            outputStream.write(TYPE_EXIT);
            outputStream.flush();
            onSuccess(bluetoothOperationsCallback, TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void close() {
        try {
            closed = true;
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
    
    private void checkNull(BluetoothOperationsCallback bluetoothOperationsCallback) {
    	if (bluetoothOperationsCallback == null)
            throw new NullPointerException("bluetoothOperationsCallback = null");
    }
    
    private void handle(Throwable t, BluetoothOperationsCallback bluetoothOperationsCallback) {
        t.printStackTrace();
        onError(bluetoothOperationsCallback, t);
    }
    
    private void onSuccess(BluetoothOperationsCallback callback, byte type, Object returnValue) {
        post(() -> callback.onSuccess(type, returnValue));
    }
    
    private void onError(BluetoothOperationsCallback callback, Throwable error) {
        post(() -> callback.onError(error));
    }

    private void post(Runnable runnable) {
        if (dispatcher != null) {
            dispatcher.dispatch(runnable);
            return;
        }
        // Created lazily so that the stream-based constructor never touches the Android
        // framework, keeping it usable from plain JVM unit tests.
        if (mainHandler == null) mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(runnable);
    }

    /** Strategy for delivering callbacks; lets tests run them inline. */
    public interface CallbackDispatcher {
        void dispatch(Runnable runnable);
    }

    public static interface BluetoothOperationsCallback {
        int READ_OPERATION = 0;
        int ERROR = 2;

        /*
         * params: operationType - The operation type, either read or error operation.
         * returnValue - If it is a read operation the returnValue is of type byte[].
         * If operationType is ERROR, returnValue will be either Throwable or null
         */
        void onSuccess(byte type, Object returnValue);
        
        void onError(Throwable t);
    }

    public static interface BluetoothOperationsConstants {
        byte TYPE_TEXT = 0;
        byte TYPE_BYTE = 1;
        byte TYPE_SHORT = 2;
        byte TYPE_CHAR = 3;
        byte TYPE_INT = 4;
        byte TYPE_LONG = 5;
        byte TYPE_FLOAT = 6;
        byte TYPE_DOUBLE = 7;
        /**
         * In the case of a raw content, the maximum number of bytes accepted
         * is 65535 (two bytes). The first two bytes of the content to be
         * written is the total length of data to be written.
         */
        byte TYPE_RAW_C0NTENT = 8;
        byte TYPE_SUCCESS = 9;
        byte TYPE_EXIT = 10;
    }

}