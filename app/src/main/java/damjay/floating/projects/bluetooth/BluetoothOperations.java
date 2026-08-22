package damjay.floating.projects.bluetooth;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;
import java.io.DataInputStream;
import java.io.DataOutputStream;

public class BluetoothOperations implements Runnable {
    private BluetoothOperationsCallback bluetoothCallback;
    private boolean closed = false;
    private DataInputStream inputStream;
    private Throwable openError;
    private DataOutputStream outputStream;
    private final BluetoothSocket socket;

    public interface BluetoothOperationsCallback {
        public static final int ERROR = 2;
        public static final int READ_OPERATION = 0;

        void onError(Throwable th);

        void onSuccess(byte b, Object obj);
    }

    public interface BluetoothOperationsConstants {
        public static final byte TYPE_TEXT = 0;
        public static final byte TYPE_BYTE = 1;
        public static final byte TYPE_SHORT = 2;
        public static final byte TYPE_CHAR = 3;
        public static final byte TYPE_INT = 4;
        public static final byte TYPE_LONG = 5;
        public static final byte TYPE_FLOAT = 6;
        public static final byte TYPE_DOUBLE = 7;
        public static final byte TYPE_RAW_CONTENT = 8;
        public static final byte TYPE_SUCCESS = 9;
        public static final byte TYPE_EXIT = 10;
    }

    public BluetoothOperations(BluetoothSocket socket) {
        this.socket = socket;
        try {
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (Throwable openError) {
            this.openError = openError;
            openError.printStackTrace();
        }
    }

    public BluetoothSocket getSocket() {
        return socket;
    }

    public void startReading(BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        bluetoothCallback = bluetoothOperationsCallback;
        if (openError != null) {
            handle(openError, bluetoothOperationsCallback);
        } else {
            new Thread(this).start();
        }
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
                if (type == -1) {
                    return;
                }
                switch (type) {
                    case BluetoothOperationsConstants.TYPE_TEXT:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readUTF());
                        break;
                    case BluetoothOperationsConstants.TYPE_BYTE:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readByte());
                        break;
                    case BluetoothOperationsConstants.TYPE_SHORT:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readShort());
                        break;
                    case BluetoothOperationsConstants.TYPE_CHAR:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readChar());
                        break;
                    case BluetoothOperationsConstants.TYPE_INT:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readInt());
                        break;
                    case BluetoothOperationsConstants.TYPE_LONG:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readLong());
                        break;
                    case BluetoothOperationsConstants.TYPE_FLOAT:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readFloat());
                        break;
                    case BluetoothOperationsConstants.TYPE_DOUBLE:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_TEXT, inputStream.readDouble());
                        break;
                    case BluetoothOperationsConstants.TYPE_RAW_CONTENT:
                        byte[] bytes = new byte[inputStream.readUnsignedShort()];
                        inputStream.readFully(bytes);
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_RAW_CONTENT, bytes);
                        break;
                    case BluetoothOperationsConstants.TYPE_EXIT:
                        onSuccess(bluetoothCallback, BluetoothOperationsConstants.TYPE_EXIT, null);
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown type " + type);
                }
                return;
            } catch (Throwable t) {
                t.printStackTrace();
                if (closed) {
                    return;
                }
                onError(bluetoothCallback, t);
                return;
            }
        }
    }

    public void write(String content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_TEXT);
            outputStream.writeUTF(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(byte content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_BYTE);
            outputStream.writeByte(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(short content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_SHORT);
            outputStream.writeShort(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(char content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_CHAR);
            outputStream.writeChar(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(int content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_INT);
            outputStream.writeInt(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(long content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_LONG);
            outputStream.writeLong(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(float content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_FLOAT);
            outputStream.writeFloat(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(double content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_DOUBLE);
            outputStream.writeDouble(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void write(byte[] content, BluetoothOperationsCallback callback) {
        checkNull(callback);
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_RAW_CONTENT);
            outputStream.write(content);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void writeExit(BluetoothOperationsCallback callback) {
        try {
            outputStream.write(BluetoothOperationsConstants.TYPE_EXIT);
            onSuccess(callback, BluetoothOperationsConstants.TYPE_SUCCESS, null);
        } catch (Throwable t) {
            handle(t, callback);
        }
    }

    public void close() {
        try {
            closed = true;
            inputStream.close();
            outputStream.close();
            socket.close();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void checkNull(BluetoothOperationsCallback callback) {
        if (callback == null) {
            throw new NullPointerException("bluetoothOperationsCallback = null");
        }
    }

    private void handle(Throwable t, BluetoothOperationsCallback callback) {
        t.printStackTrace();
        onError(callback, t);
    }

    private void onSuccess(BluetoothOperationsCallback callback, byte type, Object returnValue) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(type, returnValue));
    }

    private void onError(BluetoothOperationsCallback callback, Throwable error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }
}
