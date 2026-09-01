package damjay.floating.projects.customadapters;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.bluetooth.BluetoothDevice;
import android.view.ViewGroup;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Set;

public class BluetoothDeviceAdapter extends BaseAdapter {
    private Context context;
    private ListView bluetoothDevicesList;

    private ArrayList<BluetoothDevice> bluetoothDevices = new ArrayList<>();

    public BluetoothDeviceAdapter(Context context, ListView bluetoothList) {
        this.context = context;
        this.bluetoothDevicesList = bluetoothList;

        startSearching();
    }

    /** Re-reads the bonded devices, e.g. after the user pairs a device and returns to the app. */
    @SuppressLint("MissingPermission")
    public void refresh() {
        startSearching();
        notifyDataSetChanged();
    }

    @SuppressLint("MissingPermission")
    private void startSearching() {
        bluetoothDevices = new ArrayList<>();
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) return;
        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return;
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices != null) {
                this.bluetoothDevices = new ArrayList<>(bondedDevices);
            }
        } catch (Throwable t) {
            // getBondedDevices() throws a SecurityException when BLUETOOTH_CONNECT is missing.
            t.printStackTrace();
        }
    }

    @Override
    public int getCount() {
        return bluetoothDevices.size();
    }

    @Override
    public BluetoothDevice getItem(int position) {
        return position >= 0 && position < bluetoothDevices.size()
                ? bluetoothDevices.get(position)
                : null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressLint("MissingPermission")
    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        if (view == null) {
            view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, viewGroup, false);
        }

        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);

        BluetoothDevice device = getItem(position);
        // The tag MUST be (re)assigned on every bind: list rows are recycled, so a view handed
        // back to us still carries the device of whichever row it previously displayed.
        view.setTag(device);

        if (device == null) {
            text1.setText("");
            text2.setText("");
            return view;
        }

        String name = null;
        try {
            name = device.getName();
        } catch (Throwable t) {
            // getName() throws a SecurityException when BLUETOOTH_CONNECT is missing.
            t.printStackTrace();
        }
        // Unnamed / unreachable devices report a null name; don't render the string "null".
        text1.setText(TextUtils.isEmpty(name) ? device.getAddress() : name);
        text2.setText(device.getAddress());
        return view;
    }

}
