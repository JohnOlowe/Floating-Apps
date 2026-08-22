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
        public static final byte TYPE_BYTE = 1;
        public static final byte TYPE_CHAR = 3;
        public static final byte TYPE_DOUBLE = 7;
        public static final byte TYPE_EXIT = 10;
        public static final byte TYPE_FLOAT = 6;
        public static final byte TYPE_INT = 4;
        public static final byte TYPE_LONG = 5;
        public static final byte TYPE_RAW_C0NTENT = 8;
        public static final byte TYPE_SHORT = 2;
        public static final byte TYPE_SUCCESS = 9;
        public static final byte TYPE_TEXT = 0;
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
        Throwable th = openError;
        if (th != null) {
            handle(th, bluetoothOperationsCallback);
        } else {
            new Thread(this).start();
        }
    }

    @Override
    public void run() {
        Throwable th = openError;
        if (th != null) {
            onError(bluetoothCallback, th);
            return;
        }
        while (!closed) {
            try {
                int type = inputStream.read();
                if (type != -1) {
                    switch (type) {
                        case 0:
                            onSuccess(bluetoothCallback, (byte) 0, inputStream.readUTF());
                            break;
                        case 1:
                            onSuccess(bluetoothCallback, (byte) 0, Byte.valueOf(inputStream.readByte()));
                            break;
                        case 2:
                            onSuccess(bluetoothCallback, (byte) 0, Short.valueOf(inputStream.readShort()));
                            break;
                        case 3:
                            onSuccess(bluetoothCallback, (byte) 0, Character.valueOf(inputStream.readChar()));
                            break;
                        case 4:
                            onSuccess(bluetoothCallback, (byte) 0, Integer.valueOf(inputStream.readInt()));
                            break;
                        case 5:
                            onSuccess(bluetoothCallback, (byte) 0, Long.valueOf(inputStream.readLong()));
                            break;
                        case 6:
                            onSuccess(bluetoothCallback, (byte) 0, Float.valueOf(inputStream.readFloat()));
                            break;
                        case 7:
                            onSuccess(bluetoothCallback, (byte) 0, Double.valueOf(inputStream.readDouble()));
                            break;
                        case 8:
                            byte[] bytes = new byte[inputStream.readUnsignedShort()];
                            inputStream.readFully(bytes);
                            onSuccess(bluetoothCallback, (byte) 8, bytes);
                            break;
                        case 9:
                        default:
                            throw new IllegalArgumentException("Unknown type " + type);
                        case 10:
                            onSuccess(bluetoothCallback, (byte) 10, null);
                            break;
                    }
                    return;
                }
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

    public void write(String content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(0);
            outputStream.writeUTF(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(byte content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(1);
            outputStream.writeByte(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(short content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(2);
            outputStream.writeShort(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(char content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(3);
            outputStream.writeChar(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(int content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(4);
            outputStream.writeInt(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(long content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(5);
            outputStream.writeLong(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(float content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(6);
            outputStream.writeFloat(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(double content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(7);
            outputStream.writeDouble(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(byte[] content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            outputStream.write(8);
            outputStream.write(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void writeExit(BluetoothOperationsCallback bluetoothOperationsCallback) {
        try {
            outputStream.write(10);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
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

    private void checkNull(BluetoothOperationsCallback bluetoothOperationsCallback) {
        if (bluetoothOperationsCallback == null) {
            throw new NullPointerException("bluetoothOperationsCallback = null");
        }
    }

    private void handle(Throwable t, BluetoothOperationsCallback bluetoothOperationsCallback) {
        t.printStackTrace();
        onError(bluetoothOperationsCallback, t);
    }

    private void onSuccess(BluetoothOperationsCallback callback, byte type, Object returnValue) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onSuccess(type, returnValue));
    }

    private void onError(BluetoothOperationsCallback callback, Throwable error) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onError(error));
    }
}
