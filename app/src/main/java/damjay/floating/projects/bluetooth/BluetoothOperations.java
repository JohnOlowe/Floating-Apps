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
            this.inputStream = new DataInputStream(socket.getInputStream());
            this.outputStream = new DataOutputStream(socket.getOutputStream());
        } catch (Throwable openError) {
            this.openError = openError;
            openError.printStackTrace();
        }
    }

    public BluetoothSocket getSocket() {
        return this.socket;
    }

    public void startReading(BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        this.bluetoothCallback = bluetoothOperationsCallback;
        Throwable th = this.openError;
        if (th != null) {
            handle(th, bluetoothOperationsCallback);
        } else {
            new Thread(this).start();
        }
    }

    @Override
    public void run() {
        Throwable th = this.openError;
        if (th != null) {
            onError(this.bluetoothCallback, th);
            return;
        }
        while (!this.closed) {
            try {
                int type = this.inputStream.read();
                if (type != -1) {
                    switch (type) {
                        case 0:
                            onSuccess(this.bluetoothCallback, (byte) 0, this.inputStream.readUTF());
                            break;
                        case 1:
                            onSuccess(this.bluetoothCallback, (byte) 0, Byte.valueOf(this.inputStream.readByte()));
                            break;
                        case 2:
                            onSuccess(this.bluetoothCallback, (byte) 0, Short.valueOf(this.inputStream.readShort()));
                            break;
                        case 3:
                            onSuccess(this.bluetoothCallback, (byte) 0, Character.valueOf(this.inputStream.readChar()));
                            break;
                        case 4:
                            onSuccess(this.bluetoothCallback, (byte) 0, Integer.valueOf(this.inputStream.readInt()));
                            break;
                        case 5:
                            onSuccess(this.bluetoothCallback, (byte) 0, Long.valueOf(this.inputStream.readLong()));
                            break;
                        case 6:
                            onSuccess(this.bluetoothCallback, (byte) 0, Float.valueOf(this.inputStream.readFloat()));
                            break;
                        case 7:
                            onSuccess(this.bluetoothCallback, (byte) 0, Double.valueOf(this.inputStream.readDouble()));
                            break;
                        case 8:
                            byte[] bytes = new byte[this.inputStream.readUnsignedShort()];
                            this.inputStream.readFully(bytes);
                            onSuccess(this.bluetoothCallback, (byte) 8, bytes);
                            break;
                        case 9:
                        default:
                            throw new IllegalArgumentException("Unknown type " + type);
                        case 10:
                            onSuccess(this.bluetoothCallback, (byte) 10, null);
                            break;
                    }
                    return;
                }
            } catch (Throwable t) {
                t.printStackTrace();
                if (this.closed) {
                    return;
                }
                onError(this.bluetoothCallback, t);
                return;
            }
        }
    }

    public void write(String content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(0);
            this.outputStream.writeUTF(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(byte content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(1);
            this.outputStream.writeByte(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(short content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(2);
            this.outputStream.writeShort(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(char content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(3);
            this.outputStream.writeChar(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(int content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(4);
            this.outputStream.writeInt(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(long content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(5);
            this.outputStream.writeLong(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(float content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(6);
            this.outputStream.writeFloat(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(double content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(7);
            this.outputStream.writeDouble(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void write(byte[] content, BluetoothOperationsCallback bluetoothOperationsCallback) {
        checkNull(bluetoothOperationsCallback);
        try {
            this.outputStream.write(8);
            this.outputStream.write(content);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void writeExit(BluetoothOperationsCallback bluetoothOperationsCallback) {
        try {
            this.outputStream.write(10);
            onSuccess(bluetoothOperationsCallback, (byte) 9, null);
        } catch (Throwable t) {
            handle(t, bluetoothOperationsCallback);
        }
    }

    public void close() {
        try {
            this.closed = true;
            this.inputStream.close();
            this.outputStream.close();
            this.socket.close();
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
