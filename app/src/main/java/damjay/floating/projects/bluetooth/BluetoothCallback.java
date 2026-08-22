package damjay.floating.projects.bluetooth;

public interface BluetoothCallback {
    public static final int ERROR = 0;
    public static final int SUCCESS = 1;

    void onResult(int i, Object obj);
}
