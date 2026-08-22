package damjay.floating.projects.customadapters;

import android.R;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Set;

public class BluetoothDeviceAdapter extends BaseAdapter {
    private ArrayList<BluetoothDevice> bluetoothDevices;
    private final Context context;

    public BluetoothDeviceAdapter(Context context, ListView bluetoothList) {
        this.context = context;
        startSearching();
    }

    private void startSearching() {
        BluetoothAdapter adapter;
        Set<BluetoothDevice> bluetoothDevices;
        BluetoothManager bluetoothManager = (BluetoothManager) this.context.getSystemService("bluetooth");
        if (bluetoothManager != null && (adapter = bluetoothManager.getAdapter()) != null && adapter.isEnabled() && (bluetoothDevices = adapter.getBondedDevices()) != null) {
            this.bluetoothDevices = new ArrayList<>(bluetoothDevices);
        }
    }

    @Override
    public int getCount() {
        ArrayList<BluetoothDevice> arrayList = this.bluetoothDevices;
        if (arrayList == null) {
            return 0;
        }
        return arrayList.size();
    }

    @Override
    public Object getItem(int position) {
        ArrayList<BluetoothDevice> arrayList = this.bluetoothDevices;
        if (arrayList == null || arrayList.size() <= position) {
            return null;
        }
        return this.bluetoothDevices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        ArrayList<BluetoothDevice> arrayList = this.bluetoothDevices;
        boolean deviceAvailable = arrayList != null && arrayList.size() > position;
        if (view == null) {
            view = LayoutInflater.from(this.context).inflate(R.layout.simple_list_item_2, viewGroup, false);
            if (deviceAvailable) {
                view.setTag(this.bluetoothDevices.get(position));
            }
        }
        if (deviceAvailable) {
            BluetoothDevice device = this.bluetoothDevices.get(position);
            TextView text1 = (TextView) view.findViewById(R.id.text1);
            TextView text2 = (TextView) view.findViewById(R.id.text2);
            text1.setText(device.getName());
            text2.setText(device.getAddress());
        }
        return view;
    }
}
